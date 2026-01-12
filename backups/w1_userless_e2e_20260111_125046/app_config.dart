'''import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:developer' as developer;

class AppConfig {
  static const String kHttpBaseUrlKey = 'http_base_url';
  static const String _previewUrlEnv = String.fromEnvironment('PREVIEW_URL');

  static Future<String> getHttpBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(kHttpBaseUrlKey) ?? 'http://localhost:8080';
  }

  static Future<void> setHttpBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kHttpBaseUrlKey, url);
  }

  static Future<bool> discoverAndSetBaseUrl() async {
    if (_previewUrlEnv.isEmpty) {
       developer.log('PREVIEW_URL is not set.', name: 'AppConfig.Discovery');
       return false;
    }

    // Example PREVIEW_URL: https://3000-idx-....
    // We want to test the 8080 port for the backend.
    final uri = Uri.parse(_previewUrlEnv);
    final candidateUrl = uri.replace(port: 8080).toString();
    
    developer.log('Testing candidate URL: $candidateUrl', name: 'AppConfig.Discovery');

    try {
      final healthUri = Uri.parse(candidateUrl.endsWith('/') ? '${candidateUrl}health' : '$candidateUrl/health');
      final response = await http.get(healthUri).timeout(const Duration(seconds: 3));
      
      if (response.statusCode == 200) {
        developer.log('Discovery successful! Setting Base URL to: $candidateUrl', name: 'AppConfig.Discovery');
        await setHttpBaseUrl(candidateUrl);
        return true;
      }
    } catch (e) {
      developer.log('Discovery failed for $candidateUrl: $e', name: 'AppConfig.Discovery');
    }
    return false;
  }

  static String deriveWsUrl(String httpUrl) {
    if (httpUrl.isEmpty) return '';
    final Uri uri = Uri.parse(httpUrl);
    final bool isSecure = uri.scheme == 'https';
    return '${isSecure ? 'wss' : 'ws'}://${uri.host}:${uri.port}/ws';
  }
}
''