import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Shared preference keys
const String antSizeKey = 'ant_size';
const String antSpeedKey = 'ant_speed';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  // Ant Size settings
  double _antSize = 25.0;
  final double _minAntSize = 5.0;
  // *** UPDATED max size from 50.0 to 100.0 ***
  final double _maxAntSize = 100.0; 

  // Ant Speed settings
  double _antSpeed = 50.0;
  final double _minAntSpeed = 0.0;
  final double _maxAntSpeed = 300.0;

  static const platform = MethodChannel('overlay_permission');

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    if (mounted) {
      setState(() {
        _antSize = prefs.getDouble(antSizeKey) ?? 25.0;
        _antSpeed = prefs.getDouble(antSpeedKey) ?? 50.0;
      });
    }
    print(
        "[AntCrawlerFlutter] SettingsScreen: Loaded -> Size: $_antSize, Speed: $_antSpeed");
  }

  Future<void> _saveAntSize(double newSize) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(antSizeKey, newSize);
    print("[AntCrawlerFlutter] SettingsScreen: Ant Size saved: $newSize");
  }

  Future<void> _saveAntSpeed(double newSpeed) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(antSpeedKey, newSpeed);
    print("[AntCrawlerFlutter] SettingsScreen: Ant Speed saved: $newSpeed");
  }

  // --- Live Update Functions ---
  Future<void> _sendLiveAntSizeUpdate(double newSize) async {
    try {
      await platform.invokeMethod('updateAntSettings', {'size': newSize});
    } catch (e) {
      print("[AntCrawlerFlutter] SettingsScreen: Error sending live size update: $e");
    }
  }

  Future<void> _sendLiveAntSpeedUpdate(double newSpeed) async {
    try {
      await platform.invokeMethod('updateAntSettings', {'speed': newSpeed});
    } catch (e) {
      print("[AntCrawlerFlutter] SettingsScreen: Error sending live speed update: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ant Crawler Settings'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: <Widget>[
          // --- Ant Size Slider ---
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8.0),
            child: Text('Ant Size: ${_antSize.round()} px',
                style: const TextStyle(fontSize: 18)),
          ),
          Slider(
            value: _antSize,
            min: _minAntSize,
            max: _maxAntSize, // Updated
            divisions: ((_maxAntSize - _minAntSize) / 1).round(), // Steps of 1px
            label: _antSize.round().toString(),
            onChanged: (double value) {
              if (mounted) {
                setState(() {
                  _antSize = value;
                });
              }
              _sendLiveAntSizeUpdate(value);
            },
            onChangeEnd: (double value) {
              _saveAntSize(value);
            },
          ),
          const Divider(height: 40),

          // --- Ant Speed Slider ---
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8.0),
            child: Text('Ant Speed: ${_antSpeed.round()} px/sec',
                style: const TextStyle(fontSize: 18)),
          ),
          Slider(
            value: _antSpeed,
            min: _minAntSpeed,
            max: _maxAntSpeed,
            divisions: ((_maxAntSpeed - _minAntSpeed) / 5).round(),
            label: "${_antSpeed.round()} px/s",
            onChanged: (double value) {
              if (mounted) {
                setState(() {
                  _antSpeed = value;
                });
              }
              _sendLiveAntSpeedUpdate(value);
            },
            onChangeEnd: (double value) {
              _saveAntSpeed(value);
            },
          ),
          const Divider(height: 20),
        ],
      ),
    );
  }
}
