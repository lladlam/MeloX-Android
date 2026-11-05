#!/usr/bin/env python3
"""ADB accessibility helper. Each action uses a fresh hierarchy; evidence stays local.

Usage: python3 scripts/plugin-rc-ui.py dump|tap TEXT|input ASCII|swipe up|capture NAME
Set ANDROID_SERIAL and MELOX_RC_EVIDENCE (default /tmp/opencode/melox-rc-ui).
This is an operator helper, not a test that declares the entire application PASS.
"""
import os
import re
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

adb = [os.environ.get('ADB', 'adb')]
if os.environ.get('ANDROID_SERIAL'):
    adb += ['-s', os.environ['ANDROID_SERIAL']]
root = Path(os.environ.get('MELOX_RC_EVIDENCE', '/tmp/opencode/melox-rc-ui'))
root.mkdir(parents=True, exist_ok=True)


def run(*args):
    return subprocess.check_output(adb + list(args))


def dump():
    run('shell', 'uiautomator', 'dump', '/sdcard/melox-rc-window.xml')
    raw = run('shell', 'cat', '/sdcard/melox-rc-window.xml')
    (root / 'latest.xml').write_bytes(raw)
    return ET.fromstring(raw)


command = sys.argv[1]
if command == 'dump':
    for n in dump().iter('node'):
        label = n.get('text') or n.get('content-desc')
        if label:
            print(n.get('bounds'), repr(label), 'enabled=' + n.get('enabled', ''))
elif command == 'tap':
    text = sys.argv[2]
    matches = [n for n in dump().iter('node') if text in (n.get('text') or n.get('content-desc') or '')]
    exact = [n for n in matches if text in (n.get('text'), n.get('content-desc'))]
    if exact:
        matches = exact
    if len(matches) > 1:
        # Compose exposes both a clickable semantic parent and its text child.
        clickable = [n for n in matches if n.get('clickable') == 'true']
        if len(clickable) == 1:
            matches = clickable
        elif exact:
            rectangles = [list(map(int, re.findall(r'\d+', n.get('bounds')))) for n in matches]
            for i, (a, b, c, d) in enumerate(rectangles):
                if all(a <= x and b <= y and c >= z and d >= w for x, y, z, w in rectangles):
                    matches = [matches[i]]
                    break
    if len(matches) != 1:
        raise SystemExit(f'Expected exactly one match for {text!r}, got {len(matches)}')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', matches[0].get('bounds')))
    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
elif command == 'input':
    run('shell', 'input', 'text', sys.argv[2].replace(' ', '%s'))
elif command == 'swipe':
    tree = dump()
    bounds = list(map(int, re.findall(r'\d+', next(tree.iter('node')).get('bounds'))))
    x1, y1, x2, y2 = bounds
    x = (x1+x2)//2
    top, bottom = y1+(y2-y1)//4, y1+(y2-y1)*3//4
    start, end = (bottom, top) if sys.argv[2] == 'up' else (top, bottom)
    run('shell', 'input', 'swipe', str(x), str(start), str(x), str(end), '400')
elif command == 'capture':
    name = sys.argv[2]
    if not re.fullmatch(r'[A-Za-z0-9_-]+', name):
        raise SystemExit('Invalid evidence name')
    dump()
    (root / (name+'.xml')).write_bytes((root/'latest.xml').read_bytes())
    (root / (name+'.png')).write_bytes(run('exec-out', 'screencap', '-p'))
    print(root / name)
else:
    raise SystemExit('Unknown command')
