from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / 'app/src/main/java/com/jeyu/clashgptstarter'


def rgb(c):
    return tuple(int(c[k:k+2],16)/255 for k in (0,2,4))


def luminance(c):
    return sum(w * (x/12.92 if x<=0.04045 else ((x+0.055)/1.055)**2.4)
               for w,x in zip((0.2126,0.7152,0.0722),rgb(c)))


def contrast(a,b):
    hi,lo = sorted((luminance(a),luminance(b)),reverse=True)
    return (hi+0.05)/(lo+0.05)


def test_design_palette_accessible():
    src=(PKG/'UiPalette.java').read_text()
    colors=dict(re.findall(r'public static final int (\w+)\s*=\s*0xff([0-9a-fA-F]{6})',src))
    for k in ('BG','TEXT','MUTED','ACCENT','SURFACE'):
        assert k in colors, k
    assert contrast(colors['TEXT'],colors['BG'])>=7
    assert contrast(colors['MUTED'],colors['BG'])>=4.5
    assert contrast(colors['ACCENT'],colors['BG'])>=4.5


def test_minimal_home_and_help_navigation():
    m=(PKG/'MainActivity.java').read_text()
    assert 'private void showSettings()' in m
    assert 'private void showHome()' in m
    assert 'new TechBackdropView(this)' in m
    assert 'new TechOrbitView(this)' in m
    assert 'UiCopy.from(' in m
    assert 'setMinHeight(dp(48))' in m
    assert 'setMinHeight(dp(60))' in m
    assert 'onBackPressed()' in m
    assert '安装连接工具' in m
    assert '备用：Google Play 官方商店' in m
    assert '安装内置 ChatGPT（离线）' in m
    assert '重新检测连接' in m
    assert 'com.github.metacubex.clash.meta.action.START_CLASH' in m
    assert 'ServiceNetworkGate.probe(getApplicationContext(),selected)' in m
    assert m.count('primary = accentButton(')==1
    assert m.index('home.addView(primary, spaced') < m.index('home.addView(statusCard, spaced')
    assert 'YOUR PRIVATE GATEWAY' not in m


def test_orbit_static_and_non_interactive():
    v=(PKG/'TechOrbitView.java').read_text()
    assert 'onDraw(Canvas canvas)' in v
    assert 'setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)' in v
    assert 'postInvalidateDelayed' not in v
    assert 'invalidate()' in v

if __name__ == '__main__':
    tests=[v for k,v in globals().items() if k.startswith('test_') and callable(v)]
    for t in tests:
        t()
        print('PASS:',t.__name__)
    print('PASS: %d UI contracts'%len(tests))
