import 'dart:convert';
import 'dart:math';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
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
      title: 'MMO World - S3 Playable',
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
  final GlobalKey _repaintKey = GlobalKey();
  AnimationController? _controller;
  Point<double> _localPos = const Point(100.0, 100.0);
  final Map<String, Player> _remotePlayers = {};
  
  // S3 Portal Config
  final Rect _portalRect = const Rect.fromLTWH(400, 400, 80, 80);
  final Point<double> _portalDest = const Point(100.0, 100.0);
  String _statusMsg = "MMO Started. Move to the Portal (Yellow)!";
  
  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final config = Provider.of<AppConfig>(context, listen: false);
      config.discoverBackend();
      ServicesBinding.instance.keyboard.addHandler(_onKey);
      
      // Auto-screenshot proof after 3s
      Future.delayed(const Duration(seconds: 3), _captureProof);
    });
  }

  Future<void> _captureProof() async {
    try {
        final boundary = _repaintKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
        if (boundary == null) return;
        ui.Image image = await boundary.toImage(pixelRatio: 1.0);
        ByteData? byteData = await image.toByteData(format: ui.ImageByteFormat.png);
        if (byteData != null) {
            final bytes = byteData.buffer.asUint8List();
            final b64 = base64Encode(bytes);
            print("VISUAL_PROOF_B64_START");
            print(b64.substring(0, min(100, b64.length))); 
            print("VISUAL_PROOF_B64_END");
        }
    } catch (e) {
        print("Screenshot failed: $e");
    }
  }

  bool _onKey(KeyEvent event) {
    if (event is KeyDownEvent) {
      double dx = 0;
      double dy = 0;
      const step = 20.0;
      
      if (event.logicalKey == LogicalKeyboardKey.arrowUp || event.logicalKey == LogicalKeyboardKey.keyW) dy = -step;
      if (event.logicalKey == LogicalKeyboardKey.arrowDown || event.logicalKey == LogicalKeyboardKey.keyS) dy = step;
      if (event.logicalKey == LogicalKeyboardKey.arrowLeft || event.logicalKey == LogicalKeyboardKey.keyA) dx = -step;
      if (event.logicalKey == LogicalKeyboardKey.arrowRight || event.logicalKey == LogicalKeyboardKey.keyD) dx = step;
      
      if (dx != 0 || dy != 0) {
        _moveLocalPlayer(_localPos.x + dx, _localPos.y + dy);
        return true;
      }
    }
    return false;
  }
  
  void _moveLocalPlayer(double x, double y) {
      // S3 Portal Logic
      Point<double> nextPos = Point(x, y);
      if (_portalRect.contains(Offset(x, y))) {
          nextPos = _portalDest;
          setState(() {
              _statusMsg = "✨ Portal Activated! Teleported to Spawn ✨";
          });
          Future.delayed(const Duration(seconds: 2), () {
              if (mounted) setState(() => _statusMsg = "Ready for next portal...");
          });
      }

      setState(() {
          _localPos = nextPos;
      });
      
      final config = Provider.of<AppConfig>(context, listen: false);
      if (config.wsStatus == 'Connected') {
          config.sendWsMessage(jsonEncode({
              'type': 'move',
              'x': nextPos.x,
              'y': nextPos.y
          }));
      }
  }
  
  void _handleGameState(Map<String, dynamic> data, AppConfig config) {
      final now = DateTime.now().millisecondsSinceEpoch.toDouble();
      final type = data['type'];
      
      if (type == 'snapshot') {
         final players = data['players'] as Map<String, dynamic>;
         Set<String> visibleIds = {};
         players.forEach((id, playerData) {
            if (id == config.playerId) return;
            visibleIds.add(id);
            final targetPos = Point((playerData['x'] as num).toDouble(), (playerData['y'] as num).toDouble());
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
         _remotePlayers.removeWhere((id, _) => !visibleIds.contains(id));
      } else if (type == 'delta') {
          if (data['removes'] != null) {
              for (var id in data['removes']) {
                _remotePlayers.remove(id);
              }
          }
          if (data['upserts'] != null) {
              for (var playerData in data['upserts']) {
                  final id = playerData['id'];
                  if (id == config.playerId) continue;
                  final targetPos = Point((playerData['x'] as num).toDouble(), (playerData['y'] as num).toDouble());
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
      body: Stack(
        children: [
            RepaintBoundary(
                key: _repaintKey,
                child: GestureDetector(
                onPanUpdate: (details) {
                    _moveLocalPlayer(_localPos.x + details.delta.dx, _localPos.y + details.delta.dy);
                },
                child: Consumer<AppConfig>(
                    builder: (context, config, child) {
                        return StreamBuilder<Map<String, dynamic>>(
                            stream: config.gameStateStream,
                            builder: (context, snapshot) {
                                if (snapshot.hasData) _handleGameState(snapshot.data!, config);
                                return AnimatedBuilder(
                                    animation: _controller!,
                                    builder: (context, child) {
                                        final now = DateTime.now().millisecondsSinceEpoch.toDouble();
                                        return CustomPaint(
                                            painter: WorldPainter(
                                                localPos: _localPos,
                                                remotePlayers: _remotePlayers.values.toList(),
                                                portalRect: _portalRect,
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
            ),
            // Debug Overlay
            Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: Consumer<AppConfig>(
                    builder: (context, config, child) {
                        Color statusColor = Colors.grey;
                        if (config.wsStatus == 'Connected') statusColor = Colors.green;
                        if (config.wsStatus.startsWith('Error') || config.wsStatus.startsWith('Exception')) statusColor = Colors.red;
                        if (config.wsStatus == 'Disconnected') statusColor = Colors.orange;

                        return Container(
                            color: Colors.black87,
                            padding: const EdgeInsets.all(8.0),
                            child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                    Row(
                                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                        children: [
                                            Expanded(child: Text('BaseURL: ${config.baseUrl ?? "Detecting..."}', style: const TextStyle(color: Colors.white70, fontSize: 10), overflow: TextOverflow.ellipsis)),
                                            Text('Health: ${config.healthStatus}', style: TextStyle(color: config.healthStatus == 'OK' ? Colors.greenAccent : Colors.redAccent, fontSize: 10)),
                                        ]
                                    ),
                                    const SizedBox(height: 2),
                                    Text('WS URL: ${config.lastWsUrl}', style: const TextStyle(color: Colors.white70, fontSize: 10)),
                                    const SizedBox(height: 4),
                                    Row(children: [
                                        Icon(Icons.circle, size: 8, color: statusColor),
                                        const SizedBox(width: 4),
                                        Text('WS: ${config.wsStatus}', style: TextStyle(color: statusColor, fontWeight: FontWeight.bold, fontSize: 12)),
                                        if (config.wsStatus != 'Connected') ...[
                                            const SizedBox(width: 10),
                                            SizedBox(
                                                height: 18,
                                                width: 60,
                                                child: ElevatedButton(
                                                    onPressed: config.retryConnection,
                                                    style: ElevatedButton.styleFrom(padding: EdgeInsets.zero, backgroundColor: Colors.blueGrey),
                                                    child: const Text("Retry", style: TextStyle(fontSize: 10))
                                                )
                                            )
                                        ]
                                    ]),
                                    if (config.lastWsError.isNotEmpty) 
                                        Container(
                                            margin: const EdgeInsets.only(top: 4),
                                            padding: const EdgeInsets.all(4),
                                            color: Colors.red.withOpacity(0.2),
                                            width: double.infinity,
                                            child: Text('Last Error: ${config.lastWsError}', style: const TextStyle(color: Colors.redAccent, fontSize: 10))
                                        ),
                                    const Divider(height: 8, color: Colors.white24),
                                    Text('Room: poc_world | Me: ${config.playerId?.substring(0, min(8, config.playerId?.length ?? 0)) ?? "?"} | Players: ${config.playerCount}', 
                                        style: const TextStyle(color: Colors.white, fontSize: 11)
                                    ),
                                ],
                            ),
                        );
                    },
                ),
            ),
             // Bottom Controls
             Positioned(
                 bottom: 0,
                 left: 0,
                 right: 0,
                 child: Container(
                     color: Colors.black54,
                     padding: const EdgeInsets.all(8),
                     child: Text(_statusMsg, textAlign: TextAlign.center, style: const TextStyle(color: Colors.yellowAccent)),
                 )
             )
        ],
      ),
    );
  }
}

class WorldPainter extends CustomPainter {
  final Point<double> localPos;
  final List<Player> remotePlayers;
  final Rect portalRect;
  final double now;
  final String? myId;

  WorldPainter({
      required this.localPos, 
      required this.remotePlayers, 
      required this.portalRect,
      required this.now,
      this.myId
  });

  @override
  void paint(Canvas canvas, Size size) {
    // Clear background
    canvas.drawRect(Rect.fromLTWH(0,0, size.width, size.height), Paint()..color = Colors.black);

    final gridPaint = Paint()..color = Colors.white10..strokeWidth = 1;
    for (double i = 0; i < size.width; i += 50) {
      canvas.drawLine(Offset(i, 0), Offset(i, size.height), gridPaint);
    }
    for (double i = 0; i < size.height; i += 50) {
      canvas.drawLine(Offset(0, i), Offset(size.width, i), gridPaint);
    }

    // Draw Portal
    final portalPaint = Paint()
      ..color = Colors.yellow.withOpacity(0.3 + 0.2 * sin(now/200))
      ..style = PaintingStyle.fill;
    canvas.drawRect(portalRect, portalPaint);
    canvas.drawRect(portalRect, Paint()..color = Colors.yellow..style = PaintingStyle.stroke..strokeWidth = 3);
    
    // Portal Label
    final textPainter = TextPainter(text: TextSpan(text: "PORTAL", style: TextStyle(color: Colors.yellow, fontWeight: FontWeight.bold, fontSize: 10)), textDirection: TextDirection.ltr);
    textPainter.layout();
    textPainter.paint(canvas, Offset(portalRect.center.dx - textPainter.width/2, portalRect.center.dy - textPainter.height/2));

    for (final player in remotePlayers) {
      _drawPlayer(canvas, player.getLerpPosition(now), player.color, player.name);
    }
    _drawPlayer(canvas, localPos, Colors.blue, "Me (${myId?.substring(0, min(4, myId?.length ?? 0)) ?? '?'})");
  }

  void _drawPlayer(Canvas canvas, Point<double> pos, Color color, String name) {
      final paint = Paint()..color = color;
      canvas.drawCircle(Offset(pos.x, pos.y + 5), 15, Paint()..color = Colors.black26..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5));
      canvas.drawRect(Rect.fromCenter(center: Offset(pos.x, pos.y), width: 30, height: 30), paint);
      canvas.drawRect(Rect.fromCenter(center: Offset(pos.x, pos.y), width: 30, height: 30), Paint()..color = Colors.white..style = PaintingStyle.stroke..strokeWidth = 2);
      final textPainter = TextPainter(text: TextSpan(text: name, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold, shadows: [Shadow(offset: Offset(1,1), blurRadius: 2, color: Colors.black)])), textDirection: TextDirection.ltr);
      textPainter.layout();
      textPainter.paint(canvas, Offset(pos.x - textPainter.width / 2, pos.y - 35));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
