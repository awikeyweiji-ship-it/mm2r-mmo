from pathlib import Path
r=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/jeyu/clashgptstarter/SmartRouteEngine.java').read_text()
assert 'boolean selectedSuccessfully=false;' in r, 'missing tracked success state'
assert 'selectedSuccessfully=true;' in r, 'must distinguish successful selection'
assert 'if(!selectedSuccessfully' in r, 'failed scan must restore original selector'
assert 'controller.select(selector,original' in r, 'original selection not restored'
print('PASS: auto-selection restores original route on unsuccessful verification')
