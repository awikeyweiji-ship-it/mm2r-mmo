
import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:web_socket_channel/web_socket_channel.dart';

class AppConfig extends ChangeNotifier {
  String? apiBaseUrl;
  String? wsBaseUrl;

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

  Timer? _healthCheckTimer;
  Timer? _reconnectTimer;
  int _reconnectAttempts = 0;

  Future<void> discoverBackend() async {
    final Uri currentUri = Uri.base;

    apiBaseUrl = '${currentUri.origin}/api';

    if (kIsWeb) {
      Uri wsUri = currentUri.replace(
        scheme: currentUri.scheme == 'https' ? 'wss' : 'ws',
        path: '/ws',
      );
      wsBaseUrl = wsUri.toString();
    } else {
      wsBaseUrl = 'ws://127.0.0.1:8080/ws';
      apiBaseUrl = 'http://127.0.0.1:8080';
    }

    notifyListeners();
    _startHealthChecks();
    _connectWs();
  }

  void _startHealthChecks() {
    _healthCheckTimer?.cancel();
    _healthCheckTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      _checkHealth();
    });
  }

  Future<void> _checkHealth() async {
    if (apiBaseUrl == null) return;
    lastHealthUrl = '$apiBaseUrl/health';
    
    // Don't spam "Checking..."
    if(healthStatus != 'OK' && healthStatus != 'Checking...'){
        healthStatus = 'Checking...';
        notifyListeners();
    }

    try {
      final response = await http.get(Uri.parse(lastHealthUrl));
      if (response.statusCode == 200) {
        if(healthStatus != 'OK'){
          healthStatus = 'OK';
          notifyListeners();
        }
      } else {
        if(healthStatus != 'Fail: ${response.statusCode}'){
           healthStatus = 'Fail: ${response.statusCode}';
           notifyListeners();
        }
      }
    } catch (e) {
      final errorMsg = 'Err: ${e.toString()}';
      if(healthStatus != errorMsg){
         healthStatus = errorMsg;
         notifyListeners();
      }
    }
  }

  void _scheduleReconnect() {
    _reconnectTimer?.cancel();
    if (_reconnectAttempts >= 5) { // Stop after 5 attempts
        wsStatus = "Reconnect failed";
        notifyListeners();
        return;
    }

    final waitSeconds = min(pow(2, _reconnectAttempts), 2);
    _reconnectTimer = Timer(Duration(seconds: waitSeconds.toInt()), () {
      _reconnectAttempts++;
      wsStatus = "Reconnecting...";
      notifyListeners();
      _connectWs();
    });
  }

  Future<void> _connectWs() async {
    if (wsBaseUrl == null) return;

    lastWsUrl = '$wsBaseUrl?roomId=poc_world&name=player-${DateTime.now().microsecondsSinceEpoch % 10000}';

    try {
      ws?.sink.close();
      
      print("Connecting to WS: $lastWsUrl");
      ws = WebSocketChannel.connect(Uri.parse(lastWsUrl));
      wsStatus = 'Connecting';
      lastWsError = '';
      notifyListeners();

      ws!.stream.listen(
        (message) {
          _reconnectAttempts = 0; // Reset on successful connection
          if (wsStatus != 'Connected') {
            wsStatus = 'Connected';
            lastWsError = '';
            notifyListeners();
          }

          final data = jsonDecode(message);
          if (data['type'] == 'welcome') {
            playerId = data['playerId'];
          } else if (data['type'] == 'snapshot') {
            playerCount = (data['players'] as Map<String, dynamic>).length;
            _gameStateController.add(data);
          } else if (data['type'] == 'state' || data['type'] == 'delta') {
            _gameStateController.add(data);
          }
          notifyListeners();
        },
        onDone: () {
          wsStatus = 'Disconnected';
          if (lastWsError.isEmpty) lastWsError = 'Closed by server';
          playerCount = 0;
          notifyListeners();
          _scheduleReconnect();
        },
        onError: (error) {
          wsStatus = 'Error';
          lastWsError = error.toString();
          playerCount = 0;
          notifyListeners();
          _scheduleReconnect();
        },
      );
    } catch (e) {
      wsStatus = 'Exception';
      lastWsError = e.toString();
      playerCount = 0;
      notifyListeners();
      _scheduleReconnect();
    }
  }

  void sendWsMessage(String message) {
    if (wsStatus == 'Connected') {
      ws?.sink.add(message);
    }
  }

  void retryConnection() {
    _reconnectAttempts = 0;
    discoverBackend();
  }

  @override
  void dispose() {
    _healthCheckTimer?.cancel();
    _reconnectTimer?.cancel();
    _gameStateController.close();
    ws?.sink.close();
    super.dispose();
  }
}
