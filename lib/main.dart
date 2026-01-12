
import 'dart:convert';
import 'dart:math';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'app_config.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (context) => AppConfig(),
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter World',
      theme: ThemeData.dark(),
      home: const WorldScreen(),
    );
  }
}

class WorldScreen extends StatefulWidget {
  const WorldScreen({super.key});

  @override
  State<WorldScreen> createState() => _WorldScreenState();
}

class Player {
  final String id;
  final String name;
  final Color color;
  
  // For lerp
  Point<double> lastPosition;
  Point<double> targetPosition;
  double lastServerUpdate;

  Player({
    required this.id,
    required this.name,
    required this.color,
    required Point<double> position,
  }) : lastPosition = position,
       targetPosition = position,
       lastServerUpdate = DateTime.now().millisecondsSinceEpoch.toDouble();

  void updateTarget(Point<double> newPosition, double timestamp) {
    lastPosition = getLerpPosition(DateTime.now().millisecondsSinceEpoch.toDouble());
    targetPosition = newPosition;
    lastServerUpdate = timestamp;
  }

  Point<double> getLerpPosition(double now) {
    const serverUpdateRate = 1000 / 15; // 15Hz
    final timeSinceUpdate = now - lastServerUpdate;
    final t = (timeSinceUpdate / serverUpdateRate).clamp(0.0, 1.0);
    
    return Point(
      ui.lerpDouble(lastPosition.x, targetPosition.x, t)!,
      ui.lerpDouble(lastPosition.y, targetPosition.y, t)!,
    );
  }
}


class _WorldScreenState extends State<WorldScreen> with SingleTickerProviderStateMixin {
  AnimationController? _controller;
  Map<String, Player> _remotePlayers = {};

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final config = Provider.of<AppConfig>(context, listen: false);
      config.discoverBackend();
      config.addListener(_onAppConfigChange);
    });
  }

  void _onAppConfigChange() {
    final config = Provider.of<AppConfig>(context, listen: false);
    if (config.wsStatus == 'Connected') {
      config.ws?.stream.listen((message) {
        _handleWsMessage(message);
      });
    }
  }
  
  void _handleWsMessage(String message) {
    final data = jsonDecode(message);
    if (data['type'] == 'state') {
        final Map<String, dynamic> players = data['players'];
        final now = DateTime.now().millisecondsSinceEpoch.toDouble();

        setState(() {
            players.forEach((id, playerData) {
                final config = Provider.of<AppConfig>(context, listen: false);
                if (id == config.playerId) return; 

                final position = Point(playerData['x'].toDouble(), playerData['y'].toDouble());

                if (_remotePlayers.containsKey(id)) {
                    _remotePlayers[id]!.updateTarget(position, now);
                } else {
                    _remotePlayers[id] = Player(
                        id: id,
                        name: playerData['name'],
                        color: Color(int.parse(playerData['color'].substring(1), radix: 16) + 0xFF000000),
                        position: position,
                    );
                }
            });
            // Cleanup stale players
            _remotePlayers.removeWhere((id, player) => !players.containsKey(id));
        });
    }
  }


  @override
  void dispose() {
    Provider.of<AppConfig>(context, listen: false).removeListener(_onAppConfigChange);
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('World MMO'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(80.0),
          child: Padding(
            padding: const EdgeInsets.all(8.0),
            child: Consumer<AppConfig>(
              builder: (context, config, child) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Browser Origin: ${config.browserOrigin}', style: const TextStyle(fontSize: 12)),
                    Text('Backend URL: ${config.baseUrl ?? "Detecting..."}', style: const TextStyle(fontSize: 12)),
                    Text('Health Check: ${config.healthStatus} (${config.lastHealthUrl})', style: const TextStyle(fontSize: 12)),
                    Text('WebSocket: ${config.wsStatus} (${config.lastWsUrl})', style: const TextStyle(fontSize: 12)),
                  ],
                );
              },
            ),
          ),
        ),
      ),
      body: AnimatedBuilder(
          animation: _controller!,
          builder: (context, child) {
            final now = DateTime.now().millisecondsSinceEpoch.toDouble();
            return CustomPaint(
              painter: WorldPainter(remotePlayers: _remotePlayers.values.toList(), now: now),
              child: Container(),
            );
          },
      ),
    );
  }
}

class WorldPainter extends CustomPainter {
  final List<Player> remotePlayers;
  final double now;

  WorldPainter({required this.remotePlayers, required this.now});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint();
    
    // Draw remote players
    for (final player in remotePlayers) {
      paint.color = player.color;
      final pos = player.getLerpPosition(now);
      canvas.drawRect(Rect.fromCenter(center: Offset(pos.x, pos.y), width: 20, height: 20), paint);

      // Draw name tag
      final textStyle = TextStyle(color: Colors.white, fontSize: 12);
      final textSpan = TextSpan(text: player.name, style: textStyle);
      final textPainter = TextPainter(text: textSpan, textDirection: TextDirection.ltr);
      textPainter.layout();
      final offset = Offset(pos.x - textPainter.width / 2, pos.y - 30);
      textPainter.paint(canvas, offset);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
