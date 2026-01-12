import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:flutter/scheduler.dart';
import 'dart:convert';
import 'dart:ui';
import 'pages/map_debug_page.dart';
import 'app_config.dart';

void main() {
  runApp(const MyApp());
}

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
  String _status = '正在初始化...';
  String _baseUrl = "";
  String _roomId = "";
  String _playerId = "";
  WebSocketChannel? _channel;
  Ticker? _ticker;

  final Map<String, Player> _players = {};
  Player? _localPlayer;

  @override
  void initState() {
    super.initState();
    _initialize();
    _ticker = createTicker((_) {
      setState(() {
        _updatePlayerPositions();
      });
    });
    _ticker?.start();
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

  Future<void> _initialize() async {
    await AppConfig.discoverAndSetBaseUrl();
    _baseUrl = await AppConfig.getHttpBaseUrl();
    _roomId = await AppConfig.getRoomId();
    setState(() {
      _status = '准备连接...';
    });
    _connect();
  }

  void _connect() async {
    final wsUrl = await AppConfig.deriveWsUrl();
    setState(() {
      _status = '连接到 $wsUrl';
    });

    try {
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      setState(() {
        _status = '连接成功!';
      });

      _channel?.stream.listen(
        (message) {
          final data = jsonDecode(message);
          if (!mounted) return;

          setState(() {
            if (data['type'] == 'welcome') {
              _playerId = data['playerId'];
              _updateRoomState(data['roomState']);
              _localPlayer = _players[_playerId];
              _status = "已加入房间!";
            } else if (data['type'] == 'state') {
              _updateRoomState(data['players']);
            }
          });
        },
        onError: (error) {
          if (mounted) setState(() => _status = '连接错误: $error');
        },
        onDone: () {
          if (mounted) setState(() => _status = '连接已断开');
        },
      );
    } catch (e) {
      if (mounted) setState(() => _status = '连接失败: $e');
    }
  }

  void _updateRoomState(Map<String, dynamic> roomState) {
    final receivedPlayers = roomState.map(
      (id, data) => MapEntry(id, Player.fromJson(data)),
    );
    // Update existing players or add new ones
    receivedPlayers.forEach((id, newPlayer) {
      if (_players.containsKey(id)) {
        final oldPlayer = _players[id]!;
        if (id != _playerId) {
          // For remote players, update target
          oldPlayer.targetPos = newPlayer.targetPos;
          oldPlayer.lastUpdate = newPlayer.lastUpdate;
        }
      } else {
        _players[id] = newPlayer;
      }
    });
    // Remove players that are no longer in the room state
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
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('World')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'BaseURL: $_baseUrl',
                  style: const TextStyle(fontSize: 10, color: Colors.grey),
                ),
                Text(
                  'Room: $_roomId | PlayerID: $_playerId',
                  style: const TextStyle(fontSize: 10, color: Colors.grey),
                ),
                Text(
                  'Status: $_status | Players: ${_players.length}',
                  style: const TextStyle(fontWeight: FontWeight.bold),
                ),
              ],
            ),
          ),
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
