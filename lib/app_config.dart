import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

class AppConfig extends ChangeNotifier {
  String? apiBaseUrl; // For HTTP/Health (Proxy)
  String? wsBaseUrl;  // For WebSocket (Direct 8080)
  
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

  // Robust derivation logic for backend URL (Direct 8080)
  String? deriveBackendBaseUrlFromWebOrigin(Uri origin) {
    if (!origin.host.contains('cloudworkstations.dev') && 
        !origin.host.contains('app.goog') && 
        !origin.host.endsWith('gitpod.io')) {
      return null; // Not in a recognized preview environment
    }
    
    String derivedHost = origin.host;
    
    // Case 1: Prefix based (e.g. 9000-abc... -> 8080-abc...)
    final prefixRegex = RegExp(r'^(\d+)-(.*)$');
    final prefixMatch = prefixRegex.firstMatch(derivedHost);
    
    if (prefixMatch != null) {
      derivedHost = '8080-${prefixMatch.group(2)}';
    } 
    // Case 2: Infix based (e.g. abc-9000.... -> abc-8080....)
    else if (derivedHost.contains('-9000.')) {
        derivedHost = derivedHost.replaceFirst('-9000.', '-8080.');
    }
    // Case 3: Already correct port?
    else if (derivedHost.startsWith('8080-') || derivedHost.contains('-8080.')) {
        // Keep it as is
    }
    // Case 4: Fallback, try to replace any 9000 with 8080 if present
    else if (derivedHost.contains('9000')) {
         derivedHost = derivedHost.replaceAll('9000', '8080');
    }
    
    return '${origin.scheme}://$derivedHost';
  }

  String deriveWsUrl(String httpBaseUrl) {
      String wsScheme = 'ws';
      if (httpBaseUrl.startsWith('https')) {
          wsScheme = 'wss';
      }

      Uri parsedHttp = Uri.parse(httpBaseUrl);
      
      // Reconstruct with correct scheme and path
      Uri wsUri = parsedHttp.replace(
          scheme: wsScheme,
          path: '/ws'
      );
      
      return wsUri.toString();
  }

  Future<void> discoverBackend() async {
    final Uri currentUri = Uri.base;
    String? directBackendUrl = deriveBackendBaseUrlFromWebOrigin(currentUri);

    if (directBackendUrl != null) {
        // Preview Environment:
        // API -> Proxy (Same Origin + /api)
        apiBaseUrl = '${currentUri.origin}/api';
        // WS -> Direct Backend (8080)
        wsBaseUrl = deriveWsUrl(directBackendUrl);
    } else {
        // Localhost / Fallback
        if (currentUri.host == 'localhost' || currentUri.host == '127.0.0.1') {
            // Assume Proxy on current port, Backend on 8080
            apiBaseUrl = '${currentUri.origin}/api';
            wsBaseUrl = 'ws://${currentUri.host}:8080/ws';
        } else {
            // Unknown, try direct fallback
            apiBaseUrl = '${currentUri.origin}/api';
            wsBaseUrl = deriveWsUrl('${currentUri.scheme}://${currentUri.host}:${currentUri.port}');
        }
    }

    notifyListeners();

    // 4. Verify connectivity
    await _checkHealth();
    
    // 5. Connect WS
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
    
    lastWsUrl = '$wsBaseUrl?roomId=poc_world&name=player-${DateTime.now().millisecondsSinceEpoch % 1000}';

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
      discoverBackend(); 
  }

  @override
  void dispose() {
    _gameStateController.close();
    ws?.sink.close();
    super.dispose();
  }
}
