from pathlib import Path
import hashlib, json, zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / 'app/src/main/assets/chatgpt-original.apkm'
EXPECTED_HASH = 'cd7af30278fcca6b37cb0a7ee77aebc2fd562039b05a2ea4263c6498698e3c8f'


def test_original_apkm_matches_uploaded_archive():
    assert ASSET.stat().st_size == 94_860_022
    with ASSET.open('rb') as f:
        assert hashlib.file_digest(f, 'sha256').hexdigest() == EXPECTED_HASH
    with zipfile.ZipFile(ASSET) as z:
        assert z.testzip() is None
        assert len([n for n in z.namelist() if n.endswith('.apk')]) == 37
        info = json.loads(z.read('info.json'))
        assert info['pname'] == 'com.openai.chatgpt'
        assert info['versioncode'] == '2627907'
        assert info['min_api'] == '32'
        assert 'base.apk' in z.namelist()
        assert 'split_config.arm64_v8a.apk' in z.namelist()
        assert all('/' not in name for name in z.namelist() if name.endswith('.apk'))


def test_bundled_installer_uses_session_and_archive_integrity():
    src = (ROOT / 'app/src/main/java/com/jeyu/clashgptstarter/BundledChatGptInstaller.java').read_text()
    assert EXPECTED_HASH in src
    assert 'ChatGptSplitSelector.select(' in src
    assert 'ZipInputStream' in src and 'getNextEntry' in src
    assert 'getAssets().open(' in src
    assert 'PackageInstaller.SessionParams.MODE_FULL_INSTALL' in src
    assert 'session.openWrite(' in src and 'session.fsync(output)' in src
    assert 'session.commit(' in src and 'session.abandon()' in src
    assert 'STATUS_PENDING_USER_ACTION' not in src  # system callback belongs in InstallResultReceiver
    assert 'REQUEST_INSTALL_PACKAGES' not in src  # permission request belongs in Activity/manifest
    assert 'MAX_UNCOMPRESSED_BYTES' in src
    assert 'Build.VERSION.SDK_INT < 32' in src
    assert 'PendingIntent.FLAG_MUTABLE' in src


def test_build_includes_uncompressed_apkm_and_correct_ui():
    gradle = (ROOT / 'app/build.gradle').read_text()
    assert 'noCompress' in gradle and 'apkm' in gradle
    main = (ROOT / 'app/src/main/java/com/jeyu/clashgptstarter/MainActivity.java').read_text()
    assert 'BundledChatGptInstaller.installBundled' in (ROOT / 'app/src/main/java/com/jeyu/clashgptstarter/ChatGptSourceInstaller.java').read_text()
    assert 'installBundledChatGpt();' in main and 'installBundledGemini();' in main
    assert '安装内置 ChatGPT' in main
    assert 'Android 12L' in main
    assert 'openClash(true)' in main
    assert 'ServiceNetworkGate.probe(getApplicationContext(),selected)' in main

if __name__ == '__main__':
    import inspect
    funcs=[v for k,v in globals().items() if k.startswith('test_') and inspect.isfunction(v)]
    for fn in funcs:
        fn(); print('PASS:',fn.__name__)
    print('PASS:',len(funcs),'offline APKM contracts')
