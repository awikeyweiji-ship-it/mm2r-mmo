import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
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
        title: const Text('MM2R MMO'),
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

class WorldPage extends StatefulWidget {
  const WorldPage({super.key});

  @override
  State<WorldPage> createState() => _WorldPageState();
}

class _WorldPageState extends State<WorldPage> {
  String _status = '未连接';
  String _responseJson = '';
  Color _statusColor = Colors.grey;
  String _currentBaseUrl = "";

  @override
  void initState() {
    super.initState();
    _loadUrlAndCheck();
  }

  Future<void> _loadUrlAndCheck() async {
    final url = await AppConfig.getHttpBaseUrl();
    setState(() {
      _currentBaseUrl = url;
    });
    if (url.contains("localhost") || url.contains("127.0.0.1")) {
      setState(() {
        _status = '配置无效';
        _statusColor = Colors.red;
        _responseJson = '请不要使用 localhost，必须在“设置”中配置 IDX 的 Forwarded URL。';
      });
    } else {
      _checkHealth();
    }
  }

  Future<void> _checkHealth() async {
    setState(() {
      _status = '连接中...';
      _statusColor = Colors.orange;
      _responseJson = '';
    });

    final baseUrl = await AppConfig.getHttpBaseUrl();

    try {
      final uri = Uri.parse(
        baseUrl.endsWith('/') ? '${baseUrl}health' : '$baseUrl/health',
      );
      final response = await http.get(uri);

      if (response.statusCode == 200) {
        setState(() {
          _status = 'SUCCESS';
          _statusColor = Colors.green;
          _responseJson = const JsonEncoder.withIndent(
            '  ',
          ).convert(jsonDecode(response.body));
        });
      } else {
        setState(() {
          _status = 'ERROR: ${response.statusCode}';
          _statusColor = Colors.red;
          _responseJson = response.body;
        });
      }
    } catch (e) {
      setState(() {
        _status = 'ERROR';
        _statusColor = Colors.red;
        _responseJson =
            '连接失败: $e\n\n提示: 请检查“设置”中的 URL 是否为正确的 IDX Forwarded URL，并且后端服务已启动。';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('World / 连接测试')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              "当前后端地址: $_currentBaseUrl",
              style: TextStyle(color: Colors.grey[600]),
            ),
            const SizedBox(height: 5),
            Row(
              children: [
                const Text('连接状态: '),
                Text(
                  _status,
                  style: TextStyle(
                    color: _statusColor,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const SettingsPage(),
                    ),
                  ).then((_) => _loadUrlAndCheck()),
                  child: const Text('修改设置'),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _checkHealth,
              child: const Text('刷新连接测试 /health'),
            ),
            const SizedBox(height: 20),
            const Text('后端返回:', style: TextStyle(fontWeight: FontWeight.bold)),
            Expanded(
              child: SingleChildScrollView(
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(8.0),
                  decoration: BoxDecoration(
                    color: Colors.grey[200],
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: Colors.grey),
                  ),
                  child: Text(
                    _responseJson.isEmpty ? 'Waiting...' : _responseJson,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final TextEditingController _controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadSettings();
    _controller.addListener(() {
      setState(() {}); // Re-render on text change to show/hide warning
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _loadSettings() async {
    String url = await AppConfig.getHttpBaseUrl();
    setState(() {
      _controller.text = url;
    });
  }

  Future<void> _saveSettings() async {
    await AppConfig.setHttpBaseUrl(_controller.text);
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('设置已保存')));
    }
  }

  void _fillExample() {
    setState(() {
      _controller.text =
          "https://8080-idx-xxxx.cluster-xxxx.cloudworkstations.dev";
    });
  }

  Future<void> _resetSettings() async {
    setState(() {
      _controller.text = 'http://localhost:8080';
    });
    await _saveSettings();
  }

  @override
  Widget build(BuildContext context) {
    final bool isLocalhost =
        _controller.text.contains("localhost") ||
        _controller.text.contains("127.0.0.1");

    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '环境配置',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
              ),
              const SizedBox(height: 10),
              const Text('后端地址 (HTTP Base URL):'),
              const Text(
                '在 IDX 中，请从 Ports 面板复制 8080 的 URL。',
                style: TextStyle(color: Colors.grey, fontSize: 12),
              ),
              const SizedBox(height: 5),
              TextField(
                controller: _controller,
                decoration: const InputDecoration(
                  border: OutlineInputBorder(),
                  hintText: 'https://...',
                ),
              ),
              if (isLocalhost)
                const Padding(
                  padding: EdgeInsets.only(top: 8.0),
                  child: Text(
                    '警告: 请不要使用 localhost 或 127.0.0.1。这在 IDX 预览中无效。请使用 Ports 面板的 Forwarded URL。',
                    style: TextStyle(
                      color: Colors.red,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 10,
                children: [
                  ElevatedButton(
                    onPressed: isLocalhost
                        ? null
                        : _saveSettings, // Disable save if localhost
                    child: const Text('保存'),
                  ),
                  OutlinedButton(
                    onPressed: _fillExample,
                    child: const Text('一键填示例'),
                  ),
                  TextButton(
                    onPressed: _resetSettings,
                    child: const Text('重置为 localhost'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
