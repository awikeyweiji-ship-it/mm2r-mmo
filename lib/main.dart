import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';

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

  Future<void> _checkHealth() async {
    setState(() {
      _status = '连接中...';
      _statusColor = Colors.orange;
      _responseJson = '';
    });

    final prefs = await SharedPreferences.getInstance();
    final baseUrl = prefs.getString('api_base_url') ?? 'http://localhost:8080';

    try {
      // Handle potential trailing slash
      final uri = Uri.parse(baseUrl.endsWith('/') ? '${baseUrl}health' : '$baseUrl/health');
      final response = await http.get(uri);

      if (response.statusCode == 200) {
        setState(() {
          _status = 'SUCCESS';
          _statusColor = Colors.green;
          _responseJson = const JsonEncoder.withIndent('  ').convert(jsonDecode(response.body));
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
        _responseJson = '连接失败: $e\n\n提示: 如果您在 Firebase Studio/IDX 中，localhost 可能无法直接访问。请尝试在设置中配置正确的预览 URL。';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('World'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Text('连接状态: '),
                Text(
                  _status,
                  style: TextStyle(color: _statusColor, fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _checkHealth,
              child: const Text('连接测试 /health'),
            ),
            const SizedBox(height: 20),
            const Text('后端返回:', style: TextStyle(fontWeight: FontWeight.bold)),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(8.0),
              decoration: BoxDecoration(
                color: Colors.grey[200],
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: Colors.grey),
              ),
              child: Text(_responseJson.isEmpty ? 'Waiting...' : _responseJson),
            ),
            const SizedBox(height: 30),
            const Text('玩家坐标占位：', style: TextStyle(fontWeight: FontWeight.bold)),
            const Text('x=0 y=0 zone=poc'),
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
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _controller.text = prefs.getString('api_base_url') ?? 'http://localhost:8080';
    });
  }

  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('api_base_url', _controller.text);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('设置已保存')),
      );
    }
  }

  Future<void> _resetSettings() async {
    setState(() {
      _controller.text = 'http://localhost:8080';
    });
    await _saveSettings();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('环境: dev'),
            const SizedBox(height: 20),
            TextField(
              controller: _controller,
              decoration: const InputDecoration(
                labelText: '后端地址 (API Base URL)',
                border: OutlineInputBorder(),
                hintText: 'http://localhost:8080',
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                ElevatedButton(
                  onPressed: _saveSettings,
                  child: const Text('保存'),
                ),
                const SizedBox(width: 10),
                TextButton(
                  onPressed: _resetSettings,
                  child: const Text('重置为默认'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
