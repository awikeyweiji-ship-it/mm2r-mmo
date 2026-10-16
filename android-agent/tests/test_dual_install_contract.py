from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/jeyu/clashgptstarter'

def test_local_chatgpt_source_checked():
    s=(SRC/'ChatGptSourceInstaller.java').read_text()
    assert 'getPackageArchiveInfo' in s
    assert 'GET_SIGNING_CERTIFICATES' in s
    assert 'getApkContentsSigners' in s
    assert 'PackageInstaller' not in s or 'AppInstaller.installLocalChatGpt' in s
    assert 'TrustedApkPolicy.isTrustedChatGpt' in s
    assert 'MAX_BYTES' in s
    assert 'delete()' in s

def test_installer_has_second_package_session():
    s=(SRC/'AppInstaller.java').read_text()
    assert 'installLocalChatGpt' in s
    assert 'setAppPackageName(expectedPackage)' in s
    assert 'PackageInstaller.SessionParams.MODE_FULL_INSTALL' in s
    assert 'session.commit(' in s
    assert 'INSTALL_RESULT' in s

def test_ui_has_one_guide_and_two_trusted_acquisition_routes():
    s=(SRC/'MainActivity.java').read_text()
    assert 'ACTION_OPEN_DOCUMENT' in s
    assert 'onActivityResult' in s
    assert '安装 ChatGPT' in s
    assert 'openChatGptPlay' in s
    assert 'installChatGptFromUri' in s
    assert 'showSettings()' in s
    assert 'ServiceNetworkGate.probe' in s

def test_install_consent_is_never_bypassed():
    root=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
    assert 'REQUEST_INSTALL_PACKAGES' in root
    assert 'BIND_ACCESSIBILITY_SERVICE' not in root
    assert 'INSTALL_PACKAGES' not in root.replace('REQUEST_INSTALL_PACKAGES','')
    s=(SRC/'AppInstaller.java').read_text()
    assert 'setRequireUserAction' not in s

def test_original_chatgpt_apkm_is_bundled_and_byte_verified():
    asset=ROOT/'app/src/main/assets/chatgpt-original.apkm'
    assert asset.exists(), "ChatGPT original APKM was not bundled"
    assert asset.stat().st_size == 94_860_022
    text=(SRC/'BundledChatGptInstaller.java').read_text()
    assert 'cd7af30278fcca6b37cb0a7ee77aebc2fd562039b05a2ea4263c6498698e3c8f' in text
    assert (ROOT/'app/src/main/assets/clash-meta.apk').exists()

if __name__=='__main__':
    t=[v for k,v in globals().items() if k.startswith('test_') and callable(v)]
    for f in t:
        f();print('PASS:',f.__name__)
    print('PASS:',len(t),'dual installer contract tests')
