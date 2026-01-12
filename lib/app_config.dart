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
  String lastWsError = '';
  String browserOrigin = Uri.base.origin;
  
  WebSocketChannel? ws;
  String? playerId;
  int playerCount = 0;
  
  final _gameStateController = StreamController<Map<String, dynamic>>.broadcast();
  Stream<Map<String, dynamic>> get gameStateStream => _gameStateController.stream;

  Future<void> discoverBackend() async {
    final Uri currentUri = Uri.base;
    String detectedBaseUrl;

    // Detect base URL from browser location
    if (currentUri.host.endsWith('.app.goog') || currentUri.host.endsWith('.cloudworkstations.dev')) {
      detectedBaseUrl = '${currentUri.scheme}://${currentUri.host}';
      if (currentUri.hasPort && currentUri.port != 80 && currentUri.port != 443) {
         detectedBaseUrl += ':${currentUri.port}';
      }
    } else {
      detectedBaseUrl = 'http://localhost:8080';
    }
    
    // Allow overriding via query param
    if (currentUri.queryParameters.containsKey('backend')) {
        detectedBaseUrl = currentUri.queryParameters['backend']!;
    }

    baseUrl = detectedBaseUrl;
    notifyListeners();

    await _checkHealth();
    await _connectWs();
  }

  String deriveWsUrl(String httpBaseUrl) {
      final Uri currentUri = Uri.base;
      String wsScheme = 'ws';
      if (currentUri.scheme == 'https' || httpBaseUrl.startsWith('https')) {
          wsScheme = 'wss';
      }

      // Parse the httpBaseUrl to get host/port
      Uri parsedHttp = Uri.parse(httpBaseUrl);
      
      // Construct WS URL
      // Strict requirement: path must be /ws
      Uri wsUri = parsedHttp.replace(
          scheme: wsScheme,
          path: '/ws'
      );
      
      return wsUri.toString();
  }

  Future<void> _checkHealth() async {
    if (baseUrl == null) return;
    lastHealthUrl = '$baseUrl/health';
    try {
      final response = await http.get(Uri.parse(lastHealthUrl));
      if (response.statusCode == 200) {
        healthStatus = 'OK';
      } else {
        healthStatus = 'Fail: ${response.statusCode}';
      }
    } catch (e) {
      healthStatus = 'Err: ${e.toString()}';
    }
    notifyListeners();
  }

  Future<void> _connectWs() async {
    if (baseUrl == null) return;
    
    String wsBase = deriveWsUrl(baseUrl!);
    lastWsUrl = '$wsBase?roomId=poc_world&name=player-${DateTime.now().millisecondsSinceEpoch % 1000}';

    try {
      if (ws != null) {
          ws!.sink.close();
      }
      print("Connecting to WS: $lastWsUrl");
      ws = WebSocketChannel.connect(Uri.parse(lastWsUrl));
      wsStatus = 'Connecting';
      lastWsError = '';
      notifyListeners();

      ws!.stream.listen(
        (message) {
            try {
                if (wsStatus != 'Connected') {
                    wsStatus = 'Connected';
                    lastWsError = '';
                    notifyListeners();
                }
                
                final data = jsonDecode(message);
                
                if (data['type'] == 'welcome') {
                    playerId = data['playerId'];
                    notifyListeners();
                } else if (data['type'] == 'snapshot') {
                    final players = data['players'] as Map<String, dynamic>;
                    playerCount = players.length;
                    _gameStateController.add(data);
                    notifyListeners();
                } else if (data['type'] == 'state' || data['type'] == 'delta') {
                    _gameStateController.add(data);
                }
            } catch (e) {
                print('Error parsing WS message: $e');
            }
        },
        onDone: () {
          wsStatus = 'Disconnected';
          if (lastWsError.isEmpty) lastWsError = 'Closed by server';
          notifyListeners();
        },
        onError: (error) {
          wsStatus = 'Error';
          lastWsError = error.toString();
          notifyListeners();
        },
      );
    } catch (e) {
      wsStatus = 'Exception';
      lastWsError = e.toString();
      notifyListeners();
    }
  }

  void sendWsMessage(String message) {
      ws?.sink.add(message);
  }
  
  void retryConnection() {
      _connectWs();
  }

  @override
  void dispose() {
    _gameStateController.close();
    ws?.sink.close();
    super.dispose();
  }
}
