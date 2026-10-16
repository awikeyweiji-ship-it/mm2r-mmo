from pathlib import Path
root=Path(__file__).resolve().parents[1]
activity=(root/'app/src/main/java/com/jeyu/clashgptstarter/MainActivity.java').read_text()
for text in ['private ServiceTarget target','chooseTarget(ServiceTarget','ServiceTarget.GEMINI','ServiceTarget.CHATGPT',
             'GeminiInstaller.installBundledGemini','SmartRouteEngine.optimize','ControllerSecrets.save',
             'launchTarget()','智能选点','target.packageName']:
    assert text in activity, text
assert 'installAfterPermission' in activity
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'com.google.android.apps.bard' in manifest
assert 'networkSecurityConfig' in manifest
print('PASS: R5 target switching, setup and smart-route UI contracts')
