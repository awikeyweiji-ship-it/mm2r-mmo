import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static const String _kBaseUrlKey = 'api_base_url';

  // Default placeholder (prompt user to change)
  static const String _defaultBaseUrl = 'http://localhost:8080';

  // Environment variable override (compile time)
  static const String _envBaseUrl = String.fromEnvironment('BASE_URL');

  static Future<String> getHttpBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    String? stored = prefs.getString(_kBaseUrlKey);

    if (stored != null && stored.isNotEmpty) return stored;
    if (_envBaseUrl.isNotEmpty) return _envBaseUrl;

    return _defaultBaseUrl;
  }

  static Future<void> setHttpBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    // Remove trailing slash
    if (url.endsWith('/')) url = url.substring(0, url.length - 1);
    await prefs.setString(_kBaseUrlKey, url);
  }

  static String deriveWsUrl(String httpUrl) {
    String wsUrl = httpUrl;
    if (wsUrl.startsWith('https://')) {
      wsUrl = wsUrl.replaceFirst('https://', 'wss://');
    } else if (wsUrl.startsWith('http://')) {
      wsUrl = wsUrl.replaceFirst('http://', 'ws://');
    }
    // Append /ws path
    return '$wsUrl/ws';
  }
}
