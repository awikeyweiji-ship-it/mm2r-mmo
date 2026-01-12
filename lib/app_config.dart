import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

class AppConfig extends ChangeNotifier {
  String? baseUrl;
  String healthStatus = 'Unknown';
  String wsStatus = 'Disconnected';
  String lastHealthUrl = '';
  String lastWsUrl = '';
  String browserOrigin = Uri.base.origin;
  WebSocketChannel? ws;
  String? playerId;

  Future<void> discoverBackend() async {
    final Uri currentUri = Uri.base;
    String detectedBaseUrl;

    if (currentUri.host.endsWith('.app.goog')) {
      detectedBaseUrl = 'https://${currentUri.host}:8080';
    } else {
      detectedBaseUrl = 'http://localhost:8080';
    }

    baseUrl = detectedBaseUrl;
    notifyListeners();

    await _checkHealth();
    await _connectWs();
  }

  Future<void> _checkHealth() async {
    if (baseUrl == null) return;
    lastHealthUrl = '$baseUrl/health';
    try {
      final response = await http.get(Uri.parse(lastHealthUrl));
      if (response.statusCode == 200) {
        healthStatus = 'OK';
      } else {
        healthStatus = 'Error: ${response.statusCode}';
      }
    } catch (e) {
      healthStatus = 'Exception: ${e.toString()}';
    }
    notifyListeners();
  }

  Future<void> _connectWs() async {
    if (baseUrl == null) return;
    final wsUrl = baseUrl!.replaceFirst('http', 'ws');
    lastWsUrl = '$wsUrl?roomId=poc_world&name=player-${DateTime.now().millisecondsSinceEpoch % 1000}';

    try {
      ws = WebSocketChannel.connect(Uri.parse(lastWsUrl));
      wsStatus = 'Connecting';
      notifyListeners();

      ws!.stream.listen(
        (message) {
          if (wsStatus != 'Connected') {
            wsStatus = 'Connected';
            final data = jsonDecode(message);
            if (data['type'] == 'welcome') {
                playerId = data['playerId'];
            }
            notifyListeners();
          }
        },
        onDone: () {
          wsStatus = 'Disconnected';
          notifyListeners();
        },
        onError: (error) {
          wsStatus = 'Error: $error';
          notifyListeners();
        },
      );
    } catch (e) {
      wsStatus = 'Exception: ${e.toString()}';
      notifyListeners();
    }
  }

  void sendWsMessage(String message) {
      ws?.sink.add(message);
  }

  @override
  void dispose() {
    ws?.sink.close();
    super.dispose();
  }
}
