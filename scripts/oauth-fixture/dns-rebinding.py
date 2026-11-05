#!/usr/bin/env python3
"""Small authoritative A/AAAA fault fixture for a delegated, controlled test hostname.

Each query advances that address family's configured answer sequence, holding its final answer.
TTL is zero. No recursion, forwarding, arbitrary hostnames, or production credentials.
"""
import argparse
import ipaddress
import json
import socket
import socketserver
import struct
import threading


def question(packet):
    if len(packet) < 12:
        raise ValueError('short DNS header')
    ident, flags, count, _, _, _ = struct.unpack('!6H', packet[:12])
    if flags & 0xF800 or count != 1:
        raise ValueError('only standard queries with one question are supported')
    labels, cursor = [], 12
    while True:
        if cursor >= len(packet):
            raise ValueError('truncated question')
        size = packet[cursor]
        cursor += 1
        if size == 0:
            break
        if size > 63 or cursor + size > len(packet):
            raise ValueError('compressed or invalid question')
        labels.append(packet[cursor:cursor+size].decode('ascii').lower())
        cursor += size
        if cursor > 267:
            raise ValueError('name too long')
    if cursor + 4 > len(packet):
        raise ValueError('truncated question type')
    qtype, qclass = struct.unpack('!HH', packet[cursor:cursor+4])
    return ident, flags, '.'.join(labels), qtype, qclass, packet[12:cursor+4]


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        packet, sock = self.request
        try:
            ident, flags, host, qtype, qclass, wire = question(packet)
            answers = []
            status = 0
            with self.server.lock:
                if host != self.server.hostname:
                    status = 5  # REFUSED, never recurse for arbitrary names.
                elif qclass == 1 and qtype in (1, 28):
                    family = 'a' if qtype == 1 else 'aaaa'
                    number = self.server.counts[family]
                    sequence = self.server.plan[family]
                    answers = sequence[min(number, len(sequence)-1)]
                    self.server.counts[family] += 1
                    print(json.dumps({'family': family, 'query': number+1, 'answers': [str(a) for a in answers]}), flush=True)
            header = struct.pack('!6H', ident, 0x8400 | (flags & 0x0100) | status, 1, len(answers), 0, 0)
            records = b''.join(b'\xc0\x0c' + struct.pack('!HHIH', qtype, 1, 0, len(a.packed)) + a.packed for a in answers)
            sock.sendto(header + wire + records, self.client_address)
        except (ValueError, UnicodeError, struct.error):
            return


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5353)
    parser.add_argument('--hostname', required=True)
    parser.add_argument('--plan', required=True, help='JSON object: a and aaaa arrays of successive address lists')
    args = parser.parse_args()
    hostname = args.hostname.rstrip('.').lower()
    if len(hostname) > 253 or '.' not in hostname or not all(label and len(label) <= 63 and all(c.isascii() and (c.isalnum() or c == '-') for c in label) for label in hostname.split('.')):
        parser.error('provide a valid controlled hostname')
    with open(args.plan, encoding='utf-8') as stream:
        plan = json.load(stream)
    for family, version in (('a', 4), ('aaaa', 6)):
        sequence = plan.get(family, [[]])
        if not isinstance(sequence, list) or not 1 <= len(sequence) <= 100:
            parser.error('each sequence must have 1..100 answer sets')
        normalized = []
        for answers in sequence:
            if not isinstance(answers, list) or len(answers) > 8:
                parser.error('answer sets must be lists of at most eight IPs')
            addresses = [ipaddress.ip_address(a) for a in answers]
            if any(a.version != version for a in addresses):
                parser.error('address family mismatch')
            normalized.append(addresses)
        plan[family] = normalized
    class Server(socketserver.ThreadingUDPServer):
        address_family = socket.AF_INET6 if ':' in args.bind else socket.AF_INET
        daemon_threads = True
    with Server((args.bind, args.port), Handler) as server:
        server.hostname, server.plan = hostname, plan
        server.counts, server.lock = {'a': 0, 'aaaa': 0}, threading.Lock()
        print('Authoritative synthetic DNS fixture ready; TTL=0, recursion disabled.', flush=True)
        server.serve_forever()


if __name__ == '__main__':
    main()
