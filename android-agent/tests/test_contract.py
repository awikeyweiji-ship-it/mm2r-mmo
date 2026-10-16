import hashlib
import pathlib
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
APP = ROOT / 'app' / 'src' / 'main'
APK = APP / 'assets' / 'clash-meta.apk'
EXPECTED_HASH = '93a719347dc2318b6b6356c588646a7ee82fd7ab0355e7a865fe6954de1234f0'
ANDROID_NS = '{http://schemas.android.com/apk/res/android}'


def test_bundled_apk_is_unmodified():
    sha = hashlib.file_digest(APK.open('rb'), 'sha256').hexdigest()
    assert sha == EXPECTED_HASH, sha
    assert APK.stat().st_size > 40_000_000
    with zipfile.ZipFile(APK) as z:
        names = z.namelist()
        assert 'classes.dex' in names
        assert any(x.startswith('lib/arm64-v8a/') for x in names)
        assert not any(x.startswith('lib/x86') for x in names)


def test_manifest_permissions_are_scoped():
    root = ET.parse(APP / 'AndroidManifest.xml').getroot()
    perms = {p.attrib[ANDROID_NS + 'name'] for p in root.findall('uses-permission')}
    assert perms == {
        'android.permission.ACCESS_NETWORK_STATE',
        'android.permission.INTERNET',
        'android.permission.REQUEST_INSTALL_PACKAGES',
    }
    queries = root.find('queries')
    assert queries is not None
    visible = {p.attrib[ANDROID_NS + 'name'] for p in queries.findall('package')}
    assert visible == {'com.github.metacubex.clash.meta', 'com.openai.chatgpt', 'com.google.android.apps.bard', 'com.anthropic.claude'}
    activity = root.find('.//activity')
    assert activity.attrib[ANDROID_NS + 'exported'] == 'true'
    receiver = root.find('.//receiver')
    assert receiver.attrib[ANDROID_NS + 'exported'] == 'false'
    assert root.find('.//application').attrib[ANDROID_NS + 'allowBackup'] == 'false'


def test_android_build_configuration():
    gradle = (ROOT / 'app' / 'build.gradle').read_text()
    assert "minSdk 26" in gradle
    assert "targetSdk 35" in gradle
    assert "noCompress += ['apk', 'apkm']" in gradle
    assert "com.android.application" in gradle
    assert (ROOT / '.github' / 'workflows' / 'build.yml').exists()


def test_installer_hash_is_literal_and_sessions_are_used():
    source = (APP / 'java' / 'com' / 'jeyu' / 'clashgptstarter' / 'AppInstaller.java').read_text()
    assert EXPECTED_HASH in source
    assert 'PackageInstaller.SessionParams.MODE_FULL_INSTALL' in source
    assert 'session.openWrite(' in source
    assert 'session.commit(' in source
    assert 'session.fsync(output)' in source
    assert 'PendingIntent.FLAG_MUTABLE' in source


def test_vpn_gated_https_probe_and_official_chatgpt():
    main = (APP / 'java' / 'com' / 'jeyu' / 'clashgptstarter' / 'MainActivity.java').read_text()
    network = (APP / 'java' / 'com' / 'jeyu' / 'clashgptstarter' / 'NetworkGate.java').read_text()
    assert 'startFlow()' in main and 'target.packageName' in main
    assert 'NetworkGate.hasVpnTransport' in main
    assert 'ServiceNetworkGate.probe(getApplicationContext(),selected)' in main
    assert 'com.github.metacubex.clash.meta.action.START_CLASH' in main
    assert 'https://play.google.com/store/apps/details?id=' in main
    assert 'https://chatgpt.com/' in network
    assert 'HttpsURLConnection' in network
    assert 'NetworkCapabilities.TRANSPORT_VPN' in network
    assert 'startActivity(app)' in main
    assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' not in (APP / 'AndroidManifest.xml').read_text()


def test_chatgpt_probe_forced_onto_vpn_network():
    source = (APP / 'java' / 'com' / 'jeyu' / 'clashgptstarter' / 'NetworkGate.java').read_text()
    main = (APP / 'java' / 'com' / 'jeyu' / 'clashgptstarter' / 'MainActivity.java').read_text()
    # Detecting a VPN separately is not enough: a default URLConnection can use a direct network.
    assert 'Network findVpnNetwork(Context context)' in source
    assert 'vpn.openConnection(' in source
    assert 'new URL("https://chatgpt.com/").openConnection()' not in source
    assert 'ServiceNetworkGate.probe(getApplicationContext(),selected)' in main


if __name__ == '__main__':
    tests = [v for k,v in globals().items() if k.startswith('test_') and callable(v)]
    for item in tests:
        item()
        print('PASS:', item.__name__)
    print(f'PASS: {len(tests)} contract checks')
