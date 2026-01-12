import 'package:flutter/material.dart';

class MapDebugPage extends StatefulWidget {
  const MapDebugPage({super.key});

  @override
  State<MapDebugPage> createState() => _MapDebugPageState();
}

class _MapDebugPageState extends State<MapDebugPage> {
  // Map settings
  final double mapWidth = 256;
  final double mapHeight = 192;
  final double scale = 3.0; // Scale up for visibility

  // Player settings
  double playerX = 100;
  double playerY = 100;
  final double playerSize = 10;
  final double moveSpeed = 5;

  bool collisionEnabled = false;

  // Obstacles (Scale 1.0 coordinates)
  final List<Rect> obstacles = [
    const Rect.fromLTWH(50, 50, 30, 30),
    const Rect.fromLTWH(150, 100, 40, 20),
  ];

  void _move(double dx, double dy) {
    double newX = playerX + dx;
    double newY = playerY + dy;

    if (collisionEnabled) {
      // Boundary check
      if (newX < 0) newX = 0;
      if (newY < 0) newY = 0;
      if (newX > mapWidth - playerSize) newX = mapWidth - playerSize;
      if (newY > mapHeight - playerSize) newY = mapHeight - playerSize;

      // Obstacle check
      Rect playerRect = Rect.fromLTWH(newX, newY, playerSize, playerSize);
      bool hit = false;
      for (var obs in obstacles) {
        if (playerRect.overlaps(obs)) {
          hit = true;
          break;
        }
      }
      if (hit) return; // Simple blocking
    } else {
      // Even without collision, keep somewhat in bounds for sanity?
      // Or fully free. Let's keep strict bounds OFF if collision OFF,
      // but strictly speaking user asked "开启后：限制玩家不能走出图片边界".
      // So if OFF, can go out.
    }

    setState(() {
      playerX = newX;
      playerY = newY;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Map Debug')),
      body: KeyboardListener(
        focusNode: FocusNode()..requestFocus(),
        onKeyEvent: (event) {
          // Hardware keyboard support
          // Not easy to handle continuous press without ticker,
          // but single press works for "Button A" style.
          // For movement, usually we need game loop.
          // For this demo, just simple tap-move.
        },
        child: LayoutBuilder(
          builder: (context, constraints) {
            double viewportW = constraints.maxWidth;
            double viewportH = constraints.maxHeight;

            // Camera position (top-left of the viewport relative to map)
            // Desired camera center is player position * scale
            double camCenterX = (playerX + playerSize / 2) * scale;
            double camCenterY = (playerY + playerSize / 2) * scale;

            // Top-left offset to apply to the map (which starts at 0,0)
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
                          // The World Transform
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
                                    // Map Image
                                    Positioned.fill(
                                      child: Image.asset(
                                        'assets/poc/screen.png',
                                        fit: BoxFit.fill,
                                        filterQuality: FilterQuality.none,
                                        errorBuilder: (c, e, s) => Container(
                                          color: Colors.blue,
                                          child: const Center(
                                            child: Text("No Image"),
                                          ),
                                        ),
                                      ),
                                    ),
                                    // Obstacles debug
                                    if (collisionEnabled)
                                      ...obstacles.map(
                                        (r) => Positioned(
                                          left: r.left,
                                          top: r.top,
                                          width: r.width,
                                          height: r.height,
                                          child: Container(
                                            color: Colors.red.withOpacity(0.3),
                                          ),
                                        ),
                                      ),
                                    // Player
                                    Positioned(
                                      left: playerX,
                                      top: playerY,
                                      width: playerSize,
                                      height: playerSize,
                                      child: Container(
                                        decoration: BoxDecoration(
                                          color: Colors.red,
                                          border: Border.all(
                                            color: Colors.white,
                                            width: 1,
                                          ),
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
                                'Pos: (${playerX.toStringAsFixed(1)}, ${playerY.toStringAsFixed(1)})\n'
                                'Collision: ${collisionEnabled ? "ON" : "OFF"}',
                                style: const TextStyle(color: Colors.white),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                // Controls
                Container(
                  height: 140,
                  color: Colors.grey[200],
                  padding: const EdgeInsets.all(10),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      // D-Pad
                      Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          SizedBox(
                            height: 40,
                            child: ElevatedButton(
                              onPressed: () => _move(0, -moveSpeed),
                              child: const Icon(Icons.arrow_upward),
                            ),
                          ),
                          Row(
                            children: [
                              ElevatedButton(
                                onPressed: () => _move(-moveSpeed, 0),
                                child: const Icon(Icons.arrow_back),
                              ),
                              const SizedBox(width: 10),
                              ElevatedButton(
                                onPressed: () => _move(moveSpeed, 0),
                                child: const Icon(Icons.arrow_forward),
                              ),
                            ],
                          ),
                          SizedBox(
                            height: 40,
                            child: ElevatedButton(
                              onPressed: () => _move(0, moveSpeed),
                              child: const Icon(Icons.arrow_downward),
                            ),
                          ),
                        ],
                      ),
                      // Settings
                      Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Text("Options"),
                          Switch(
                            value: collisionEnabled,
                            onChanged: (v) =>
                                setState(() => collisionEnabled = v),
                          ),
                          const Text("Collision"),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
