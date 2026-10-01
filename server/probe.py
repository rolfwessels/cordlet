"""Host-only live admission probe. Never prints the device credential.
Run inside the Hermes container after restarting the gateway.
"""
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid


def main():
    home = Path(os.environ.get('HERMES_HOME', '/opt/data'))
    config_path = Path(os.environ.get('CORDLET_INGRESS_CONFIG', str(home / 'cordlet-ingress.json')))
    config = json.loads(config_path.read_text())
    rid = 'cordlet-proof-' + uuid.uuid4().hex
    text = f'Phone ingress host proof {rid}. Please reply in this DM with exactly: {rid} received. This is only a harmless delivery test.'
    body = json.dumps({'request_id':rid, 'text':text}).encode()
    request = urllib.request.Request('http://127.0.0.1:8642/cordlet/messages', data=body,
        headers={'Authorization':'Bearer ' + config['token'], 'Content-Type':'application/json'}, method='POST')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(NoRedirect)
    print('Proof ID:', rid, flush=True)
    try:
        with opener.open(request, timeout=15) as response:
            print('HTTP', response.status)
            result = json.load(response)
            print({k:result.get(k) for k in ('request_id', 'status', 'duplicate')})
            print('Admission only. Verify this ID in the active DM transcript and the delivered Discord reply.')
            return 0 if response.status == 202 else 1
    except urllib.error.HTTPError as exc:
        print('HTTP', exc.code, '- live admission not proven')
        return 1
    except urllib.error.URLError:
        print('Gateway unreachable - live admission not proven')
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
