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
      title: 'MMO World - AUTO_RECONNECT_HARDEN',
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

class WorldObject {
  final String id;
  final String type; // 'pickup', 'npc', 'portal'
  final double x;
  final double y;
  final String? label;
  final Point<double>? target; // for portal
  bool active;

  WorldObject({
      required this.id, 
      required this.type, 
      required this.x, 
      required this.y, 
      this.active = true,
      this.label,
      this.target
  });

  // Factory constructor to safely create from JSON
  factory WorldObject.fromJson(Map<String, dynamic> json, String type) {
    return WorldObject(
      id: json['id'] as String,
      type: type,
      x: (json['x'] as num).toDouble(),
      y: (json['y'] as num).toDouble(),
      label: json['label'] as String? ?? json['name'] as String?,
      target: json.containsKey('target') 
          ? Point((json['target']['x'] as num).toDouble(), (json['target']['y'] as num).toDouble())
          : null,
    );
  }
}

class _WorldScreenState extends State<WorldScreen> with SingleTickerProviderStateMixin {
  final GlobalKey _repaintKey = GlobalKey();
  AnimationController? _controller;
  Point<double> _localPos = const Point(100.0, 100.0);
  final Map<String, Player> _remotePlayers = {};
  
  final Map<String, WorldObject> _worldObjects = {};
  String _objectsSource = 'none'; 

  int _inventoryCount = 0;
  int _questsCompleted = 0;
  bool _showNpcDialog = false;
  
  String _statusMsg = "Welcome! Find the Green Pickup to start quest.";
  
  static const String buildId = String.fromEnvironment('BUILD_ID', defaultValue: 'DEV_BUILD');
  
  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
    
    _loadWorldObjects();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      // AppConfig constructor now handles this
      // final config = Provider.of<AppConfig>(context, listen: false);
      // config.discoverBackend(); 
      ServicesBinding.instance.keyboard.addHandler(_onKey);
      
      Future.delayed(const Duration(seconds: 3), _captureProof);
    });
  }

  Future<void> _loadWorldObjects() async {
    String source = 'default';
    Map<String, dynamic> data;

    try {
      // Try to load and parse the generated file first.
      final generatedString = await rootBundle.loadString('assets/poc/world_objects_generated.json');
      if (generatedString.trim().isEmpty) {
        throw Exception("Generated JSON is empty");
      }
      
      final decodedData = json.decode(generatedString);
      
      // Basic validation: check if it's a map and has at least one of the expected keys.
      if (decodedData is Map<String, dynamic> && (decodedData.containsKey('portals') || decodedData.containsKey('npcs') || decodedData.containsKey('pickups'))) {
        data = decodedData;
        source = 'generated';
        print("Successfully loaded and parsed generated world objects.");
      } else {
        throw Exception("Generated JSON is not a valid object map or is missing keys.");
      }
    } catch (e) {
      print("Failed to load generated objects ('$e'), falling back to default.");
      // If any error occurs, load the default file.
      final defaultString = await rootBundle.loadString('assets/poc/world_objects.json');
      data = json.decode(defaultString);
      source = 'default';
    }

    try {
        final newObjects = <String, WorldObject>{};
        for (var p in (data['portals'] as List? ?? [])) {
          newObjects[p['id']] = WorldObject.fromJson(p, 'portal');
        }
        for (var n in (data['npcs'] as List? ?? [])) {
          newObjects[n['id']] = WorldObject.fromJson(n, 'npc');
        }
        for (var p in (data['pickups'] as List? ?? [])) {
          newObjects[p['id']] = WorldObject.fromJson(p, 'pickup');
        }

        setState(() {
          _worldObjects.clear();
          _worldObjects.addAll(newObjects);
          _objectsSource = source;
        });
        print("Loaded ${_worldObjects.length} objects from '$source' source.");

    } catch(e) {
        print("FATAL: Could not parse even the default world objects: $e");
        setState(() {
          _objectsSource = 'error';
        });
    }
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
      Point<double> nextPos = Point(x, y);
      
      _worldObjects.forEach((id, obj) {
          if (obj.type == 'portal' && obj.target != null && obj.active) {
              if (x >= obj.x && x <= obj.x + 80 && y >= obj.y && y <= obj.y + 80) {
                  nextPos = obj.target!;
                  setState(() => _statusMsg = "✨ Portal ${obj.label} Activated! ✨");
                  Future.delayed(const Duration(seconds: 2), () {
                      if (mounted) setState(() => _statusMsg = "Find NPC to deliver items.");
                  });
              }
          }
      });

      bool nearNpc = false;
      _worldObjects.forEach((id, obj) {
          if (obj.type == 'npc' && obj.active) {
             double dist = sqrt(pow(obj.x - nextPos.x, 2) + pow(obj.y - nextPos.y, 2));
             if (dist < 60) nearNpc = true;
          }
      });

      if (nearNpc != _showNpcDialog) {
          setState(() => _showNpcDialog = nearNpc);
      }

      setState(() => _localPos = nextPos);
      
      final config = Provider.of<AppConfig>(context, listen: false);
      config.sendWsMessage(jsonEncode({'type': 'move','x': nextPos.x,'y': nextPos.y}));
  }

  void _completeQuest() {
      if (_inventoryCount > 0) {
          setState(() {
              _inventoryCount--;
              _questsCompleted++;
              _statusMsg = "✅ Quest Completed! Delivered item to NPC.";
          });
      } else {
           setState(() => _statusMsg = "❌ No items to deliver! Find a Pickup first.");
      }
  }
  
  void _handleGameState(Map<String, dynamic> data, AppConfig config) {
    try {
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
         
         if (data['objects'] != null) {
             final serverObjs = data['objects'] as List<dynamic>;
             for (var sObj in serverObjs) {
                 final id = sObj['id'];
                 if (_worldObjects.containsKey(id)) {
                     _worldObjects[id]!.active = sObj['active'] ?? true;
                 }
             }
         }

      } else if (type == 'delta') {
          (data['removes'] as List? ?? []).forEach(_remotePlayers.remove);
          
          for (var playerData in (data['upserts'] as List? ?? [])) {
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

          for (var oid in (data['objRemoves'] as List? ?? [])) {
              if (_worldObjects.containsKey(oid)) {
                  final obj = _worldObjects[oid]!;
                  if (obj.type == 'pickup' && obj.active) {
                      double dist = sqrt(pow(obj.x - _localPos.x, 2) + pow(obj.y - _localPos.y, 2));
                      if (dist < 60) {
                          setState(() {
                              _inventoryCount++;
                              _statusMsg = "📦 Picked up ${obj.label ?? 'item'}! Go to NPC.";
                          });
                      }
                  }
                  if(mounted) setState(() => obj.active = false);
              }
          }
      }
    } catch(e) {
      print("!!! dartException in _handleGameState: $e");
      final config = Provider.of<AppConfig>(context, listen: false);
      config.lastWsError = "dartException: $e";
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
                onPanUpdate: (details) => _moveLocalPlayer(_localPos.x + details.delta.dx, _localPos.y + details.delta.dy),
                child: Consumer<AppConfig>(
                    builder: (context, config, child) {
                        return StreamBuilder<Map<String, dynamic>>(
                            stream: config.gameStateStream,
                            builder: (context, snapshot) {
                                if (snapshot.hasData && snapshot.data != null) _handleGameState(snapshot.data!, config);
                                if (snapshot.hasError) print("gameStateStream ERROR: ${snapshot.error}");

                                return AnimatedBuilder(
                                    animation: _controller!,
                                    builder: (context, child) {
                                        final now = DateTime.now().millisecondsSinceEpoch.toDouble();
                                        return CustomPaint(
                                            painter: WorldPainter(
                                                localPos: _localPos,
                                                remotePlayers: _remotePlayers.values.toList(),
                                                worldObjects: _worldObjects.values.toList(),
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
            Positioned(
                top: 20,
                right: 20,
                child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                        color: Colors.black54,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.white24)
                    ),
                    child: Column(
                        children: [
                            const Text("🎒 INVENTORY", style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 5),
                            Text("$_inventoryCount", style: const TextStyle(color: Colors.yellowAccent, fontSize: 24, fontWeight: FontWeight.bold)),
                            const SizedBox(height: 10),
                            const Text("📜 QUESTS", style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
                            Text("$_questsCompleted", style: const TextStyle(color: Colors.greenAccent, fontSize: 18, fontWeight: FontWeight.bold)),
                        ]
                    )
                )
            ),
            
            if (_showNpcDialog)
            Positioned(
                bottom: 80,
                left: 0,
                right: 0,
                child: Center(
                    child: Container(
                        padding: const EdgeInsets.all(16),
                        width: 300,
                        decoration: BoxDecoration(
                            color: Colors.blueGrey[900],
                            border: Border.all(color: Colors.cyanAccent),
                            borderRadius: BorderRadius.circular(12),
                            boxShadow: const [BoxShadow(color: Colors.black45, blurRadius: 10)]
                        ),
                        child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                                const Text("NPC Interaction", style: TextStyle(color: Colors.cyanAccent, fontWeight: FontWeight.bold)),
                                const SizedBox(height: 10),
                                Text(_inventoryCount > 0 ? "Ah, you found it! Hand it over?" : "Please find the item.", 
                                    style: const TextStyle(color: Colors.white70), textAlign: TextAlign.center),
                                const SizedBox(height: 15),
                                ElevatedButton(
                                    onPressed: _inventoryCount > 0 ? _completeQuest : null,
                                    style: ElevatedButton.styleFrom(backgroundColor: Colors.cyan),
                                    child: const Text("Deliver Item", style: TextStyle(color: Colors.black))
                                )
                            ]
                        )
                    )
                )
            ),

            Positioned(
                top: 0,
                left: 0,
                child: Consumer<AppConfig>(
                    builder: (context, config, child) {
                        Color healthColor = config.healthStatus == 'OK' ? Colors.greenAccent : Colors.redAccent;
                        Color wsColor = config.wsStatus == 'Connected' ? Colors.green : (config.wsStatus.contains('Error') || config.wsStatus.contains('Exception') ? Colors.red : Colors.orange);

                        return Container(
                            width: 300, 
                            color: Colors.black.withOpacity(0.85),
                            padding: const EdgeInsets.all(8.0),
                            child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                    Text('BUILD: $buildId', style: const TextStyle(color: Colors.cyanAccent, fontSize: 10, fontWeight: FontWeight.bold)),
                                    const SizedBox(height: 4),
                                    Text('Health: ${config.healthStatus}', style: TextStyle(color: healthColor, fontSize: 11, fontWeight: FontWeight.bold)),
                                    if(config.lastHealthError.isNotEmpty) Text('  └ ${config.lastHealthError}', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.red, fontSize: 9)),

                                    const SizedBox(height: 4),
                                    Row(children: [
                                        Icon(Icons.circle, size: 8, color: wsColor),
                                        const SizedBox(width: 4),
                                        Text('WS: ${config.wsStatus}', style: TextStyle(color: wsColor, fontWeight: FontWeight.bold, fontSize: 12)),
                                        if(config.reconnectAttempt > 0) Text(' (x${config.reconnectAttempt})', style: TextStyle(color: Colors.orange, fontSize: 10)),
                                    ]),
                                    if(config.lastWsError.isNotEmpty) Text('  └ ${config.lastWsError}', maxLines: 3, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.red, fontSize: 9)),

                                    const Divider(height: 8, color: Colors.white24),
                                    Text('Me: ${config.playerId?.substring(0, min(8, config.playerId?.length ?? 0)) ?? "?"} | Players: ${config.playerCount}', style: const TextStyle(color: Colors.white, fontSize: 11)),
                                    Text('Objects: ${_worldObjects.length} ($_objectsSource)', 
                                        style: TextStyle(color: _objectsSource == 'generated' ? Colors.greenAccent : (_objectsSource == 'default' ? Colors.orangeAccent : Colors.redAccent), fontSize: 11)
                                    ),
                                ],
                            ),
                        );
                    },
                ),
            ),
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
  final List<WorldObject> worldObjects;
  final double now;
  final String? myId;

  WorldPainter({
      required this.localPos, 
      required this.remotePlayers, 
      required this.worldObjects,
      required this.now,
      this.myId
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Rect.fromLTWH(0,0, size.width, size.height), Paint()..color = Colors.black);

    final gridPaint = Paint()..color = Colors.white10..strokeWidth = 1;
    for (double i = 0; i < size.width; i += 50) {
      canvas.drawLine(Offset(i, 0), Offset(i, size.height), gridPaint);
    }
    for (double i = 0; i < size.height; i += 50) {
      canvas.drawLine(Offset(0, i), Offset(size.width, i), gridPaint);
    }

    for (final obj in worldObjects) {
        if (!obj.active) continue;
        
        if (obj.type == 'portal') {
            final portalPaint = Paint()..color = Colors.yellow.withOpacity(0.3 + 0.2 * sin(now/200));
            final rect = Rect.fromLTWH(obj.x, obj.y, 80, 80);
            canvas.drawRect(rect, portalPaint);
            canvas.drawRect(rect, Paint()..color = Colors.yellow..style = PaintingStyle.stroke..strokeWidth = 3);
            
            _paintText(canvas, obj.label ?? "PORTAL", rect.center, color: Colors.yellow, bold: true);

        } else if (obj.type == 'pickup') {
            final paint = Paint()..color = Colors.greenAccent..style = PaintingStyle.fill;
            double radius = 10 + 2 * sin(now/150);
            canvas.drawCircle(Offset(obj.x, obj.y), radius, paint);
            _paintText(canvas, obj.label ?? "ITEM", Offset(obj.x, obj.y + 15), color: Colors.green, bold: true, size: 10);

        } else if (obj.type == 'npc') {
             final paint = Paint()..color = Colors.cyan..style = PaintingStyle.fill;
             canvas.drawRect(Rect.fromCenter(center: Offset(obj.x, obj.y), width: 30, height: 40), paint);
             _paintText(canvas, obj.label ?? "NPC", Offset(obj.x, obj.y - 30), color: Colors.cyanAccent, bold: true, bgColor: Colors.black45);
        }
    }

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
      _paintText(canvas, name, Offset(pos.x, pos.y - 35), bold: true, shadows: true);
  }

  void _paintText(Canvas canvas, String text, Offset offset, {Color color = Colors.white, double size = 12, bool bold = false, Color? bgColor, bool shadows = false}) {
      final style = TextStyle(
          color: color, 
          fontSize: size, 
          fontWeight: bold ? FontWeight.bold : FontWeight.normal,
          backgroundColor: bgColor,
          shadows: shadows ? [const Shadow(offset: Offset(1,1), blurRadius: 2, color: Colors.black)] : null
      );
      final textPainter = TextPainter(text: TextSpan(text: text, style: style), textDirection: TextDirection.ltr);
      textPainter.layout();
      textPainter.paint(canvas, Offset(offset.dx - textPainter.width / 2, offset.dy - textPainter.height / 2));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}