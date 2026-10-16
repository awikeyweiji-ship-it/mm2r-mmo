"""Offline syntax-check stubs ONLY, not a real Android SDK. Do NOT distribute as android.jar."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'.test-stubs'
stubs={
'android.content.Context': '''public class Context {
 public static final int CONNECTIVITY_SERVICE=0,MODE_PRIVATE=0;
 public Object getSystemService(int x){return null;} public android.content.pm.PackageManager getPackageManager(){return null;}
 public android.content.res.Resources getResources(){return null;} public android.content.res.AssetManager getAssets(){return null;}
 public Context getApplicationContext(){return this;} public String getPackageName(){return "";}
 public android.content.SharedPreferences getSharedPreferences(String x,int y){return null;}
 public void startActivity(Intent i){} }
''',
'android.app.Activity': '''public class Activity extends android.content.Context {
 protected void onCreate(android.os.Bundle b){} protected void onResume(){} protected void onDestroy(){}
 public android.view.Window getWindow(){return null;} public void setContentView(android.view.View v){}
 public void runOnUiThread(Runnable r){} }''',
'android.app.PendingIntent': '''public class PendingIntent {
 public static final int FLAG_UPDATE_CURRENT=134217728, FLAG_MUTABLE=33554432;
 public static PendingIntent getBroadcast(android.content.Context c,int n,android.content.Intent i,int f){return null;}
 public android.content.IntentSender getIntentSender(){return null;} }''',
'android.content.IntentSender':'''public class IntentSender {}''',
'android.content.BroadcastReceiver': '''public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}''',
'android.content.Intent': '''public class Intent {
 public static final String ACTION_VIEW="v", EXTRA_INTENT="i";
 public static final int FLAG_ACTIVITY_NEW_TASK=268435456;
 public Intent(){} public Intent(String a){} public Intent(String a,android.net.Uri u){}
 public Intent(Context c,Class<?> cls){}
 public Intent setAction(String a){return this;} public Intent setClassName(String p,String c){return this;}
 public Intent addFlags(int x){return this;} public Intent putExtra(String k,String v){return this;}
 public String getAction(){return null;} public int getIntExtra(String s,int d){return d;}
 public String getStringExtra(String x){return null;}
 public <T> T getParcelableExtra(String s,Class<T> c){return null;}
 public <T> T getParcelableExtra(String s){return null;} }''',
'android.content.ActivityNotFoundException':'''public class ActivityNotFoundException extends RuntimeException {}''',
'android.content.SharedPreferences':'''public interface SharedPreferences {
 String getString(String key,String def); Editor edit();
 interface Editor {Editor putString(String s,String v); void apply();} }''',
'android.content.pm.PackageManager': '''public class PackageManager {
 public android.content.Intent getLaunchIntentForPackage(String p){return null;}
 public PackageInstaller getPackageInstaller(){return null;} public boolean canRequestPackageInstalls(){return false;} }''',
'android.content.pm.PackageInstaller':'''public class PackageInstaller {
 public static final String EXTRA_STATUS="",EXTRA_STATUS_MESSAGE="";
 public static final int STATUS_FAILURE=-1,STATUS_PENDING_USER_ACTION=-2,STATUS_SUCCESS=0;
 public int createSession(SessionParams p){return 0;} public Session openSession(int id){return null;}
 public static class SessionParams {public static final int MODE_FULL_INSTALL=1;
 public SessionParams(int m){} public void setAppPackageName(String n){} }
 public static class Session {public java.io.OutputStream openWrite(String n,long offset,long size){return null;}
 public void fsync(java.io.OutputStream o){} public void commit(android.content.IntentSender s){}
 public void abandon(){} public void close(){} } }''',
'android.content.res.Resources':'''public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return null;}}''',
'android.content.res.AssetManager':'''public class AssetManager {public java.io.InputStream open(String s) throws java.io.IOException {return null;}}''',
'android.content.res.ColorStateList':'''public class ColorStateList {public static ColorStateList valueOf(int v){return null;}}''',
'android.util.DisplayMetrics':'''public class DisplayMetrics {public float density=1;}''',
'android.graphics.Color':'''public class Color {public static int rgb(int r,int g,int b){return 0;} }''',
'android.graphics.Typeface':'''public class Typeface {public static final Typeface DEFAULT=null; public static final int BOLD=1;}''',
'android.graphics.Insets':'''public class Insets {public int left,right,top,bottom;}''',
'android.graphics.drawable.GradientDrawable':'''public class GradientDrawable {
 public enum Orientation{TL_BR} public GradientDrawable(){}public GradientDrawable(Orientation x,int[] a){}
 public void setColor(int x){} public void setCornerRadius(float x){} public void setStroke(int w,int c){} }''',
'android.net.Uri':'''public class Uri {public static Uri parse(String s){return null;}}''',
'android.net.ConnectivityManager':'''public class ConnectivityManager {public android.net.Network[] getAllNetworks(){return null;}
 public android.net.NetworkCapabilities getNetworkCapabilities(android.net.Network n){return null;} }''',
'android.net.Network':'''public class Network {public java.net.URLConnection openConnection(java.net.URL url) throws java.io.IOException {return null;}}''',
'android.net.NetworkCapabilities':'''public class NetworkCapabilities {
 public static final int TRANSPORT_VPN=4;public boolean hasTransport(int n){return false;} }''',
'android.os.Build':'''public class Build {public static final String[] SUPPORTED_ABIS=new String[0];
 public static class VERSION {public static final int SDK_INT=35;} }''',
'android.os.Bundle':'''public class Bundle {}''',
'android.os.Looper':'''public class Looper {public static Looper getMainLooper(){return null;}}''',
'android.os.Handler':'''public class Handler {public Handler(Looper l){} public boolean post(Runnable r){return true;}
 public boolean postDelayed(Runnable r,long d){return true;} }''',
'android.provider.Settings':'''public class Settings {public static final String ACTION_MANAGE_UNKNOWN_APP_SOURCES="";}''',
'android.view.Gravity':'''public class Gravity {public static final int CENTER_HORIZONTAL=1;}''',
'android.view.Window':'''public class Window {public void setStatusBarColor(int c){}public void setNavigationBarColor(int c){} }''',
'android.view.WindowInsets':'''public class WindowInsets {public static class Type {public static int systemBars(){return 0;}}
 public android.graphics.Insets getInsets(int type){return null;} }''',
'android.view.View':'''public class View {
 public interface OnClickListener{void onClick(View v);} public interface OnApplyWindowInsetsListener{WindowInsets onApplyWindowInsets(View v,WindowInsets i);}
 public void setBackground(Object d){} public void setPadding(int l,int t,int r,int b){}
 public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l){} public void setClipToPadding(boolean b){}
 public void setStateListAnimator(Object x){} }''',
'android.widget.FrameLayout':'''public class FrameLayout extends android.view.View {
 public FrameLayout(android.content.Context c){} public void addView(android.view.View v,LayoutParams p){}
 public static class LayoutParams{public LayoutParams(int w,int h){} } }''',
'android.widget.LinearLayout':'''public class LinearLayout extends android.view.View {
 public static final int VERTICAL=1; public LinearLayout(android.content.Context c){}
 public void setOrientation(int i){} public void addView(android.view.View v){} public void addView(android.view.View v,LayoutParams p){}
 public static class LayoutParams{public int topMargin,bottomMargin; public LayoutParams(int w,int h){} } }''',
'android.widget.ScrollView':'''public class ScrollView extends android.widget.FrameLayout {
 public ScrollView(android.content.Context c){super(c);} public void setFillViewport(boolean x){}
 public static class LayoutParams extends FrameLayout.LayoutParams {public LayoutParams(int w,int h){super(w,h);}} }''',
'android.widget.TextView':'''public class TextView extends android.view.View {
 public TextView(android.content.Context c){} public void setText(CharSequence s){} public void setTextSize(float s){}
 public void setTextColor(int c){} public void setTypeface(android.graphics.Typeface t,int s){}
 public void setLineSpacing(float a,float m){} public void setLetterSpacing(float l){}
 public void setGravity(int i){} }''',
'android.widget.Button':'''public class Button extends android.widget.TextView {
 public Button(android.content.Context c){super(c);} public void setAllCaps(boolean b){}
 public void setMinHeight(int h){} public void setBackgroundTintList(android.content.res.ColorStateList c){}
 public void setOnClickListener(android.view.View.OnClickListener l){} public void setEnabled(boolean b){} }'''
}
for fullname, body in stubs.items():
 pack,name=fullname.rsplit('.',1)
 dest=root/('/'.join(pack.split('.')))
 dest.mkdir(parents=True,exist_ok=True)
 (dest/(name+'.java')).write_text(f'package {pack};\n'+body+'\n')
print('wrote',len(stubs),'Android syntax-only API stubs')
