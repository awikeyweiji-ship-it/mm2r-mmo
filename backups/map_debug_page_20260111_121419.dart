import 'package:flutter/material.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

class MapDebugPage extends StatefulWidget {
  const MapDebugPage({super.key});

  @override
  State<MapDebugPage> createState() => _MapDebugPageState();
}

class _MapDebugPageState extends State<MapDebugPage> {
  // Map settings
  final double mapWidth = 256;
  final double mapHeight = 192;
  final double scale = 3.0; 

  // Player settings
  double playerX = 100;
  double playerY = 100;
  final double playerSize = 10;
  final double moveSpeed = 5;

  bool collisionEnabled = false;

  // WS State
  WebSocketChannel? _channel;
  bool _connected = false;
  String _myClientId = 'Not Connected';
  Map<String, Offset> _otherPlayers = {};

  final List<Rect> obstacles = [
    const Rect.fromLTWH(50, 50, 30, 30),
    const Rect.fromLTWH(150, 100, 40, 20),
  ];

  @override
  void dispose() {
    _channel?.sink.close();
    super.dispose();
  }

  Future<void> _connectWS() async {
    final prefs = await SharedPreferences.getInstance();
    String baseUrl = prefs.getString('api_base_url') ?? 'http://localhost:8080';
    String wsUrl = baseUrl.replaceFirst('http', 'ws');
    
    try {
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      
      _channel!.stream.listen((message) {
        final data = jsonDecode(message);
        if (data['type'] == 'welcome') {
          setState(() {
            _myClientId = data['clientId'];
            _connected = true;
          });
          _channel!.sink.add(jsonEncode({'type': 'join', 'room': 'poc_world'}));
        } else if (data['type'] == 'state') {
          final List players = data['players'];
          final newOthers = <String, Offset>{};
          for (var p in players) {
            if (p['clientId'] != _myClientId) {
              newOthers[p['clientId']] = Offset(p['x'].toDouble(), p['y'].toDouble());
            }
          }
          setState(() {
            _otherPlayers = newOthers;
          });
        }
      }, onDone: () {
        setState(() { _connected = false; _myClientId = 'Disconnected'; });
      }, onError: (e) {
        setState(() { _connected = false; _myClientId = 'Error'; });
      });
    } catch (e) {
      setState(() { _connected = false; _myClientId = 'Error: $e'; });
    }
  }

  void _disconnectWS() {
    _channel?.sink.close();
    setState(() { _connected = false; _myClientId = 'Disconnected'; });
  }

  void _move(double dx, double dy) {
    double newX = playerX + dx;
    double newY = playerY + dy;

    if (collisionEnabled) {
      if (newX < 0) newX = 0;
      if (newY < 0) newY = 0;
      if (newX > mapWidth - playerSize) newX = mapWidth - playerSize;
      if (newY > mapHeight - playerSize) newY = mapHeight - playerSize;

      Rect playerRect = Rect.fromLTWH(newX, newY, playerSize, playerSize);
      bool hit = false;
      for (var obs in obstacles) {
        if (playerRect.overlaps(obs)) {
          hit = true;
          break;
        }
      }
      if (hit) return;
    }

    setState(() {
      playerX = newX;
      playerY = newY;
    });

    if (_connected && _channel != null) {
      _channel!.sink.add(jsonEncode({
        'type': 'move',
        'x': playerX.toInt(),
        'y': playerY.toInt()
      }));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Map Debug (WS)')),
      body: LayoutBuilder(
        builder: (context, constraints) {
          double viewportW = constraints.maxWidth;
          double viewportH = constraints.maxHeight;

          double camCenterX = (playerX + playerSize/2) * scale;
          double camCenterY = (playerY + playerSize/2) * scale;
          
          double offsetX = viewportW / 2 - camCenterX;
          double offsetY = viewportH / 2 - camCenterY;

          return Column(
            children: [
              Expanded(
                child: Container(
                  color: Colors.grey[800],
                  child: ClipRect(
                    child: Stack(
                      children: [
                        Transform.translate(
                          offset: Offset(offsetX, offsetY),
                          child: Transform.scale(
                            scale: scale,
                            alignment: Alignment.topLeft,
                            child: SizedBox(
                              width: mapWidth,
                              height: mapHeight,
                              child: Stack(
                                children: [
                                  Positioned.fill(
                                    child: Image.asset(
                                      'assets/poc/screen.png',
                                      fit: BoxFit.fill,
                                      filterQuality: FilterQuality.none,
                                      errorBuilder: (c,e,s) => Container(color: Colors.blue),
                                    ),
                                  ),
                                  if (collisionEnabled)
                                    ...obstacles.map((r) => Positioned(
                                      left: r.left, top: r.top, width: r.width, height: r.height,
                                      child: Container(color: Colors.red.withOpacity(0.3)),
                                    )),
                                  // Other Players
                                  ..._otherPlayers.entries.map((e) => Positioned(
                                    left: e.value.dx,
                                    top: e.value.dy,
                                    width: playerSize,
                                    height: playerSize,
                                    child: Container(
                                      decoration: BoxDecoration(
                                        color: Colors.blue,
                                        border: Border.all(color: Colors.white, width: 1),
                                      ),
                                      child: Text(
                                        e.key.substring(0, min(4, e.key.length)),
                                        style: const TextStyle(fontSize: 6, color: Colors.white),
                                        overflow: TextOverflow.clip,
                                      ),
                                    ),
                                  )),
                                  // Me
                                  Positioned(
                                    left: playerX,
                                    top: playerY,
                                    width: playerSize,
                                    height: playerSize,
                                    child: Container(
                                      decoration: BoxDecoration(
                                        color: Colors.red,
                                        border: Border.all(color: Colors.white, width: 1),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        // HUD
                        Positioned(
                          top: 10,
                          left: 10,
                          child: Container(
                            color: Colors.black54,
                            padding: const EdgeInsets.all(8),
                            child: Text(
                              'Pos: (${playerX.toStringAsFixed(0)}, ${playerY.toStringAsFixed(0)})\n'
                              'WS: ${_connected ? "ON" : "OFF"}\n'
                              'ID: $_myClientId\n'
                              'Others: ${_otherPlayers.length}',
                              style: const TextStyle(color: Colors.white),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                Container(
                  height: 140,
                  color: Colors.grey[200],
                  padding: const EdgeInsets.all(10),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                       Column(
                         mainAxisAlignment: MainAxisAlignment.center,
                         children: [
                           SizedBox(height: 30, child: ElevatedButton(onPressed: () => _move(0, -moveSpeed), child: const Icon(Icons.arrow_upward))),
                           Row(
                             children: [
                               ElevatedButton(onPressed: () => _move(-moveSpeed, 0), child: const Icon(Icons.arrow_back)),
                               const SizedBox(width: 10),
                               ElevatedButton(onPressed: () => _move(moveSpeed, 0), child: const Icon(Icons.arrow_forward)),
                             ],
                           ),
                           SizedBox(height: 30, child: ElevatedButton(onPressed: () => _move(0, moveSpeed), child: const Icon(Icons.arrow_downward))),
                         ],
                       ),
                       Column(
                         mainAxisAlignment: MainAxisAlignment.center,
                         children: [
                           Switch(value: collisionEnabled, onChanged: (v) => setState(() => collisionEnabled = v)),
                           const Text("Collision"),
                           const SizedBox(height: 10),
                           _connected 
                             ? ElevatedButton(onPressed: _disconnectWS, style: ElevatedButton.styleFrom(backgroundColor: Colors.red), child: const Text("断开WS"))
                             : ElevatedButton(onPressed: _connectWS, style: ElevatedButton.styleFrom(backgroundColor: Colors.green), child: const Text("连接世界WS")),
                         ],
                       )
                    ],
                  ),
                )
              ],
            );
          },
        ),
      ),
    );
  }
  
  int min(int a, int b) => a < b ? a : b;
}
