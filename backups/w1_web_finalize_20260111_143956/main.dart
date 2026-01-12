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

class _WorldScreenState extends State<WorldScreen> {

  @override
  void initState() {
    super.initState();
    // Auto-run discovery on startup
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Provider.of<AppConfig>(context, listen: false).discoverBackend();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('World Status'),
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
                    Text('Backend URL: ${config.baseUrl ?? \"Detecting...\"}', style: const TextStyle(fontSize: 12)),
                    Text('Health Check: ${config.healthStatus} (${config.lastHealthUrl})', style: const TextStyle(fontSize: 12)),
                    Text('WebSocket: ${config.wsStatus} (${config.lastWsUrl})', style: const TextStyle(fontSize: 12)),
                  ],
                );
              },
            ),
          ),
        ),
      ),
      body: const Center(
        child: Text('Welcome to the World!'),
      ),
    );
  }
}
