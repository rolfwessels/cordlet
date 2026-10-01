import asyncio
import importlib.util
import json
import tempfile
import threading
from unittest.mock import patch
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
        self.echoes = []
        self.order = []
        async def accept(event):
            self.order.append('dispatch')
            self.events.append(event)
            event._gateway_accepted = True
        async def send(chat_id, content, reply_to=None, metadata=None):
            self.order.append('echo')
            self.echoes.append((chat_id, content, reply_to, metadata))
            return SimpleNamespace(success=True, message_id='echo-id')
        self.discord = SimpleNamespace(handle_message=accept, send=send)
        from gateway.config import Platform
        self.runner = SimpleNamespace(adapters={Platform.DISCORD: self.discord}, _is_user_authorized=lambda source: True)
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
        self.assertTrue(e.internal)
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
    async def test_busy_input_queues_instead_of_steering(self):
        from gateway.run_busy import GatewayBusySessionMixin
        pending = SimpleNamespace(_pending_messages={})
        async def no_approval(event, key):
            return False
        actual_runner = SimpleNamespace(
            _is_user_authorized=lambda source: True,
            _effective_busy_input_mode=lambda source: 'steer',
            _draining=False,
            _route_plaintext_approval_while_busy=no_approval,
            _adapter_for_source=lambda source: pending,
        )
        actual_runner._queue_or_replace_pending_event = lambda key, event: GatewayBusySessionMixin._enqueue_fifo(actual_runner, key, event, pending)
        async def busy(event):
            from gateway.session import build_session_key
            handled = await GatewayBusySessionMixin._handle_active_session_busy_message(actual_runner, event, build_session_key(event.source))
            if handled and event._gateway_accepted:
                self.events.append(event)
        self.discord.handle_message = busy
        r = await self.post({'request_id':'busy-proof','text':'hello'})
        self.assertEqual(r.status,202)
        self.assertEqual(len(self.events),1)
    async def test_owner_authorization_checked_before_dispatch(self):
        self.runner._is_user_authorized = lambda source: False
        r = await self.post({'request_id':'a','text':'hello'})
        self.assertEqual(r.status,403)
        self.assertEqual(self.events,[])
    async def test_missing_authorization_gate_fails_closed(self):
        del self.runner._is_user_authorized
        r = await self.post({'request_id':'a','text':'hello'})
        self.assertEqual(r.status,503)
        self.assertEqual(self.events,[])
    async def test_invalid_json(self):
        r = await self.client.post('/cordlet/messages', data='not json', headers=self.headers)
        self.assertEqual(r.status,400)

class AudioIngressTests(IngressTests):
    # Deliberately NOT real audio: injectable probe/STT unit fixtures. No provider calls.
    AUDIO = b'unit-fixture-aac-m4a'

    async def asyncSetUp(self):
        await super().asyncSetUp()
        self.home = tempfile.TemporaryDirectory()
        self.addCleanup(self.home.cleanup)
        self.paths = []
        self.calls = []
        self.module._hermes_home = lambda: pathlib.Path(self.home.name)
        def probe(path):
            self.paths.append(pathlib.Path(path))
            self.assertEqual(pathlib.Path(path).read_bytes(), self.AUDIO)
            self.assertEqual(pathlib.Path(path).stat().st_mode & 0o777, 0o600)
            self.assertEqual(pathlib.Path(path).parent.stat().st_mode & 0o777, 0o700)
        def transcribe(path):
            self.calls.append(path)
            return {'success': True, 'transcript': '/stop please remember milk'}
        self.module._probe_audio = probe
        self.module._transcribe_audio = transcribe

    async def audio(self, rid='voice-1', data=None, headers=None):
        h = {**self.headers, 'Content-Type': 'audio/mp4', 'X-Cordlet-Request-ID': rid}
        if headers is not None:
            h.update(headers)
        return await self.client.post('/cordlet/messages', data=self.AUDIO if data is None else data, headers=h)

    async def test_audio_transcribes_to_quoted_text_in_same_dm(self):
        r = await self.audio()
        self.assertEqual(r.status, 202)
        self.assertEqual(len(self.calls), 1)
        e = self.events[0]
        self.assertIn('Quoted voice input', e.text)
        self.assertIn('> /stop please remember milk', e.text)
        self.assertTrue(e.internal)
        self.assertFalse(e.allow_gateway_control)
        self.assertEqual(e.source.chat_id, self.config['chat_id'])
        self.assertEqual(e.media_urls, [])
        self.assertTrue(all(not p.exists() for p in self.paths))

    async def test_voice_duplicates_and_crossmodal_conflicts(self):
        self.assertEqual((await self.audio()).status, 202)
        r = await self.audio()
        self.assertEqual(r.status, 202)
        self.assertTrue((await r.json())['duplicate'])
        self.assertEqual(len(self.calls), 1)
        self.assertEqual((await self.audio(data=b'changed')).status, 409)
        self.assertEqual((await self.post({'request_id':'voice-1', 'text':'unit-fixture-aac-m4a'})).status, 409)
        await self.post({'request_id':'text-first', 'text':'hello'})
        self.assertEqual((await self.audio('text-first')).status, 409)

    async def test_audio_auth_before_storing_or_transcribing(self):
        self.assertEqual((await self.audio(headers={'Authorization':'Bearer bad'})).status, 401)
        self.runner._is_user_authorized = lambda source: False
        self.assertEqual((await self.audio()).status, 403)
        self.assertEqual(self.calls, [])
        self.assertEqual(self.echoes, [])
        self.assertEqual(list(pathlib.Path(self.home.name).iterdir()), [])

    async def test_audio_invalid_headers_and_empty_body(self):
        for rid in ('', '../oops', 'é', 'x'*81, 'has space'):
            self.assertEqual((await self.audio(rid)).status, 400)
        self.assertEqual((await self.audio(headers={'Content-Type':'audio/wav'})).status, 415)
        self.assertEqual((await self.audio(data=b'')).status, 422)
        self.assertEqual(self.calls, [])

    async def test_chunked_audio_cap_ignores_content_length(self):
        async def chunks():
            for _ in range(11):
                yield b'x' * (1024 * 1024)
        self.assertEqual((await self.audio(data=chunks())).status, 413)
        self.assertEqual(self.calls, [])
        self.assertEqual(self.echoes, [])
        self.assertEqual(list(pathlib.Path(self.home.name).iterdir()), [])

    async def test_invalid_probe_and_silence_do_not_queue(self):
        def bad(path):
            raise self.module.InvalidAudio('invalid')
        self.module._probe_audio = bad
        self.assertEqual((await self.audio()).status, 422)
        self.assertEqual(self.calls, [])
        self.module._probe_audio = lambda path: None
        self.module._transcribe_audio = lambda path: {'success':True, 'transcript':'  '}
        self.assertEqual((await self.audio()).status, 422)
        self.assertEqual(self.events, [])
        self.assertEqual(list(pathlib.Path(self.home.name).iterdir()), [])

    async def test_stt_failure_retry_keeps_id_unaccepted(self):
        self.module._transcribe_audio = lambda path: {'success':False, 'error':'provider unavailable'}
        self.assertEqual((await self.audio()).status, 503)
        self.assertEqual(self.events, [])
        self.module._transcribe_audio = lambda path: {'success':True, 'transcript':'try again'}
        self.assertEqual((await self.audio()).status, 202)
        self.assertEqual(list(pathlib.Path(self.home.name).iterdir()), [])

    async def test_concurrent_duplicates_share_one_stt_and_dispatch(self):
        started, release = threading.Event(), threading.Event()
        def blocked(path):
            self.calls.append(path)
            started.set()
            release.wait(2)
            return {'success':True, 'transcript':'one note'}
        self.module._transcribe_audio = blocked
        first = asyncio.create_task(self.audio())
        for _ in range(100):
            if started.is_set():
                break
            await asyncio.sleep(.005)
        self.assertTrue(started.is_set(), 'audio ingress did not start STT')
        second = asyncio.create_task(self.audio())
        await asyncio.sleep(.02)
        release.set()
        responses = await asyncio.gather(first, second)
        self.assertEqual([r.status for r in responses], [202, 202])
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(len(self.events), 1)
        self.assertEqual(len(self.echoes), 1)
        self.assertEqual(self.order, ['echo', 'dispatch'])
        self.assertEqual([(await r.json())['duplicate'] for r in responses], [False, True])

    async def test_audio_busy_fifo_never_merges_media_head(self):
        from gateway.run_busy import GatewayBusySessionMixin
        from gateway.session import build_session_key
        from gateway.platforms.event import MessageType
        pending = SimpleNamespace(_pending_messages={})
        conversation = SimpleNamespace(queued_events=[])
        async def no_approval(event, key):
            return False
        actual_runner = SimpleNamespace(
            _route_plaintext_approval_while_busy=no_approval,
            _is_user_authorized=lambda source: True,
            _effective_busy_input_mode=lambda source: 'steer',
            _draining=False,
            _adapter_for_source=lambda source: pending,
            _session_state=lambda key: SimpleNamespace(conversation=conversation),
        )
        actual_runner._queue_or_replace_pending_event = lambda key, event: GatewayBusySessionMixin._enqueue_fifo(actual_runner, key, event, pending)
        async def busy(event):
            handled = await GatewayBusySessionMixin._handle_active_session_busy_message(actual_runner, event, build_session_key(event.source))
            self.assertTrue(handled)
            self.events.append(event)
        self.discord.handle_message = busy
        self.assertEqual((await self.post({'request_id':'head', 'text':'first text'})).status, 202)
        self.assertEqual((await self.audio('note-one')).status, 202)
        self.assertEqual((await self.audio('note-two')).status, 202)
        self.assertEqual(len(pending._pending_messages), 1)
        self.assertEqual(len(conversation.queued_events), 2)
        self.assertEqual(next(iter(pending._pending_messages.values())).text, 'first text')
        self.assertTrue(all(e.message_type == MessageType.TEXT for e in self.events))
        self.assertTrue(all(e.media_urls == [] for e in self.events))
        self.assertEqual(len(self.echoes), 2)
        self.assertEqual([e.metadata['cordlet_request_id'] for e in conversation.queued_events],
                         ['note-one', 'note-two'])
        self.assertEqual((await self.audio('note-one')).status, 202)
        self.assertEqual(len(self.echoes), 2)
        self.assertEqual(len(conversation.queued_events), 2)

    async def test_timeout_holds_single_slot_and_file_until_thread_finishes(self):
        self.module.STT_TIMEOUT_SECONDS = .03
        started, release, finished = threading.Event(), threading.Event(), threading.Event()
        def blocked(path):
            self.calls.append(path)
            started.set()
            release.wait(2)
            self.assertTrue(pathlib.Path(path).exists())
            finished.set()
            return {'success':True, 'transcript':'late note'}
        self.module._transcribe_audio = blocked
        try:
            self.assertEqual((await self.audio()).status, 503)
            self.assertTrue(started.is_set())
            self.assertTrue(pathlib.Path(self.calls[0]).exists())
            self.assertEqual((await self.audio('different')).status, 503)
            self.assertEqual((await self.audio()).status, 503)
            self.assertEqual(len(self.calls), 1)
            self.assertEqual(self.events, [])
        finally:
            release.set()
        for _ in range(100):
            if finished.is_set() and not pathlib.Path(self.calls[0]).exists():
                break
            await asyncio.sleep(.005)
        self.assertFalse(pathlib.Path(self.calls[0]).exists())
        self.assertEqual(self.events, [])

    async def test_voice_echo_precedes_dispatch_and_duplicate_has_no_echo(self):
        self.assertEqual((await self.audio()).status, 202)
        self.assertEqual(self.order, ['echo', 'dispatch'])
        chat, content, reply_to, metadata = self.echoes[0]
        self.assertEqual(chat, self.config['chat_id'])
        self.assertEqual(content, '🎙️ Heard from your voice note:\n'
                         '_Automatic transcription; may contain mistakes._\n'
                         '> /stop please remember milk')
        self.assertIsNone(reply_to)
        self.assertIsNone(metadata)
        self.assertEqual((await self.audio()).status, 202)
        self.assertEqual(len(self.echoes), 1)
        self.assertEqual(len(self.events), 1)

    async def test_text_has_no_echo(self):
        self.assertEqual((await self.post({'request_id':'text', 'text':'hello'})).status, 202)
        self.assertEqual(self.echoes, [])

    async def test_long_multiline_echo_is_quoted_bounded_and_mentions_inert(self):
        transcript = ('😀' * 2100) + '\n@everyone <@306142339628400640> ```\nlast line'
        self.module._transcribe_audio = lambda path: {'success':True, 'transcript':transcript}
        self.assertEqual((await self.audio()).status, 202)
        self.assertGreater(len(self.echoes), 1)
        for chat, content, _, _ in self.echoes:
            self.assertEqual(chat, self.config['chat_id'])
            self.assertLessEqual(len(content.encode('utf-16-le')) // 2, 2000)
            self.assertTrue(content.startswith('🎙️ Heard from your voice note:'))
            self.assertTrue(all(line.startswith('> ') for line in content.splitlines()[2:]))
            self.assertNotIn('@everyone', content)
            self.assertNotIn('<@306', content)
        self.assertIn('last line', self.echoes[-1][1])
        self.assertIn('@everyone', self.events[0].text)

    async def test_echo_failure_is_not_dispatched_or_repeated_on_retry(self):
        attempts = []
        async def fail(chat_id, content):
            attempts.append(content)
            return SimpleNamespace(success=False, error='secret-provider-error')
        self.discord.send = fail
        for _ in range(2):
            r = await self.audio()
            self.assertEqual(r.status, 503)
            self.assertEqual((await r.json())['error'], 'voice_echo_uncertain')
        self.assertEqual(len(attempts), 1)
        self.assertEqual(self.events, [])
        self.assertEqual((await self.audio(data=b'changed')).status, 409)

    async def test_partial_echo_failure_never_replays_chunks_or_dispatches(self):
        self.module._transcribe_audio = lambda path: {'success':True, 'transcript':'x' * 4000}
        async def partial(chat_id, content):
            self.echoes.append((chat_id, content))
            return SimpleNamespace(success=len(self.echoes) == 1)
        self.discord.send = partial
        for _ in range(2):
            self.assertEqual((await self.audio()).status, 503)
        self.assertEqual(len(self.echoes), 2)
        self.assertEqual(self.events, [])
        self.assertEqual(len(self.calls), 0)  # Replaced fixture above; no provider calls.

    async def test_owner_revoked_during_stt_prevents_echo(self):
        def revoke(path):
            self.runner._is_user_authorized = lambda source: False
            return {'success':True, 'transcript':'private transcript'}
        self.module._transcribe_audio = revoke
        self.assertEqual((await self.audio()).status, 403)
        self.assertEqual(self.echoes, [])
        self.assertEqual(self.events, [])

    async def test_echo_exception_is_sanitized_and_not_retried(self):
        async def fail(chat_id, content):
            self.echoes.append(content)
            raise RuntimeError('secret-provider-error')
        self.discord.send = fail
        for _ in range(2):
            r = await self.audio()
            self.assertEqual(r.status, 503)
            self.assertEqual((await r.json())['error'], 'voice_echo_uncertain')
        self.assertEqual(len(self.echoes), 1)
        self.assertEqual(self.events, [])

    async def test_dispatch_exception_after_acceptance_does_not_create_second_answer(self):
        async def accepted_then_raise(event):
            self.events.append(event)
            event._gateway_accepted = True
            raise RuntimeError('dispatch-error')
        self.discord.handle_message = accepted_then_raise
        self.assertEqual((await self.audio()).status, 202)
        self.assertEqual((await self.audio()).status, 202)
        self.assertEqual(len(self.events), 1)
        self.assertEqual(len(self.echoes), 1)

    async def test_uncertain_dispatch_is_not_retried(self):
        async def uncertain(event):
            self.events.append(event)
            raise RuntimeError('dispatch-error')
        self.discord.handle_message = uncertain
        for _ in range(2):
            r = await self.audio()
            self.assertEqual(r.status, 503)
            self.assertEqual((await r.json())['error'], 'voice_admission_uncertain')
        self.assertEqual(len(self.events), 1)
        self.assertEqual(len(self.echoes), 1)

    async def test_audio_not_admitted_is_retryable(self):
        async def refuse(event): pass
        self.discord.handle_message = refuse
        self.assertEqual((await self.audio()).status, 503)
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(len(self.echoes), 1)
        async def accept(event):
            self.events.append(event)
            event._gateway_accepted = True
        self.discord.handle_message = accept
        self.assertEqual((await self.audio()).status, 202)
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(len(self.echoes), 1)
        self.assertEqual(len(self.events), 1)


class ProbeTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('cordlet_probe', MODULE)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_probe_real_generated_aac_m4a_fixture(self):
        # Actual encoded media generated locally in the isolated test container.
        # Synthetic tone, not a recording or a fabricated ffprobe response; no STT.
        import subprocess
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'tone.m4a'
            subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i',
                            'sine=frequency=440:duration=0.1', '-c:a', 'aac', str(path)],
                           check=True, capture_output=True, timeout=10)
            self.module._probe_audio(path)
            path.write_bytes(b'not a media container')
            with self.assertRaises(self.module.InvalidAudio):
                self.module._probe_audio(path)

    def test_probe_process_failures_are_bounded(self):
        import subprocess
        for failure in (FileNotFoundError('ffprobe missing'), subprocess.TimeoutExpired('ffprobe', 10)):
            with self.subTest(failure=failure), patch.object(self.module.subprocess, 'run', side_effect=failure):
                with self.assertRaises(type(failure)):
                    self.module._probe_audio(pathlib.Path('/tmp/server-generated.m4a'))
        for stdout in ('not json', '{}', '{"streams":null,"format":null}'):
            with self.subTest(stdout=stdout), patch.object(self.module.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout=stdout)):
                with self.assertRaises(self.module.InvalidAudio):
                    self.module._probe_audio(pathlib.Path('/tmp/server-generated.m4a'))

    def test_probe_validates_streams_format_and_duration(self):
        valid = {'streams':[{'codec_type':'audio','codec_name':'aac'}],
                 'format':{'format_name':'mov,mp4,m4a,3gp,3g2,mj2','duration':'300', 'tags':{'major_brand':'M4A '}}}
        invalid = [
            {**valid, 'streams':[]},
            {**valid, 'streams':[{'codec_type':'video','codec_name':'h264'}]},
            {**valid, 'streams':valid['streams'] + [{'codec_type':'video'}]},
            {**valid, 'streams':[{'codec_type':'audio','codec_name':'mp3'}]},
            {**valid, 'format':{'format_name':'mp3','duration':'10'}},
            {**valid, 'format':{**valid['format'], 'duration':'301'}},
            {**valid, 'format':{**valid['format'], 'duration':'NaN'}},
            {**valid, 'format':{**valid['format'], 'duration':'0'}},
        ]
        for payload in [valid, *invalid]:
            with self.subTest(payload=payload), patch.object(self.module.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout=json.dumps(payload))) as run:
                if payload is valid:
                    self.module._probe_audio(pathlib.Path('/tmp/server-generated.m4a'))
                else:
                    with self.assertRaises(self.module.InvalidAudio):
                        self.module._probe_audio(pathlib.Path('/tmp/server-generated.m4a'))
                self.assertFalse(run.call_args.kwargs.get('shell', False))
                self.assertLessEqual(run.call_args.kwargs['timeout'], 15)

if __name__ == '__main__': unittest.main()
