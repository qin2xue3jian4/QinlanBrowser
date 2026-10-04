"""Small ADB inspection helper; saves captures only inside this project."""
import subprocess
import sys
import re
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = r'D:\ProgramFiles\Android\Sdk\platform-tools\adb.exe'

def adb(*args):
    return subprocess.check_output([ADB, *args], stderr=subprocess.STDOUT)

def snapshot():
    for attempt in range(3):
        result=adb('shell', 'uiautomator', 'dump', '/data/local/tmp/qinglan-window.xml')
        if b'UI hierchary dumped' in result: break
        if attempt==2: raise RuntimeError('Could not capture a fresh UI hierarchy')
        time.sleep(0.7)
    raw = adb('shell', 'cat', '/data/local/tmp/qinglan-window.xml').decode('utf-8')
    doc = ET.fromstring(raw[raw.index('<?xml'):])
    for n in doc.iter('node'):
        a=n.attrib
        if a.get('text') or a.get('content-desc') or a.get('class','').endswith('EditText'):
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
    elif action=='text': adb('shell','input','text',sys.argv[2])
    elif action=='back': adb('shell','input','keyevent','4')
