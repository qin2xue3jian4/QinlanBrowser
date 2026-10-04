"""Stream an explicitly supplied cookie file into this debug app's private storage.
The test instrumentation deletes it after import. Never prints credential contents.
"""
import pathlib
import subprocess
import sys

ADB=r'D:\ProgramFiles\Android\Sdk\platform-tools\adb.exe'
source=pathlib.Path(sys.argv[1])
payload=source.read_bytes()
if len(payload)>1048576: raise SystemExit('File exceeds 1 MB')
subprocess.run([ADB,'shell','run-as','dev.qinglan.browser','sh','-c',"'cat > files/authorized-cookie-import.json'"],input=payload,check=True,stdout=subprocess.DEVNULL)
print('Transferred authorized file into app-private storage; contents not logged.')
