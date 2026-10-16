import hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/com/jeyu/clashgptstarter'
assert (java/'GeminiInstaller.java').exists(),'GeminiInstaller missing'
assert (java/'MihomoController.java').exists(),'MihomoController missing'
assert (java/'ServiceNetworkGate.java').exists(),'ServiceNetworkGate missing'
assert (java/'ControllerSecrets.java').exists(),'ControllerSecrets missing'
a=root/'app/src/main/assets/gemini-original.apk'
assert a.exists(),'Gemini original APK not bundled'
assert hashlib.sha256(a.read_bytes()).hexdigest()=='df5d131c354853764de41cb6a5e579692387971e17d4b0b3beb2e08edd587c68'
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'com.google.android.apps.bard' in manifest
assert 'networkSecurityConfig' in manifest
controller=(java/'MihomoController.java').read_text()
assert '127.0.0.1' in controller and 'Authorization' in controller
assert 'https://' in (java/'ServiceTarget.java').read_text()
assert 'http://' in controller  # loopback-only control channel
assert '0.0.0.0' not in controller
ui=(java/'MainActivity.java').read_text()
assert 'Gemini' in ui and 'ChatGPT' in ui and '智能选点' in ui
assert 'installBundledGemini' in ui
print('PASS: 11 Gemini + node controller static contracts')
