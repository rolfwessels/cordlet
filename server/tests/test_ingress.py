import importlib.util
import pathlib
import unittest
from types import SimpleNamespace
from aiohttp import web
from aiohttp.test_utils import TestClient, TestServer

MODULE = pathlib.Path(__file__).parents[1] / 'cordlet_ingress' / '__init__.py'

class IngressTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.assertTrue(MODULE.exists(), 'Cordlet ingress implementation missing')
        spec = importlib.util.spec_from_file_location('cordlet_ingress', MODULE)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.events = []
        async def accept(event):
            self.events.append(event)
            event._gateway_accepted = True
        self.discord = SimpleNamespace(handle_message=accept)
        from gateway.config import Platform
        self.runner = SimpleNamespace(adapters={Platform.DISCORD: self.discord})
        self.api = SimpleNamespace(gateway_runner=self.runner)
        self.config = {'token':'test-device-token-not-for-production', 'chat_id':'1477001090951286794', 'user_id':'306142339628400640', 'user_name':'Splicer'}
        self.app = web.Application(client_max_size=8192)
        self.module.wire(self.app, self.api, self.config)
        self.client = TestClient(TestServer(self.app))
        await self.client.start_server()
        self.headers = {'Authorization':'Bearer ' + self.config['token']}
    async def asyncTearDown(self):
        if hasattr(self, 'client'):
            await self.client.close()
    async def post(self, payload, headers=None):
        return await self.client.post('/cordlet/messages', json=payload, headers=headers or self.headers)
    async def test_auth_required(self):
        r = await self.client.post('/cordlet/messages', json={'request_id':'a', 'text':'hello'})
        self.assertEqual(r.status,401)
        self.assertEqual(self.events,[])
    async def test_fixed_dm_route_and_admission_only(self):
        r = await self.post({'request_id':'a', 'text':'hello from phone'})
        self.assertEqual(r.status,202)
        self.assertEqual((await r.json())['status'],'accepted')
        e = self.events[0]
        self.assertEqual(e.source.chat_id,self.config['chat_id'])
        self.assertEqual(e.source.user_id,self.config['user_id'])
        self.assertEqual(e.source.chat_type,'dm')
        self.assertFalse(e.internal)
        self.assertFalse(e.allow_gateway_control)
        self.assertIsNone(e.message_id)
        from gateway.session import build_session_key
        self.assertEqual(build_session_key(e.source), 'agent:main:discord:dm:1477001090951286794')
    async def test_duplicate_does_not_dispatch_twice(self):
        await self.post({'request_id':'a', 'text':'hello'})
        r = await self.post({'request_id':'a', 'text':'hello'})
        self.assertEqual(r.status,202)
        self.assertTrue((await r.json())['duplicate'])
        self.assertEqual(len(self.events),1)
    async def test_changed_duplicate_conflicts(self):
        await self.post({'request_id':'a', 'text':'hello'})
        r = await self.post({'request_id':'a', 'text':'different'})
        self.assertEqual(r.status,409)
    async def test_rejects_destination_override_and_invalid_input(self):
        for payload in ({'request_id':'a','text':'hello','chat_id':'other'}, {'request_id':'a','text':' '}, {'request_id':'a','text':'x'*4001}, {'request_id':'../a','text':'hello'}, {'request_id':'a','text':12}):
            with self.subTest(payload=type(payload['text']).__name__):
                r = await self.post(payload)
                self.assertEqual(r.status,400)
        self.assertEqual(self.events,[])
    async def test_disconnected_gateway_does_not_accept(self):
        self.runner.adapters.clear()
        r = await self.post({'request_id':'a','text':'hello'})
        self.assertEqual(r.status,503)
    async def test_no_admission_is_retryable(self):
        async def refuse(event): pass
        self.discord.handle_message = refuse
        r = await self.post({'request_id':'a','text':'hello'})
        self.assertEqual(r.status,503)
        async def accept(event):
            self.events.append(event)
            event._gateway_accepted = True
        self.discord.handle_message = accept
        r = await self.post({'request_id':'a','text':'hello'})
        self.assertEqual(r.status,202)
    async def test_invalid_json(self):
        r = await self.client.post('/cordlet/messages', data='not json', headers=self.headers)
        self.assertEqual(r.status,400)

if __name__ == '__main__': unittest.main()
