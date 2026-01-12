import 'dart:convert';
import 'dart:math';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
      title: 'Flutter World MMO',
      theme: ThemeData.dark(),
      debugShowCheckedModeBanner: false,
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
  
  // Interpolation state
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
    lastServerUpdate = DateTime.now().millisecondsSinceEpoch.toDouble();
  }

  Point<double> getLerpPosition(double now) {
    const serverUpdateInterval = 100.0; 
    double timeSinceUpdate = now - lastServerUpdate;
    double t = (timeSinceUpdate / serverUpdateInterval).clamp(0.0, 1.0);
    return Point(
      ui.lerpDouble(lastPosition.x, targetPosition.x, t)!,
      ui.lerpDouble(lastPosition.y, targetPosition.y, t)!,
    );
  }
}

class _WorldScreenState extends State<WorldScreen> with SingleTickerProviderStateMixin {
  AnimationController? _controller;
  Point<double> _localPos = const Point(200.0, 200.0);
  final Map<String, Player> _remotePlayers = {};
  
  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final config = Provider.of<AppConfig>(context, listen: false);
      config.discoverBackend();
      ServicesBinding.instance.keyboard.addHandler(_onKey);
    });
  }

  bool _onKey(KeyEvent event) {
    if (event is KeyDownEvent) {
      double dx = 0;
      double dy = 0;
      const step = 10.0;
      
      if (event.logicalKey == LogicalKeyboardKey.arrowUp) dy = -step;
      if (event.logicalKey == LogicalKeyboardKey.arrowDown) dy = step;
      if (event.logicalKey == LogicalKeyboardKey.arrowLeft) dx = -step;
      if (event.logicalKey == LogicalKeyboardKey.arrowRight) dx = step;
      
      if (dx != 0 || dy != 0) {
        _moveLocalPlayer(_localPos.x + dx, _localPos.y + dy);
        return true;
      }
    }
    return false;
  }
  
  void _moveLocalPlayer(double x, double y) {
      setState(() {
          _localPos = Point(x, y);
      });
      final config = Provider.of<AppConfig>(context, listen: false);
      if (config.wsStatus == 'Connected') {
          config.sendWsMessage(jsonEncode({
              'type': 'move',
              'x': x,
              'y': y
          }));
      }
  }
  
  void _handleGameState(Map<String, dynamic> data, AppConfig config) {
      final now = DateTime.now().millisecondsSinceEpoch.toDouble();
      final type = data['type'];
      
      if (type == 'state' || type == 'snapshot') {
         // Full snapshot
         final players = data['players'] as Map<String, dynamic>;
         
         // 1. Mark all existing as potentially stale (if we want strict sync), 
         //    but for snapshot we usually just update or add. 
         //    Actually, snapshot implies "this is the world".
         //    So we should remove anyone NOT in snapshot?
         //    Yes, if it's a "visible world" snapshot.
         
         Set<String> visibleIds = {};

         players.forEach((id, playerData) {
            if (id == config.playerId) return;
            visibleIds.add(id);

            final targetPos = Point(
                (playerData['x'] as num).toDouble(),
                (playerData['y'] as num).toDouble()
            );
            
            if (_remotePlayers.containsKey(id)) {
                _remotePlayers[id]!.updateTarget(targetPos, now);
            } else {
                _remotePlayers[id] = Player(
                    id: id,
                    name: playerData['name'] ?? 'Unknown',
                    color: Color(int.parse((playerData['color'] as String).substring(1), radix: 16) + 0xFF000000),
                    position: targetPos,
                );
            }
         });
         
         // Remove stale players that are not in the snapshot (moved out of AOI or disconnected)
         _remotePlayers.removeWhere((id, _) => !visibleIds.contains(id));

      } else if (type == 'delta') {
          // Delta update
          if (data['removes'] != null) {
              for (var id in data['removes']) {
                  _remotePlayers.remove(id);
              }
          }
          
          if (data['upserts'] != null) {
              for (var playerData in data['upserts']) {
                  final id = playerData['id'];
                  if (id == config.playerId) continue;

                  final targetPos = Point(
                    (playerData['x'] as num).toDouble(),
                    (playerData['y'] as num).toDouble()
                  );

                  if (_remotePlayers.containsKey(id)) {
                      _remotePlayers[id]!.updateTarget(targetPos, now);
                  } else {
                       _remotePlayers[id] = Player(
                            id: id,
                            name: playerData['name'] ?? 'Unknown',
                            color: Color(int.parse((playerData['color'] as String).substring(1), radix: 16) + 0xFF000000),
                            position: targetPos,
                        );
                  }
              }
          }
      }
  }

  @override
  void dispose() {
    ServicesBinding.instance.keyboard.removeHandler(_onKey);
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[900],
      appBar: AppBar(
        title: const Text('World MMO - S2 Scalable'),
        backgroundColor: Colors.grey[850],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(60.0),
          child: Container(
            color: Colors.black26,
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
            child: Consumer<AppConfig>(
              builder: (context, config, child) {
                return Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                            Text('Status: ${config.wsStatus}', style: TextStyle(color: config.wsStatus == 'Connected' ? Colors.green : Colors.red)),
                            Text('ID: ${config.playerId ?? "..."}', style: const TextStyle(fontSize: 10, color: Colors.white54)),
                        ]
                    ),
                    Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                            Text('Backend: ${config.baseUrl ?? "Locating..."}', style: const TextStyle(fontSize: 10, color: Colors.white54)),
                            Text('Players: ${_remotePlayers.length}', style: const TextStyle(fontSize: 10, color: Colors.white54)),
                        ]
                    )
                  ],
                );
              },
            ),
          ),
        ),
      ),
      body: GestureDetector(
        onPanUpdate: (details) {
            _moveLocalPlayer(_localPos.x + details.delta.dx, _localPos.y + details.delta.dy);
        },
        child: Consumer<AppConfig>(
            builder: (context, config, child) {
                return StreamBuilder<Map<String, dynamic>>(
                    stream: config.gameStateStream,
                    builder: (context, snapshot) {
                        if (snapshot.hasData) {
                             _handleGameState(snapshot.data!, config);
                        }
                        
                        return AnimatedBuilder(
                            animation: _controller!,
                            builder: (context, child) {
                                final now = DateTime.now().millisecondsSinceEpoch.toDouble();
                                return CustomPaint(
                                    painter: WorldPainter(
                                        localPos: _localPos,
                                        remotePlayers: _remotePlayers.values.toList(),
                                        now: now,
                                        myId: config.playerId
                                    ),
                                    size: Size.infinite,
                                );
                            }
                        );
                    }
                );
            }
        ),
      ),
    );
  }
}

class WorldPainter extends CustomPainter {
  final Point<double> localPos;
  final List<Player> remotePlayers;
  final double now;
  final String? myId;

  WorldPainter({
      required this.localPos, 
      required this.remotePlayers, 
      required this.now,
      this.myId
  });

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()..color = Colors.white10..strokeWidth = 1;
    for (double i = 0; i < size.width; i += 50) {
        canvas.drawLine(Offset(i, 0), Offset(i, size.height), gridPaint);
    }
    for (double i = 0; i < size.height; i += 50) {
        canvas.drawLine(Offset(0, i), Offset(size.width, i), gridPaint);
    }

    for (final player in remotePlayers) {
      _drawPlayer(canvas, player.getLerpPosition(now), player.color, player.name);
    }

    _drawPlayer(canvas, localPos, Colors.blue, "Me (${myId?.substring(0, 4) ?? '?'})");
  }

  void _drawPlayer(Canvas canvas, Point<double> pos, Color color, String name) {
      final paint = Paint()..color = color;
      canvas.drawCircle(Offset(pos.x, pos.y + 5), 15, Paint()..color = Colors.black26..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5));
      canvas.drawRect(Rect.fromCenter(center: Offset(pos.x, pos.y), width: 30, height: 30), paint);
      canvas.drawRect(Rect.fromCenter(center: Offset(pos.x, pos.y), width: 30, height: 30), Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 2);

      final textSpan = TextSpan(
        text: name,
        style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold, shadows: [Shadow(offset: Offset(1,1), blurRadius: 2, color: Colors.black)]),
      );
      final textPainter = TextPainter(text: textSpan, textDirection: TextDirection.ltr);
      textPainter.layout();
      textPainter.paint(canvas, Offset(pos.x - textPainter.width / 2, pos.y - 35));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
