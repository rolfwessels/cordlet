"""Narrow proof-of-concept ingress: authenticated phone text -> Discord adapter.

No Discord credential, destination selector, general API key, or separate agent loop.
Deduplication is process-local and bounded; 202 means admission, NOT delivery.
"""
import asyncio
import hmac
import json
import os
import re
from collections import OrderedDict
from pathlib import Path


def wire(app, api_adapter, config):
    from aiohttp import web
    from gateway.config import Platform
    from gateway.platforms.event import MessageEvent
    from gateway.session import SessionSource

    token = config.get('token', '')
    if not isinstance(token, str) or len(token) < 24:
        raise ValueError('Cordlet requires a device token of at least 24 characters')
    for field in ('chat_id', 'user_id'):
        if not isinstance(config.get(field), str) or not re.fullmatch(r'[0-9]{17,20}', config[field]):
            raise ValueError('Cordlet requires valid server-side Discord IDs')
    accepted = OrderedDict()
    lock = asyncio.Lock()

    async def submit(request):
        supplied = request.headers.get('Authorization', '')
        if not hmac.compare_digest(supplied.encode(), ('Bearer ' + token).encode()):
            return web.json_response({'error':'unauthorized'}, status=401)
        if request.content_length is not None and request.content_length > 8192:
            return web.json_response({'error':'request_too_large'}, status=413)
        # Read incrementally: do not rely on a declared Content-Length (chunked requests).
        raw = bytearray()
        async for chunk in request.content.iter_chunked(8192):
            raw.extend(chunk)
            if len(raw) > 8192:
                return web.json_response({'error':'request_too_large'}, status=413)
        try:
            body = json.loads(raw)
        except (ValueError, UnicodeError):
            return web.json_response({'error':'invalid_json'}, status=400)
        if not isinstance(body, dict) or set(body) != {'request_id', 'text'}:
            return web.json_response({'error':'expected_request_id_and_text_only'}, status=400)
        rid, text = body['request_id'], body['text']
        if (not isinstance(rid, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,80}', rid)
                or not isinstance(text, str) or not text.strip() or len(text) > 4000):
            return web.json_response({'error':'invalid_input'}, status=400)
        async with lock:
            if rid in accepted:
                if accepted[rid] != text:
                    return web.json_response({'error':'request_id_conflict'}, status=409)
                return web.json_response({'request_id':rid, 'status':'accepted', 'duplicate':True}, status=202)
            runner = getattr(api_adapter, 'gateway_runner', None)
            discord = (getattr(runner, 'adapters', None) or {}).get(Platform.DISCORD)
            if discord is None:
                return web.json_response({'error':'discord_unavailable'}, status=503)
            source = SessionSource(platform=Platform.DISCORD, chat_id=config['chat_id'],
                                   chat_type='dm', user_id=config['user_id'],
                                   user_name=config.get('user_name', 'Cordlet user'),
                                   chat_name=config.get('chat_name', 'Cordlet DM'))
            # Synthetic events are FIFO-queued while busy. Check owner authorization
            # explicitly first because the cold internal-event path bypasses that gate.
            authorize = getattr(runner, '_is_user_authorized', None)
            if not callable(authorize):
                return web.json_response({'error':'authorization_unavailable'}, status=503)
            if not authorize(source):
                return web.json_response({'error':'owner_not_authorized'}, status=403)
            # No fabricated Discord message ID: there is no native message to reply/react to.
            event = MessageEvent(text=text, source=source, user_id=source.user_id,
                                 user_name=source.user_name, internal=True,
                                 allow_gateway_control=False,
                                 metadata={'cordlet_request_id':rid})
            await discord.handle_message(event)
            if event._gateway_accepted is not True:
                return web.json_response({'error':'gateway_not_admitted'}, status=503)
            accepted[rid] = text
            if len(accepted) > 512:
                accepted.popitem(last=False)
            return web.json_response({'request_id':rid, 'status':'accepted', 'duplicate':False}, status=202)

    app.router.add_post('/cordlet/messages', submit)


def register(ctx):
    def attach(app, adapter):
        # Explicit config file; no credentials stored in the repo or manifest.
        from hermes_constants import get_hermes_home
        filename = os.environ.get('CORDLET_INGRESS_CONFIG')
        config_path = Path(filename) if filename else get_hermes_home() / 'cordlet-ingress.json'
        if not config_path.is_file():
            return
        config = json.loads(config_path.read_text())
        wire(app, adapter, config)
    ctx.register_platform_handler('api_server', attach)
