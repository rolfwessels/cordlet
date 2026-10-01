"""Scoped phone text/voice -> existing owner Discord DM; 202 is admission only."""
import asyncio
import hashlib
import hmac
import json
import math
import os
import re
import subprocess
import tempfile
from collections import OrderedDict
from pathlib import Path

TEXT_MAX_BYTES = 8192
AUDIO_MAX_BYTES = 10 * 1024 * 1024
STT_TIMEOUT_SECONDS = 120
PROBE_TIMEOUT_SECONDS = 10
REQUEST_ID = r'[A-Za-z0-9_-]{1,80}'


class InvalidAudio(ValueError):
    pass


def _hermes_home():
    from hermes_constants import get_hermes_home
    return get_hermes_home()


def _transcribe_audio(path):
    # Lazy: JSON ingress works without loading STT dependencies/providers.
    from tools.transcription_tools import transcribe_audio
    return transcribe_audio(str(path), None, 'gateway')


def _probe_audio(path):
    result = subprocess.run(
        ['ffprobe', '-v', 'error', '-protocol_whitelist', 'file',
         '-show_entries', 'stream=codec_type,codec_name,duration:format=format_name,duration',
         '-of', 'json', str(path)],
        capture_output=True, text=True, timeout=PROBE_TIMEOUT_SECONDS, check=False)
    try:
        if result.returncode != 0:
            raise InvalidAudio('invalid_audio')
        info = json.loads(result.stdout)
        streams, fmt = info['streams'], info['format']
        duration = float(fmt['duration'])
        if (not isinstance(streams, list) or not streams
                or not {'mp4', 'm4a'}.intersection(fmt['format_name'].split(','))
                or not math.isfinite(duration) or not 0 < duration <= 300):
            raise InvalidAudio('invalid_audio')
        for stream in streams:
            if stream.get('codec_type') != 'audio' or stream.get('codec_name') != 'aac':
                raise InvalidAudio('invalid_audio')
            if 'duration' in stream:
                stream_duration = float(stream['duration'])
                if not math.isfinite(stream_duration) or not 0 < stream_duration <= 300:
                    raise InvalidAudio('invalid_audio')
    except (ValueError, KeyError, TypeError, AttributeError) as exc:
        raise InvalidAudio('invalid_audio') from exc


def _audio_to_transcript(raw):
    # This worker owns its file for its entire lifetime, including after HTTP timeout.
    # No filename/path from the caller. TemporaryDirectory is private (0700).
    with tempfile.TemporaryDirectory(prefix='cordlet-voice-', dir=_hermes_home()) as directory:
        path = Path(directory) / 'recording.m4a'
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as file:
            file.write(raw)
        _probe_audio(path)
        result = _transcribe_audio(path)
        if not isinstance(result, dict) or result.get('success') is not True:
            raise RuntimeError('transcription_unavailable')
        transcript = result.get('transcript')
        if not isinstance(transcript, str) or not transcript.strip():
            raise InvalidAudio('inaudible_audio')
        # Quote every line, not a protocol/control message; native gateway controls off.
        return 'Quoted voice input (transcribed; not gateway controls):\n' + '\n'.join(
            '> ' + line for line in transcript.strip().splitlines())


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
    # Exactly one STT worker, not a semaphore queue of waiting threads.
    voice = None

    def error(name, status):
        return web.json_response({'error': name}, status=status)

    def owner():
        runner = getattr(api_adapter, 'gateway_runner', None)
        discord = (getattr(runner, 'adapters', None) or {}).get(Platform.DISCORD)
        if discord is None:
            return None, None, error('discord_unavailable', 503)
        source = SessionSource(platform=Platform.DISCORD, chat_id=config['chat_id'],
                               chat_type='dm', user_id=config['user_id'],
                               user_name=config.get('user_name', 'Cordlet user'),
                               chat_name=config.get('chat_name', 'Cordlet DM'))
        authorize = getattr(runner, '_is_user_authorized', None)
        if not callable(authorize):
            return None, None, error('authorization_unavailable', 503)
        if not authorize(source):
            return None, None, error('owner_not_authorized', 403)
        return discord, source, None

    def duplicate(rid, fingerprint):
        if rid not in accepted:
            return None
        if accepted[rid] != fingerprint:
            return error('request_id_conflict', 409)
        return receipt(rid, True)

    def receipt(rid, repeated=False):
        return web.json_response({'request_id': rid, 'status': 'accepted', 'duplicate': repeated}, status=202)

    async def admit(rid, fingerprint, text):
        # Caller holds lock: receipt, dispatch and insertion are one admission operation.
        previous = duplicate(rid, fingerprint)
        if previous is not None:
            return previous
        discord, source, failure = owner()
        if failure is not None:
            return failure
        event = MessageEvent(text=text, source=source, user_id=source.user_id,
                             user_name=source.user_name, internal=True,
                             allow_gateway_control=False,
                             metadata={'cordlet_request_id': rid})
        await discord.handle_message(event)
        if event._gateway_accepted is not True:
            return error('gateway_not_admitted', 503)
        accepted[rid] = fingerprint
        if len(accepted) > 512:
            accepted.popitem(last=False)
        return receipt(rid)

    async def voice_job(rid, fingerprint, worker):
        try:
            text = await asyncio.wait_for(asyncio.shield(worker), STT_TIMEOUT_SECONDS)
        except InvalidAudio as exc:
            return error(str(exc), 422)
        except Exception:
            # Do not expose provider errors or discard the client's retryable note.
            return error('transcription_unavailable', 503)
        async with lock:
            return await admit(rid, fingerprint, text)

    def release_voice(state):
        nonlocal voice
        # Both the admission task and thread must finish before another worker starts.
        if state['worker'].done() and state['job'].done():
            if voice is state:
                voice = None
        # Retrieve exceptions even if every HTTP waiter disconnected/timed out.
        for key in ('worker', 'job'):
            task = state[key]
            if task.done() and not task.cancelled():
                task.exception()

    async def submit(request):
        nonlocal voice
        supplied = request.headers.get('Authorization', '')
        if not hmac.compare_digest(supplied.encode(), ('Bearer ' + token).encode()):
            return error('unauthorized', 401)
        is_audio = request.content_type == 'audio/mp4'
        if request.content_type.startswith('audio/') and not is_audio:
            return error('unsupported_media_type', 415)
        if is_audio:
            rid = request.headers.get('X-Cordlet-Request-ID', '')
            if not re.fullmatch(REQUEST_ID, rid):
                return error('invalid_input', 400)
            # Internal events bypass cold auth, so check BEFORE body storage/STT.
            _, _, failure = owner()
            if failure is not None:
                return failure
        limit = AUDIO_MAX_BYTES if is_audio else TEXT_MAX_BYTES
        raw = bytearray()
        async for chunk in request.content.iter_chunked(65536 if is_audio else 8192):
            if len(raw) + len(chunk) > limit:
                return error('request_too_large', 413)
            raw.extend(chunk)
        if is_audio:
            if not raw:
                return error('invalid_audio', 422)
            fingerprint = hashlib.sha256(b'voice\0' + raw).digest()
            async with lock:
                previous = duplicate(rid, fingerprint)
                if previous is not None:
                    return previous
                if voice is not None:
                    if voice['rid'] == rid and voice['fingerprint'] != fingerprint:
                        return error('request_id_conflict', 409)
                    if voice['rid'] != rid:
                        return error('transcription_busy', 503)
                    job = voice['job']
                    repeated = True
                else:
                    repeated = False
                    worker = asyncio.create_task(asyncio.to_thread(_audio_to_transcript, bytes(raw)))
                    job = asyncio.create_task(voice_job(rid, fingerprint, worker))
                    voice = {'rid': rid, 'fingerprint': fingerprint, 'worker': worker, 'job': job}
                    state = voice
                    worker.add_done_callback(lambda _: release_voice(state))
                    job.add_done_callback(lambda _: release_voice(state))
            # Disconnects must not cancel a shared job or orphan its thread/file.
            result = await asyncio.shield(job)
            # Each request gets its own response object (aiohttp responses are single-use).
            body = json.loads(result.body)
            if result.status == 202 and repeated:
                body['duplicate'] = True
            return web.json_response(body, status=result.status)
        try:
            body = json.loads(raw)
        except (ValueError, UnicodeError):
            return error('invalid_json', 400)
        if not isinstance(body, dict) or set(body) != {'request_id', 'text'}:
            return error('expected_request_id_and_text_only', 400)
        rid, text = body['request_id'], body['text']
        if (not isinstance(rid, str) or not re.fullmatch(REQUEST_ID, rid)
                or not isinstance(text, str) or not text.strip() or len(text) > 4000):
            return error('invalid_input', 400)
        fingerprint = hashlib.sha256(b'text\0' + text.encode()).digest()
        async with lock:
            if voice is not None and voice['rid'] == rid:
                return error('request_id_conflict', 409)
            return await admit(rid, fingerprint, text)

    app.router.add_post('/cordlet/messages', submit)


def register(ctx):
    def attach(app, adapter):
        from hermes_constants import get_hermes_home
        filename = os.environ.get('CORDLET_INGRESS_CONFIG')
        config_path = Path(filename) if filename else get_hermes_home() / 'cordlet-ingress.json'
        if not config_path.is_file():
            return
        config = json.loads(config_path.read_text())
        wire(app, adapter, config)
    ctx.register_platform_handler('api_server', attach)
