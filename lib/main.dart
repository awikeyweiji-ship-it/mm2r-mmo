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
      title: 'MMO World - S4 Quest',
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
}

class _WorldScreenState extends State<WorldScreen> with SingleTickerProviderStateMixin {
  final GlobalKey _repaintKey = GlobalKey();
  AnimationController? _controller;
  Point<double> _localPos = const Point(100.0, 100.0);
  final Map<String, Player> _remotePlayers = {};
  
  // S4 Quest State & Data Driven Objects
  final Map<String, WorldObject> _worldObjects = {};
  String _loadedObjectsSource = 'none'; // 'generated', 'default' or 'none'

  int _inventoryCount = 0;
  int _questsCompleted = 0;
  bool _showNpcDialog = false;
  final String _npcDialogText = "Hello!";
  
  String _statusMsg = "Welcome! Find the Green Pickup to start quest.";
  
  // BUILD_ID from dart-define
  static const String buildId = String.fromEnvironment('BUILD_ID', defaultValue: 'DEV_BUILD');
  
  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();
    
    // Load World Objects from JSON Asset
    _loadWorldObjects();

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final config = Provider.of<AppConfig>(context, listen: false);
      config.discoverBackend();
      ServicesBinding.instance.keyboard.addHandler(_onKey);
      
      // Auto-screenshot proof after 3s
      Future.delayed(const Duration(seconds: 3), _captureProof);
    });
  }

  Future<void> _loadWorldObjects() async {
      try {
          // Attempt to load generated objects first
          String response;
          String source = 'generated';
          try {
             response = await rootBundle.loadString('assets/poc/world_objects_generated.json');
             // Validate it has portals (basic check)
             final check = json.decode(response);
             if (check['portals'] == null || (check['portals'] as List).isEmpty) {
                 throw Exception("Generated file empty or missing portals");
             }
          } catch (e) {
             print("Generated objects not found or invalid ($e), falling back to default.");
             response = await rootBundle.loadString('assets/poc/world_objects.json');
             source = 'default';
          }

          final data = json.decode(response);
          
          setState(() {
              _loadedObjectsSource = source;
              _worldObjects.clear();
              // Portals
              if (data['portals'] != null) {
                  for (var p in data['portals']) {
                      _worldObjects[p['id']] = WorldObject(
                          id: p['id'],
                          type: 'portal',
                          x: (p['x'] as num).toDouble(),
                          y: (p['y'] as num).toDouble(),
                          label: p['label'],
                          target: Point((p['target']['x'] as num).toDouble(), (p['target']['y'] as num).toDouble())
                      );
                  }
              }
              // NPCs
              if (data['npcs'] != null) {
                  for (var n in data['npcs']) {
                      _worldObjects[n['id']] = WorldObject(
                          id: n['id'],
                          type: 'npc',
                          x: (n['x'] as num).toDouble(),
                          y: (n['y'] as num).toDouble(),
                          label: n['name']
                      );
                  }
              }
              // Pickups
              if (data['pickups'] != null) {
                  for (var p in data['pickups']) {
                      _worldObjects[p['id']] = WorldObject(
                          id: p['id'],
                          type: 'pickup',
                          x: (p['x'] as num).toDouble(),
                          y: (p['y'] as num).toDouble(),
                          label: p['label']
                      );
                  }
              }
          });
          print("Loaded ${_worldObjects.length} objects from $source JSON asset.");
      } catch (e) {
          print("Error loading world objects: $e");
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
      
      // Client-side Portal Trigger (Data Driven)
      _worldObjects.forEach((id, obj) {
          if (obj.type == 'portal' && obj.target != null) {
              // Simple rect check around center
              if (x >= obj.x && x <= obj.x + 80 && y >= obj.y && y <= obj.y + 80) {
                  nextPos = obj.target!;
                  setState(() {
                      _statusMsg = "✨ Portal ${obj.label} Activated! ✨";
                  });
                  Future.delayed(const Duration(seconds: 2), () {
                      if (mounted) setState(() => _statusMsg = "Find NPC to deliver items.");
                  });
              }
          }
      });

      // Check proximity to NPC
      bool nearNpc = false;
      _worldObjects.forEach((id, obj) {
          if (obj.type == 'npc') {
             double dist = sqrt(pow(obj.x - nextPos.x, 2) + pow(obj.y - nextPos.y, 2));
             if (dist < 60) {
                 nearNpc = true;
             }
          }
      });

      // Update state if changed
      if (nearNpc != _showNpcDialog) {
          setState(() {
              _showNpcDialog = nearNpc;
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

  void _completeQuest() {
      if (_inventoryCount > 0) {
          setState(() {
              _inventoryCount--;
              _questsCompleted++;
              _statusMsg = "✅ Quest Completed! Delivered item to NPC.";
          });
      } else {
           setState(() {
              _statusMsg = "❌ No items to deliver! Find a Pickup first.";
          });
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
         
         // S4 Handle Objects (Server Authority on STATE, Client on DEFINITION)
         // We merge server state (active/inactive) into our local definition
         if (data['objects'] != null) {
             final serverObjs = data['objects'] as List<dynamic>;
             for (var sObj in serverObjs) {
                 final id = sObj['id'];
                 if (_worldObjects.containsKey(id)) {
                     _worldObjects[id]!.active = sObj['active'] ?? true;
                 } else {
                     // Server sent an object we don't have in JSON? Add it dynamically?
                     // For now, let's respect server if it sends coords
                     if (sObj['x'] != null && sObj['y'] != null) {
                        _worldObjects[id] = WorldObject(
                            id: id,
                            type: sObj['type'],
                            x: (sObj['x'] as num).toDouble(),
                            y: (sObj['y'] as num).toDouble(),
                            active: sObj['active'] ?? true,
                            label: "DynObj"
                        );
                     }
                 }
             }
         }

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
          // S4 Object Removes (Server says it's gone/inactive)
          if (data['objRemoves'] != null) {
              for (var oid in data['objRemoves']) {
                  if (_worldObjects.containsKey(oid)) {
                      final obj = _worldObjects[oid]!;
                      
                      // Check for pickup attribution (Client Prediction/Feedback)
                      if (obj.type == 'pickup' && obj.active) {
                          double dist = sqrt(pow(obj.x - _localPos.x, 2) + pow(obj.y - _localPos.y, 2));
                          if (dist < 60) {
                              setState(() {
                                  _inventoryCount++;
                                  _statusMsg = "📦 Picked up ${obj.label ?? 'item'}! Go to NPC.";
                              });
                          }
                      }
                      
                      setState(() {
                          obj.active = false; // Mark inactive instead of removing to keep definition? 
                          // Server logic removes from its list, so snapshot won't have it.
                          // But delta says remove. 
                          // If we remove from map, we lose definition.
                          // Let's just set active=false.
                      });
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
            // Inventory HUD
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
            
            // NPC Dialog
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
                            boxShadow: [BoxShadow(color: Colors.black45, blurRadius: 10)]
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

            // Debug Overlay
            Positioned(
                top: 0,
                left: 0,
                child: Consumer<AppConfig>(
                    builder: (context, config, child) {
                        Color statusColor = Colors.grey;
                        if (config.wsStatus == 'Connected') statusColor = Colors.green;
                        if (config.wsStatus.startsWith('Error') || config.wsStatus.startsWith('Exception')) statusColor = Colors.red;
                        if (config.wsStatus == 'Disconnected') statusColor = Colors.orange;

                        return Container(
                            width: 250, // Fixed width
                            color: Colors.black87,
                            padding: const EdgeInsets.all(8.0),
                            child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                    Row(
                                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                        children: [
                                            Text('BUILD: $buildId', style: const TextStyle(color: Colors.cyanAccent, fontSize: 10, fontWeight: FontWeight.bold)),
                                        ]
                                    ),
                                    const SizedBox(height: 4),
                                    Text('Health: ${config.healthStatus}', style: TextStyle(color: config.healthStatus == 'OK' ? Colors.greenAccent : Colors.redAccent, fontSize: 11, fontWeight: FontWeight.bold)),
                                    const SizedBox(height: 2),
                                    Row(children: [
                                        Icon(Icons.circle, size: 8, color: statusColor),
                                        const SizedBox(width: 4),
                                        Text('WS: ${config.wsStatus}', style: TextStyle(color: statusColor, fontWeight: FontWeight.bold, fontSize: 12)),
                                    ]),
                                    const Divider(height: 8, color: Colors.white24),
                                    Text('Room: poc_world\nMe: ${config.playerId?.substring(0, min(8, config.playerId?.length ?? 0)) ?? "?"}\nPlayers: ${config.playerCount}\nObjs: ${_worldObjects.length}', 
                                        style: const TextStyle(color: Colors.white, fontSize: 11)
                                    ),
                                    Text('Objects Source: $_loadedObjectsSource', 
                                        style: TextStyle(color: _loadedObjectsSource == 'generated' ? Colors.greenAccent : Colors.orangeAccent, fontSize: 11)
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
    // Clear background
    canvas.drawRect(Rect.fromLTWH(0,0, size.width, size.height), Paint()..color = Colors.black);

    final gridPaint = Paint()..color = Colors.white10..strokeWidth = 1;
    for (double i = 0; i < size.width; i += 50) {
      canvas.drawLine(Offset(i, 0), Offset(i, size.height), gridPaint);
    }
    for (double i = 0; i < size.height; i += 50) {
      canvas.drawLine(Offset(0, i), Offset(size.width, i), gridPaint);
    }

    // Draw Objects
    for (final obj in worldObjects) {
        if (!obj.active && obj.type != 'portal') continue; // Portals always visible usually? Or only active ones?
        
        if (obj.type == 'portal') {
            final portalPaint = Paint()
              ..color = Colors.yellow.withOpacity(0.3 + 0.2 * sin(now/200))
              ..style = PaintingStyle.fill;
            final rect = Rect.fromLTWH(obj.x, obj.y, 80, 80);
            canvas.drawRect(rect, portalPaint);
            canvas.drawRect(rect, Paint()..color = Colors.yellow..style = PaintingStyle.stroke..strokeWidth = 3);
            
            final tp = TextPainter(text: TextSpan(text: obj.label ?? "PORTAL", style: TextStyle(color: Colors.yellow, fontWeight: FontWeight.bold, fontSize: 10)), textDirection: TextDirection.ltr);
            tp.layout();
            tp.paint(canvas, Offset(rect.center.dx - tp.width/2, rect.center.dy - tp.height/2));

        } else if (obj.type == 'pickup') {
            final paint = Paint()..color = Colors.greenAccent..style = PaintingStyle.fill;
            // Pulsing effect
            double radius = 10 + 2 * sin(now/150);
            canvas.drawCircle(Offset(obj.x, obj.y), radius, paint);
            
            final tp = TextPainter(text: TextSpan(text: obj.label ?? "ITEM", style: TextStyle(color: Colors.green, fontWeight: FontWeight.bold, fontSize: 10)), textDirection: TextDirection.ltr);
            tp.layout();
            tp.paint(canvas, Offset(obj.x - tp.width/2, obj.y + 15));

        } else if (obj.type == 'npc') {
             final paint = Paint()..color = Colors.cyan..style = PaintingStyle.fill;
             canvas.drawRect(Rect.fromCenter(center: Offset(obj.x, obj.y), width: 30, height: 40), paint);
             
             final tp = TextPainter(text: TextSpan(text: obj.label ?? "NPC", style: TextStyle(color: Colors.cyanAccent, fontWeight: FontWeight.bold, fontSize: 12, backgroundColor: Colors.black45)), textDirection: TextDirection.ltr);
             tp.layout();
             tp.paint(canvas, Offset(obj.x - tp.width/2, obj.y - 30));
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
      final textPainter = TextPainter(text: TextSpan(text: name, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold, shadows: [Shadow(offset: Offset(1,1), blurRadius: 2, color: Colors.black)])), textDirection: TextDirection.ltr);
      textPainter.layout();
      textPainter.paint(canvas, Offset(pos.x - textPainter.width / 2, pos.y - 35));
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
