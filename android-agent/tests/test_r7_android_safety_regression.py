from pathlib import Path
BASE=Path(__file__).resolve().parents[1]/'app/src/main/java/com/jeyu/clashgptstarter'

def test_deduplicated_ciphertext_verified_before_reusing():
    source=(BASE/'R7FileVault.java').read_text()
    branch=source.split('if(!target.exists()){',1)[1].split('return new Imported',1)[0]
    assert 'verify(projectId,hex,len)' in branch, 'pre-existing corrupt encrypted blob must not become a valid metadata entry'

def test_mobile_explicit_model_and_consent_are_sent_to_backend():
    api=(BASE/'R7SyncClient.java').read_text()
    ui=(BASE/'R7ProjectsActivity.java').read_text()
    assert 'remote_data_consent' in api and 'model_id' in api
    assert 'remoteModel' in ui and 'remoteConsent' in ui
    assert 'demo/no-llm' in api
