import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

class AppConfig extends ChangeNotifier {
  String? apiBaseUrl; // For HTTP/Health (Proxy)
  String? wsBaseUrl;  // For WebSocket (Proxy)
  
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

    // In all environments (web, mobile, desktop), we now assume a proxy is running.
    // The proxy standardizes the API and WebSocket endpoints.
    apiBaseUrl = '${currentUri.origin}/api';

    // For WebSockets, derive the URL from the page's origin.
    // The proxy will forward wss://<origin>/ws to ws://127.0.0.1:8080/ws.
    if (kIsWeb) {
        Uri wsUri = currentUri.replace(
            scheme: currentUri.scheme == 'https' ? 'wss' : 'ws',
            path: '/ws',
        );
        wsBaseUrl = wsUri.toString();
    } else {
        // For non-web (e.g., Android/iOS testing), assume direct connection for simplicity.
        // This could be unified later if a proxy is also used for mobile development.
        wsBaseUrl = 'ws://127.0.0.1:8080/ws'; 
        apiBaseUrl = 'http://127.0.0.1:8080'; // and api points directly
    }

    notifyListeners();

    await _checkHealth();
    await _connectWs();
  }

  Future<void> _checkHealth() async {
    if (apiBaseUrl == null) return;
    lastHealthUrl = '$apiBaseUrl/health';
    healthStatus = 'Checking...';
    notifyListeners();
    
    try {
      final response = await http.get(Uri.parse(lastHealthUrl));
      if (response.statusCode == 200) {
        healthStatus = 'OK';
      } else {
        healthStatus = 'Fail: ${response.statusCode}';
      }
    } catch (e) {
      healthStatus = 'Err: ${e.toString()}';
      print("Health check failed for $lastHealthUrl: $e");
    }
    notifyListeners();
  }

  Future<void> _connectWs() async {
    if (wsBaseUrl == null) return;
    
    // Use a unique name for each connection to test multi-client
    lastWsUrl = '$wsBaseUrl?roomId=poc_world&name=player-${DateTime.now().microsecondsSinceEpoch % 10000}';

    try {
      if (ws != null) {
          ws!.sink.close();
      }
      print("Connecting to WS (Same-Origin): $lastWsUrl");
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
          playerCount = 0;
          notifyListeners();
        },
        onError: (error) {
          wsStatus = 'Error';
          lastWsError = error.toString();
          playerCount = 0;
          notifyListeners();
        },
      );
    } catch (e) {
      wsStatus = 'Exception';
      lastWsError = e.toString();
      playerCount = 0;
      notifyListeners();
    }
  }

  void sendWsMessage(String message) {
      ws?.sink.add(message);
  }
  
  void retryConnection() {
      discoverBackend(); 
  }

  @override
  void dispose() {
    _gameStateController.close();
    ws?.sink.close();
    super.dispose();
  }
}
