from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/'app/src/main/java/com/jeyu/clashgptstarter/MainActivity.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
app=(root/'app/src/main/java/com/jeyu/clashgptstarter/ServiceTarget.java').read_text()
policy=(root/'app/src/main/java/com/jeyu/clashgptstarter/ClaudeSafetyPolicy.java')
checks=[
 ('uses correct official Claude package', '"com.anthropic.claude"' in app),
 ('Android package visibility', 'com.anthropic.claude' in manifest),
 ('third service selection', 'claudeChoice' in main and 'chooseTarget(ServiceTarget.CLAUDE)' in main),
 ('official install channel', 'https://play.google.com/store/apps/details?id=' in main or 'openClaudePlay()' in main),
 ('official Play package reference', 'AppInstaller.CLAUDE_PACKAGE' in main),
 ('eligibility acknowledgement', 'ClaudeSafetyPolicy.canLaunch' in main),
 ('no automation for Claude', 'ClaudeSafetyPolicy.shouldAutoSelect' in main),
 ('node switching is forbidden in route engine for Claude', 'target == ServiceTarget.CLAUDE' in (root/'app/src/main/java/com/jeyu/clashgptstarter/SmartRouteEngine.java').read_text()),
 ('cooldown handling', 'ClaudeSafetyPolicy.inCooldown' in main),
 ('eligible users may open official Claude without forced VPN', 'startClaudeSafeFlow()' in main),
 ('Claude official installation works on Android 8 while bundled partners need 12L', main.index('if(target==ServiceTarget.CLAUDE)', main.index('private void startFlow()')) < main.index('if(Build.VERSION.SDK_INT<32)', main.index('private void startFlow()'))),
 ('not claiming ban protection', '无法保证' in main or '不能保证' in main),
 ('policy source written', policy.is_file()),
 ('version six', "versionCode 6" in (root/'app/build.gradle').read_text()),
 ('avoids bundling Claude from mirrors', 'claude-original.apk' not in main),
]
for name,ok in checks:
 assert ok,name
 print('PASS:',name)
print('PASS: 15 Claude UI/safety contracts')
