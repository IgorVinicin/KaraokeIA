"""ADB UI helper: capture evidence and send individual controller events."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
ROOT = Path(__file__).parent

def adb(*args):
    result = subprocess.run([ADB, '-e', *args], capture_output=True, timeout=45)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/home-qa-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/home-qa-ui.xml'))

def capture(name):
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', name):
        raise ValueError('Invalid capture name')
    doc = tree()
    (ROOT / (name + '.xml')).write_bytes(ET.tostring(doc, encoding='utf-8'))
    (ROOT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    nodes = [{k: n.get(k) for k in ('text', 'content-desc', 'bounds', 'focused', 'enabled')}
             for n in doc.iter('node')
             if n.get('text') or n.get('content-desc') or n.get('focused') == 'true']
    print(json.dumps(nodes, ensure_ascii=False))

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    command, *args = sys.argv[1:]
    if command == 'capture':
        capture(args[0])
    elif command == 'key':
        for code in args:
            adb('shell', 'input', 'keyevent', code)
    elif command == 'tap':
        label = args[0]
        node = next(n for n in tree().iter('node')
                    if label in (n.get('text'), n.get('content-desc')))
        left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
    elif command == 'text':
        adb('shell', 'input', 'text', args[0])
    else:
        raise ValueError('Unknown command')
