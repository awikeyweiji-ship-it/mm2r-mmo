import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:developer' as developer;
import 'dart:math' as math;

class AppConfig {
  static const String kHttpBaseUrlKey = 'http_base_url';
  static const String kRoomIdKey = 'room_id';
  static const String kPlayerNameKey = 'player_name';
  static const String _previewUrlEnv = String.fromEnvironment('PREVIEW_URL');

  static Future<String> getHttpBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(kHttpBaseUrlKey) ?? 'http://localhost:8080';
  }

  static Future<void> setHttpBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kHttpBaseUrlKey, url);
  }

  static Future<String> getRoomId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(kRoomIdKey) ?? 'poc_world';
  }

  static Future<void> setRoomId(String roomId) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kRoomIdKey, roomId);
  }

  static Future<String> getPlayerName() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(kPlayerNameKey) ??
        'Guest_${math.Random().nextInt(999)}';
  }

  static Future<void> setPlayerName(String name) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPlayerNameKey, name);
  }

  static Future<bool> discoverAndSetBaseUrl() async {
    if (_previewUrlEnv.isEmpty) {
      developer.log(
        'PREVIEW_URL is not set. Cannot discover backend.',
        name: 'AppConfig.Discovery',
      );
      return false;
    }

    final uri = Uri.parse(_previewUrlEnv);
    // On IDX, the backend is on port 8080 of the same host.
    final candidateUrl = uri.replace(port: 8080).toString();

    developer.log(
      'Probing candidate URL: $candidateUrl',
      name: 'AppConfig.Discovery',
    );

    try {
      final healthUri = Uri.parse('$candidateUrl/health');
      final response = await http
          .get(healthUri)
          .timeout(const Duration(seconds: 3));

      if (response.statusCode == 200) {
        developer.log(
          'Discovery successful! Persisting Base URL: $candidateUrl',
          name: 'AppConfig.Discovery',
        );
        await setHttpBaseUrl(candidateUrl);
        return true;
      }
    } catch (e) {
      developer.log(
        'Probe failed for $candidateUrl: $e',
        name: 'AppConfig.Discovery',
      );
    }
    return false;
  }

  static Future<String> deriveWsUrl() async {
    final httpUrl = await getHttpBaseUrl();
    final roomId = await getRoomId();
    final playerName = await getPlayerName();
    // Assign a random color for each session
    final playerColor = math.Random()
        .nextInt(0xFFFFFF)
        .toRadixString(16)
        .padLeft(6, '0');

    if (httpUrl.isEmpty) return '';
    final Uri uri = Uri.parse(httpUrl);
    final bool isSecure = uri.scheme == 'https';

    final queryParams = {
      'roomId': roomId,
      'playerName': playerName,
      'playerColor': playerColor,
    };

    final wsUri = Uri(
      scheme: isSecure ? 'wss' : 'ws',
      host: uri.host,
      port: uri.port,
      path: '/ws',
      queryParameters: queryParams,
    );

    return wsUri.toString();
  }
}
