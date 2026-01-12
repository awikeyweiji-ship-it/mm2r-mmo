import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:flutter/scheduler.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'dart:async';
import 'dart:ui';
import 'pages/map_debug_page.dart';
import 'app_config.dart';

void main() {
  runApp(const MyApp());
}

// ... (MyApp and HomePage remain the same)
class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MM2R MMO',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('MM2R MMO - S1'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const WorldPage()),
                );
              },
              child: const Text('进入世界'),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const SettingsPage()),
                );
              },
              child: const Text('设置'),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const MapDebugPage()),
                );
              },
              child: const Text('地图调试 MapDebug'),
            ),
          ],
        ),
      ),
    );
  }
}

// ... (Player class remains the same)
class Player {
  String id;
  String name;
  Color color;
  Offset currentPos;
  Offset targetPos;
  int lastUpdate;

  Player({
    required this.id,
    required this.name,
    required this.color,
    required this.currentPos,
    required this.targetPos,
    required this.lastUpdate,
  });

  static Player fromJson(dynamic data) {
    final colorStr = (data['color'] as String).replaceAll('#', '');
    return Player(
      id: data['id'],
      name: data['name'],
      color: Color(int.parse('ff$colorStr', radix: 16)),
      currentPos: Offset(data['x'].toDouble(), data['y'].toDouble()),
      targetPos: Offset(data['x'].toDouble(), data['y'].toDouble()),
      lastUpdate: data['ts'],
    );
  }
}

class WorldPage extends StatefulWidget {
  const WorldPage({super.key});

  @override
  State<WorldPage> createState() => _WorldPageState();
}

class _WorldPageState extends State<WorldPage>
    with SingleTickerProviderStateMixin {
  String _baseUrl = "";
  String _roomId = "";
  String _playerId = "";
  WebSocketChannel? _channel;
  Ticker? _ticker;

  Map<String, Player> _players = {};
  Player? _localPlayer;

  String _healthStatus = 'Pending';
  String _wsStatus = 'Pending';
  Color _healthColor = Colors.orange;
  Color _wsColor = Colors.orange;

  @override
  void initState() {
    super.initState();
    _runSelfTest(); // Autorun on enter
    _ticker = createTicker((_) {
      if (mounted) {
        setState(() {
          _updatePlayerPositions();
        });
      }
    });
    _ticker?.start();
  }

  Future<void> _runSelfTest() async {
    // Reset status
    setState(() {
      _healthStatus = 'Running...';
      _wsStatus = 'Waiting...';
      _healthColor = Colors.orange;
      _wsColor = Colors.orange;
      _players.clear();
      _localPlayer = null;
      _playerId = '';
      _channel?.sink.close();
    });

    // 1. Ensure valid Base URL
    String currentUrl = await AppConfig.getHttpBaseUrl();
    if (currentUrl.contains("localhost") || currentUrl.contains("127.0.0.1")) {
      final discovered = await AppConfig.discoverAndSetBaseUrl();
      if (discovered) {
        currentUrl = await AppConfig.getHttpBaseUrl();
      }
    }
    if (!mounted) return;
    setState(() {
      _baseUrl = currentUrl;
    });

    if (_baseUrl.contains("localhost")) {
      setState(() {
        _healthStatus = 'FAIL: Use a non-localhost URL.';
        _healthColor = Colors.red;
      });
      return;
    }

    // 2. Health Check
    try {
      final healthUri = Uri.parse('$currentUrl/health');
      final response = await http
          .get(healthUri)
          .timeout(const Duration(seconds: 4));
      if (response.statusCode == 200) {
        if (mounted)
          setState(() {
            _healthStatus = '✅ OK';
            _healthColor = Colors.green;
          });
      } else {
        if (mounted)
          setState(() {
            _healthStatus = '⚠️ FAIL (${response.statusCode})';
            _healthColor = Colors.red;
          });
        return; // Stop if health check fails
      }
    } catch (e) {
      if (mounted)
        setState(() {
          _healthStatus = '⚠️ FAIL (No connection)';
          _healthColor = Colors.red;
        });
      return; // Stop if health check fails
    }

    // 3. WebSocket Connection
    if (mounted)
      setState(() {
        _wsStatus = 'Connecting...';
      });
    _roomId = await AppConfig.getRoomId();
    final wsUrl = await AppConfig.deriveWsUrl();

    try {
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      if (mounted)
        setState(() {
          _wsStatus = 'Connected, waiting for welcome...';
        });

      _channel?.stream.listen(
        (message) {
          if (!mounted) return;
          final data = jsonDecode(message);

          setState(() {
            if (data['type'] == 'welcome') {
              _playerId = data['playerId'];
              _updateRoomState(data['roomState']);
              _localPlayer = _players[_playerId];
              _wsStatus = "✅ Welcome received!";
              _wsColor = Colors.green;
            } else if (data['type'] == 'state') {
              _updateRoomState(data['players']);
            }
          });
        },
        onError: (error) {
          if (mounted)
            setState(() {
              _wsStatus = '⚠️ Error: $error';
              _wsColor = Colors.red;
            });
        },
        onDone: () {
          if (mounted)
            setState(() {
              _wsStatus = '⚠️ Disconnected';
              _wsColor = Colors.red;
            });
        },
      );
    } catch (e) {
      if (mounted)
        setState(() {
          _wsStatus = '⚠️ FAIL (Cannot connect)';
          _wsColor = Colors.red;
        });
    }
  }

  void _updatePlayerPositions() {
    final now = DateTime.now().millisecondsSinceEpoch;
    _players.forEach((id, player) {
      if (id != _playerId) {
        // Interpolate remote players
        final timeDiff = now - player.lastUpdate;
        final t = (timeDiff / (1000 / 15)).clamp(
          0.0,
          1.0,
        ); // Assuming 15Hz tick rate
        player.currentPos = Offset.lerp(
          player.currentPos,
          player.targetPos,
          t,
        )!;
      }
    });
  }

  void _updateRoomState(Map<String, dynamic> roomState) {
    final receivedPlayers = roomState.map(
      (id, data) => MapEntry(id, Player.fromJson(data)),
    );
    receivedPlayers.forEach((id, newPlayer) {
      if (_players.containsKey(id)) {
        final oldPlayer = _players[id]!;
        if (id != _playerId) {
          oldPlayer.targetPos = newPlayer.targetPos;
          oldPlayer.lastUpdate = newPlayer.lastUpdate;
        }
      } else {
        _players[id] = newPlayer;
      }
    });
    _players.removeWhere((id, player) => !receivedPlayers.containsKey(id));
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (_channel != null && _localPlayer != null) {
      final newPos = event.localPosition;
      _localPlayer!.currentPos = newPos;
      _localPlayer!.targetPos = newPos;

      _channel?.sink.add(
        jsonEncode({'type': 'move', 'x': newPos.dx, 'y': newPos.dy}),
      );
      if (mounted) setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('World')),
      body: Column(
        children: [
          // Self-Test Panel
          Container(
            padding: const EdgeInsets.all(8.0),
            width: double.infinity,
            color: Colors.black87,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'BaseURL: $_baseUrl',
                  style: const TextStyle(
                    fontSize: 11,
                    color: Colors.white70,
                    fontFamily: 'monospace',
                  ),
                ),
                Row(
                  children: [
                    Text(
                      'Health Check: ',
                      style: TextStyle(
                        color: _healthColor,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(_healthStatus, style: TextStyle(color: _healthColor)),
                    const Spacer(),
                    Text(
                      'WebSocket: ',
                      style: TextStyle(
                        color: _wsColor,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(_wsStatus, style: TextStyle(color: _wsColor)),
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    Text(
                      'Room: $_roomId | PlayerID: $_playerId | Players: ${_players.length}',
                      style: const TextStyle(
                        fontSize: 11,
                        color: Colors.white70,
                      ),
                    ),
                    const Spacer(),
                    SizedBox(
                      height: 30,
                      child: ElevatedButton(
                        onPressed: _runSelfTest,
                        child: const Text('🔄 自检'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          // World Area
          Expanded(
            child: Listener(
              onPointerMove: _onPointerMove,
              child: Container(
                width: double.infinity,
                height: double.infinity,
                color: Colors.grey[200],
                child: CustomPaint(
                  painter: WorldPainter(players: _players.values.toList()),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _ticker?.dispose();
    _channel?.sink.close();
    super.dispose();
  }
}

// ... (WorldPainter remains the same)
class WorldPainter extends CustomPainter {
  final List<Player> players;

  WorldPainter({required this.players});

  @override
  void paint(Canvas canvas, Size size) {
    final textPainter = TextPainter(
      textAlign: TextAlign.center,
      textDirection: TextDirection.ltr,
    );

    for (var player in players) {
      final paint = Paint()..color = player.color;
      canvas.drawRect(
        Rect.fromCenter(center: player.currentPos, width: 20, height: 20),
        paint,
      );

      textPainter.text = TextSpan(
        text: player.name,
        style: const TextStyle(color: Colors.black, fontSize: 10),
      );
      textPainter.layout();
      textPainter.paint(
        canvas,
        player.currentPos - Offset(textPainter.width / 2, 20),
      );
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}

// ... (SettingsPage remains the same)
class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final TextEditingController _baseUrlController = TextEditingController();
  final TextEditingController _roomController = TextEditingController();
  final TextEditingController _nameController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  @override
  void dispose() {
    _baseUrlController.dispose();
    _roomController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    _baseUrlController.text = await AppConfig.getHttpBaseUrl();
    _roomController.text = await AppConfig.getRoomId();
    _nameController.text = await AppConfig.getPlayerName();
    setState(() {});
  }

  Future<void> _saveSettings() async {
    await AppConfig.setHttpBaseUrl(_baseUrlController.text);
    await AppConfig.setRoomId(_roomController.text);
    await AppConfig.setPlayerName(_nameController.text);
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('设置已保存')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '网络设置',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _baseUrlController,
                decoration: const InputDecoration(
                  labelText: '后端地址 (HTTP Base URL)',
                  border: OutlineInputBorder(),
                  hintText: 'https://...',
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                '玩家设置',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _roomController,
                decoration: const InputDecoration(
                  labelText: '房间名',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 10),
              TextField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: '玩家名',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton(onPressed: _saveSettings, child: const Text('保存')),
            ],
          ),
        ),
      ),
    );
  }
}
