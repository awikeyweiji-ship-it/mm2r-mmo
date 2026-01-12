import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:universal_html/html.dart' as html;

class AppConfig extends ChangeNotifier {
  String? _baseUrl;
  String? get baseUrl => _baseUrl;

  String _lastHealthUrl = 'N/A';
  String get lastHealthUrl => _lastHealthUrl;

  String _lastWsUrl = 'N/A';
  String get lastWsUrl => _lastWsUrl;

  String _healthStatus = 'Pending';
  String get healthStatus => _healthStatus;

  String _wsStatus = 'Pending';
  String get wsStatus => _wsStatus;

  String get browserOrigin => kIsWeb ? html.window.location.origin : 'N/A';

  Future<void> discoverBackend() async {
    if (!kIsWeb) {
      _baseUrl = 'http://127.0.0.1:8080';
      _healthStatus = 'Skipped on non-web';
      _wsStatus = 'Skipped on non-web';
      notifyListeners();
      await _verifyBackend();
      return;
    }

    final candidates = _generateCandidatesFromUriBase();
    if (candidates.isEmpty) {
      _healthStatus = 'Error: No backend candidates found.';
      notifyListeners();
      return;
    }

    for (final candidate in candidates) {
      if (await _probeHealth(candidate)) {
        _baseUrl = candidate;
        _healthStatus = 'OK';
        await _connectWs();
        notifyListeners();
        return;
      }
    }

    _baseUrl = null;
    _healthStatus = 'Failed to connect to any candidate.';
    notifyListeners();
  }

  List<String> _generateCandidatesFromUriBase() {
    final uri = Uri.parse(browserOrigin);
    final host = uri.host;
    final scheme = uri.scheme;

    if (host.isEmpty) return [];

    // Pattern for IDX-like URLs: <...>-8675.cluster-....
    final portPattern = RegExp(r'--?\d+\.');
    final derivedHost = host.replaceAll(portPattern, '--8080.');

    final candidates = <String>{};
    if (derivedHost != host) {
      candidates.add('$scheme://$derivedHost');
    }
    // Fallback for local dev, though we aim to avoid it
    if (host.startsWith('localhost') || host.startsWith('127.0.0.1')) {
      candidates.add('http://127.0.0.1:8080');
    }

    return candidates.toList();
  }

  Future<bool> _probeHealth(String candidate) async {
    _lastHealthUrl = '$candidate/health';
    notifyListeners();
    try {
      final response = await http
          .get(Uri.parse(_lastHealthUrl))
          .timeout(const Duration(seconds: 3));
      if (response.statusCode == 200) {
        return true;
      }
    } catch (e) {
      // Ignores errors, we'll just try the next candidate
    }
    return false;
  }

  Future<void> _verifyBackend() async {
    if (_baseUrl == null) return;
    if (await _probeHealth(_baseUrl!)) {
      _healthStatus = 'OK';
    } else {
      _healthStatus = 'Failed';
    }
    await _connectWs();
    notifyListeners();
  }

  Future<void> _connectWs() async {
    if (_baseUrl == null) {
      _wsStatus = 'Failed: No base URL';
      return;
    }
    final wsUrl = '${_baseUrl!.replaceFirst(RegExp(r'^http'), 'ws')}/ws';
    _lastWsUrl = wsUrl;
    notifyListeners();

    try {
      final channel = html.WebSocket(wsUrl);
      await channel.onOpen.first.timeout(const Duration(seconds: 3));
      _wsStatus = 'OK';
      channel.close();
    } catch (e) {
      _wsStatus = 'Failed: ${e.toString().substring(0, 50)}...';
    }
    notifyListeners();
  }
}
