#!/usr/bin/env python3
"""Disposable, memory-only OAuth public-client fixture. Synthetic identities only.

Serve behind a controlled public HTTPS origin on port 443 to exercise the production host.
No production host DNS/TLS/SSRF exceptions are needed or provided by this fixture.
"""
import argparse
import base64
import hashlib
import hmac
import html
import json
import os
import re
import secrets
import ssl
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlencode, urlsplit

REDIRECT = 'com.lladlam.melox.plugin:/oauth2redirect'
CLIENT = 'melox-rc-public'
lock = threading.RLock()
codes, access, refresh, denied_refresh, events = {}, {}, {}, set(), []


def event(kind, **fields):
    # Never record authorization codes, verifier/state, cookies or token values.
    events.append(dict(sequence=len(events)+1, kind=kind, **fields))
    if len(events) > 500:
        del events[:100]


def one(values, key):
    value = values.get(key)
    if not value or len(value) != 1:
        raise ValueError('invalid field')
    return value[0]


def digest(value):
    return hashlib.sha256(value.encode('ascii')).hexdigest()


def challenge(verifier):
    return base64.urlsafe_b64encode(hashlib.sha256(verifier.encode('ascii')).digest()).decode().rstrip('=')


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *args):
        pass  # HTTP request paths can contain authorization codes/state; do not log them.

    def reply(self, status, value, headers=None):
        body = value.encode() if isinstance(value, str) else json.dumps(value).encode()
        headers = dict(headers or {})
        self.send_response(status)
        self.send_header('Content-Type', headers.pop('Content-Type', 'text/html; charset=utf-8' if isinstance(value, str) else 'application/json'))
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'none'; form-action 'self' com.lladlam.melox.plugin:; frame-ancestors 'none'")
        for key, value in headers.items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def form(self):
        if self.headers.get('Content-Type', '').split(';')[0] != 'application/x-www-form-urlencoded':
            raise ValueError('invalid content type')
        size = int(self.headers.get('Content-Length', '-1'))
        if not 0 <= size <= 16384 or self.headers.get('Transfer-Encoding'):
            raise ValueError('invalid body size')
        return parse_qs(self.rfile.read(size).decode('utf-8', 'strict'), keep_blank_values=True, max_num_fields=16)

    def authorization(self, values):
        if one(values, 'client_id') != CLIENT or one(values, 'redirect_uri') != REDIRECT:
            raise ValueError('unregistered client or redirect')
        if one(values, 'response_type') != 'code' or one(values, 'code_challenge_method') != 'S256':
            raise ValueError('PKCE S256 required')
        if not re.fullmatch(r'[A-Za-z0-9_-]{43}', one(values, 'code_challenge')):
            raise ValueError('invalid challenge')
        if not re.fullmatch(r'[A-Za-z0-9_-]{32,128}', one(values, 'state')):
            raise ValueError('invalid state')

    def do_GET(self):
        try:
            path = urlsplit(self.path)
            if path.path == '/authorize':
                values = parse_qs(path.query, keep_blank_values=True, max_num_fields=16)
                self.authorization(values)
                hidden = ''.join('<input type="hidden" name="%s" value="%s">' % (html.escape(k, quote=True), html.escape(one(values, k), quote=True))
                                 for k in ('client_id', 'redirect_uri', 'response_type', 'code_challenge_method', 'code_challenge', 'state'))
                self.reply(200, '<h1>MeloX disposable OAuth test</h1><p>Synthetic accounts only. No passwords or real platform access.</p>'
                           '<form method="post" action="/consent">'+hidden+
                           '<label>Account <select name="account"><option value="alpha">Synthetic Alpha</option><option value="beta">Synthetic Beta</option></select></label>'
                           '<button name="decision" value="allow">Allow synthetic access</button><button name="decision" value="deny">Deny</button></form>',
                           {'Set-Cookie': 'rc_browser_only='+secrets.token_urlsafe(16)+'; Secure; HttpOnly; SameSite=Lax; Path=/'})
            elif path.path == '/api/me':
                header = self.headers.get('Authorization', '')
                token = header.removeprefix('Bearer ') if header.startswith('Bearer ') else ''
                with lock:
                    item = access.get(digest(token)) if token else None
                    event('api', cookiePresent=bool(self.headers.get('Cookie')), authorized=bool(item and item['expires'] > time.monotonic()))
                    if not item or item['expires'] <= time.monotonic():
                        self.reply(401, {'error': 'invalid_token'}); return
                    self.reply(200, {'id': item['account'], 'displayName': 'Synthetic '+item['account'].title()})
            elif path.path == '/health':
                self.reply(200, {'fixture': 'melox-oauth-v1', 'synthetic': True})
            elif path.path == '/fault/redirect-outside':
                self.reply(302, '', {'Location': 'https://undeclared.invalid/must-not-connect'})
            elif path.path == '/fault/redirect-loop':
                self.reply(307, '', {'Location': '/fault/redirect-loop'})
            elif path.path == '/fault/large':
                self.reply(200, {'synthetic': 'x' * (64 * 1024 + 1)})
            elif path.path == '/fault/gzip-large':
                import gzip
                body = gzip.compress(b'x' * (64 * 1024 + 1))
                self.send_response(200)
                self.send_header('Content-Type', 'application/octet-stream')
                self.send_header('Content-Encoding', 'gzip')
                self.send_header('Content-Length', str(len(body)))
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(body)
            elif path.path == '/fault/adaptive':
                self.reply(200, '#EXTM3U\n', {'Content-Type': 'application/vnd.apple.mpegurl'})
            elif path.path == '/admin/events' and self.admin():
                with lock:
                    self.reply(200, list(events))
            else:
                self.reply(404, {'error': 'not_found'})
        except (ValueError, UnicodeError):
            self.reply(400, {'error': 'invalid_request'})

    def admin(self):
        expected = self.server.admin_secret
        return bool(expected) and hmac.compare_digest(self.headers.get('X-RC-Admin', ''), expected)

    def issue(self, account):
        token, rotated = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        access[digest(token)] = {'account': account, 'expires': time.monotonic()+self.server.ttl}
        refresh[digest(rotated)] = {'account': account, 'expires': time.monotonic()+600}
        return {'token_type': 'Bearer', 'access_token': token, 'refresh_token': rotated, 'expires_in': self.server.ttl}

    def do_POST(self):
        try:
            values = self.form()
            path = urlsplit(self.path).path
            with lock:
                if path == '/consent':
                    self.authorization(values)
                    account, decision = one(values, 'account'), one(values, 'decision')
                    if account not in ('alpha', 'beta') or decision not in ('allow', 'deny'):
                        raise ValueError('invalid consent')
                    state = one(values, 'state')
                    if decision == 'deny':
                        callback = {'error': 'access_denied', 'state': state}
                    else:
                        if len(codes) >= 100:
                            codes.clear()
                        code = secrets.token_urlsafe(32)
                        codes[digest(code)] = {'account': account, 'challenge': one(values, 'code_challenge'), 'expires': time.monotonic()+60}
                        callback = {'code': code, 'state': state}
                    event('consent', account=account, allowed=decision == 'allow')
                    self.reply(303, '', {'Location': REDIRECT+'?'+urlencode(callback)})
                elif path == '/token':
                    if one(values, 'client_id') != CLIENT or 'client_secret' in values:
                        raise ValueError('not a public client')
                    grant = one(values, 'grant_type')
                    if grant == 'authorization_code':
                        if one(values, 'redirect_uri') != REDIRECT:
                            raise ValueError('invalid redirect')
                        verifier = one(values, 'code_verifier')
                        if not re.fullmatch(r'[A-Za-z0-9._~-]{43,128}', verifier):
                            raise ValueError('invalid verifier')
                        item = codes.pop(digest(one(values, 'code')), None)
                        valid = bool(item and item['expires'] > time.monotonic() and hmac.compare_digest(item['challenge'], challenge(verifier)))
                    elif grant == 'refresh_token':
                        item = refresh.pop(digest(one(values, 'refresh_token')), None)
                        valid = bool(item and item['expires'] > time.monotonic() and item['account'] not in denied_refresh)
                    else:
                        raise ValueError('unsupported grant')
                    event('token', grant=grant, accepted=valid, cookiePresent=bool(self.headers.get('Cookie')))
                    if not valid:
                        self.reply(400, {'error': 'invalid_grant'}); return
                    # Bound memory without persisting any synthetic secret to disk.
                    if len(access) > 500:
                        access.clear()
                    if len(refresh) > 500:
                        refresh.clear()
                    self.reply(200, self.issue(item['account']))
                elif path == '/admin/control' and self.admin():
                    action, account = one(values, 'action'), one(values, 'account')
                    if account not in ('alpha', 'beta'):
                        raise ValueError('invalid account')
                    if action == 'expire':
                        for token in access.values():
                            if token['account'] == account:
                                token['expires'] = 0
                    elif action == 'fail-refresh':
                        denied_refresh.add(account)
                    elif action == 'allow-refresh':
                        denied_refresh.discard(account)
                    else:
                        raise ValueError('invalid control')
                    event('control', action=action, account=account)
                    self.reply(200, {'ok': True})
                else:
                    self.reply(404, {'error': 'not_found'})
        except (ValueError, UnicodeError):
            self.reply(400, {'error': 'invalid_request'})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--port', default=8443, type=int)
    parser.add_argument('--cert', required=True)
    parser.add_argument('--key', required=True)
    parser.add_argument('--ttl', type=int, default=15)
    args = parser.parse_args()
    if not 1 <= args.ttl <= 300:
        parser.error('ttl must be 1..300')
    server = ThreadingHTTPServer((args.bind, args.port), Handler)
    server.daemon_threads = True
    server.ttl = args.ttl
    server.admin_secret = os.environ.get('MELOX_RC_OAUTH_ADMIN', '')
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.minimum_version = ssl.TLSVersion.TLSv1_2
    tls.load_cert_chain(args.cert, args.key)
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    print('Synthetic OAuth HTTPS fixture ready; request logging disabled.', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
