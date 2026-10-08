import pathlib
import subprocess
import sys

original = pathlib.Path(sys.argv[2]).read_bytes()
restored = subprocess.check_output([
    sys.argv[1], '-e', 'shell', 'run-as', 'com.araujo.karaoke_ia',
    'cat', 'shared_prefs/' + sys.argv[3],
])
def adb_newlines(data):
    # Windows adb shell adds CR characters to newline separators.
    return data.replace(b'\r\r\n', b'\n').replace(b'\r\n', b'\n').strip()

sys.exit(0 if adb_newlines(original) == adb_newlines(restored) else 1)
