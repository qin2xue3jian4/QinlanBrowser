"""Small ADB inspection helper; saves captures only inside this project."""
import subprocess
import os
import sys
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = os.environ.get('ADB', 'adb')

def adb(*args):
    return subprocess.check_output([ADB, *args], stderr=subprocess.STDOUT)

def snapshot(quiet=False):
    for attempt in range(3):
        result=adb('shell', 'uiautomator', 'dump', '/data/local/tmp/qinglan-window.xml')
        if b'UI hierchary dumped' in result: break
        if attempt==2: raise RuntimeError('Could not capture a fresh UI hierarchy')
        time.sleep(0.7)
    raw = adb('shell', 'cat', '/data/local/tmp/qinglan-window.xml').decode('utf-8')
    doc = ET.fromstring(raw[raw.index('<?xml'):])
    for n in doc.iter('node'):
        a=n.attrib
        if not quiet and (a.get('text') or a.get('content-desc') or a.get('class','').endswith('EditText')):
            print(a.get('class','').split('.')[-1], repr(a.get('text')), repr(a.get('content-desc')), a.get('bounds'))
    return doc

if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    action = sys.argv[1] if len(sys.argv)>1 else 'snapshot'
    if action=='snapshot': snapshot()
    elif action=='shot':
        dest=ROOT/'captures'/(sys.argv[2]+'.png');dest.parent.mkdir(exist_ok=True)
        dest.write_bytes(adb('exec-out','screencap','-p'));print(dest)
    elif action=='tap': adb('shell','input','tap',sys.argv[2],sys.argv[3])
    elif action=='tap-label':
        wanted=''.join(chr(int(x,16)) for x in sys.argv[2][2:].split('-')) if sys.argv[2].startswith('U+') else sys.argv[2]
        doc=snapshot(quiet=True)
        for n in doc.iter('node'):
            if wanted not in (n.get('text'),n.get('content-desc')): continue
            coords=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
            if len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]:
                adb('shell','input','tap',str((coords[0]+coords[2])//2),str((coords[1]+coords[3])//2));break
        else: raise RuntimeError('Visible label not found: '+wanted)
    elif action=='text': adb('shell','input','text',sys.argv[2])
    elif action=='back': adb('shell','input','keyevent','4')
