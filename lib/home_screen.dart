import 'dart:io';
import 'dart:math';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'settings_screen.dart';

// Keys for settings
const String antSizeKey = 'ant_size';
const String antSpeedKey = 'ant_speed';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  // State variables
  bool _isCheckingPermission = true;
  bool _showPermission = false;
  bool _showWelcome = false;
  bool _permissionCheckInProgress = false;
  bool _isAntCrawling = false;
  String _statusText = "Ant is resting.";

  // Slider state
  double _antSize = 50.0; // The reliable default size
  double _antSpeed = 50.0;

  static const platform = MethodChannel('overlay_permission');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // Call the setup logic directly from initState
    _loadSettingsAndPermissions();
  }
  
  // *** REVERTED: Removed the complex screen size logic for simplicity ***
  Future<void> _loadSettingsAndPermissions() async {
    final prefs = await SharedPreferences.getInstance();
    
    // Set state with a fixed default of 50.0 if nothing is saved
    if (mounted) {
      setState(() {
        _antSize = prefs.getDouble(antSizeKey) ?? 50.0;
        _antSpeed = prefs.getDouble(antSpeedKey) ?? 50.0;
      });
    }

    // Now proceed with permission checks etc.
    _handleStartupLogic();
  }
  
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted && !_showWelcome && !_showPermission && !_isCheckingPermission && !_permissionCheckInProgress) {
      _syncOverlayStatusWithNative();
    }
  }

  Future<void> _handleStartupLogic() async {
    final prefs = await SharedPreferences.getInstance();
    bool isFirstTime = prefs.getBool('is_first_time') ?? true;
    if (isFirstTime) {
      if (mounted) setState(() { _showWelcome = true; _isCheckingPermission = false; });
      await prefs.setBool('is_first_time', false);
    } else {
      if (mounted) setState(() { _isCheckingPermission = true; });
      await _checkOverlayPermission();
    }
  }

  Future<void> _checkOverlayPermission() async {
    if (!mounted || _permissionCheckInProgress) return;
    setState(() => _permissionCheckInProgress = true);
    bool granted = await _hasOverlayPermission();
    if (mounted) {
      setState(() {
        _showPermission = !granted;
        _isCheckingPermission = false;
        _permissionCheckInProgress = false;
      });
    }
  }

  Future<void> _syncOverlayStatusWithNative() async {
    try {
      final bool? isActive = await platform.invokeMethod<bool>('isOverlayActive');
      if (mounted && isActive != null) {
        setState(() => _isAntCrawling = isActive);
        _updateStatusText();
      }
    } catch (e) { /* silent fail */ }
  }

  Future<bool> _hasOverlayPermission() async {
    try {
      return await platform.invokeMethod<bool>('checkOverlayPermission') ?? false;
    } catch (e) { return false; }
  }

  void _requestOverlayPermission() async {
    try {
      await platform.invokeMethod('requestOverlayPermission');
    } catch (e) { /* silent fail */ }
  }

  void _updateStatusText() {
    if (mounted) {
      setState(() => _statusText = _isAntCrawling ? "Ant is crawling..." : "Ant is resting.");
    }
  }
  
  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(antSizeKey, _antSize);
    await prefs.setDouble(antSpeedKey, _antSpeed);
  }

  void _sendLiveUpdate() {
    if (!_isAntCrawling) return;
    try {
      platform.invokeMethod('updateAntSettings', {'size': _antSize, 'speed': _antSpeed});
    } catch (e) { /* silent fail */ }
  }

  void _startCrawling() async {
    bool granted = await _hasOverlayPermission();
    if (!granted) {
      if (mounted) setState(() => _showPermission = true);
      return;
    }
    await _saveSettings();
    final ByteData data = await rootBundle.load('assets/images/ant.png');
    final Uint8List antImageBytes = data.buffer.asUint8List();
    try {
      await platform.invokeMethod('startOverlay', {'ant_size': _antSize, 'ant_speed': _antSpeed, 'ant_image': antImageBytes});
      if (mounted) {
        setState(() => _isAntCrawling = true);
        _updateStatusText();
      }
    } catch (e) { /* silent fail */ }
  }

  void _stopCrawling() async {
    try {
      await platform.invokeMethod('stopOverlay');
      if (mounted) {
        setState(() => _isAntCrawling = false);
        _updateStatusText();
      }
    } catch (e) { /* silent fail */ }
  }

  void _closeWelcome() async {
    setState(() { _showWelcome = false; _isCheckingPermission = true; });
    await _checkOverlayPermission();
  }

  void _declinePermissionMain() => exit(0);

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ant Crawler'),
        actions: [
          IconButton(
            icon: const Icon(Icons.info_outline_rounded),
            tooltip: 'App Info & Help',
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (context) => const SettingsScreen())),
          )
        ],
      ),
      body: Stack(
        children: [
          Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // --- Card 1: Main Controls ---
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 24.0),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircleAvatar(
                            radius: 40,
                            backgroundColor: Theme.of(context).scaffoldBackgroundColor,
                            child: Padding(
                              padding: const EdgeInsets.all(8.0),
                              child: Image.asset('assets/images/ant.png'),
                            ),
                          ),
                          const SizedBox(height: 16),
                          Text(
                            _statusText,
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                  color: _isAntCrawling ? Colors.green.shade600 : Colors.orange.shade800,
                                ),
                          ),
                          const SizedBox(height: 24),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              OutlinedButton.icon(
                                icon: const Icon(Icons.stop_circle_outlined),
                                label: const Text('Stop'),
                                onPressed: _isAntCrawling ? _stopCrawling : null,
                              ),
                              const SizedBox(width: 16),
                              FilledButton.icon(
                                icon: const Icon(Icons.play_arrow_rounded),
                                label: const Text('Start'),
                                onPressed: _isAntCrawling ? null : _startCrawling,
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  // --- Card 2: Settings Sliders ---
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 24.0),
                      child: Column(
                        children: [
                          Center(child: Text('Ant Size: ${_antSize.round()} px')),
                          Slider(
                            value: _antSize,
                            min: 5.0, max: 100.0,
                            divisions: (100 - 5),
                            label: _antSize.round().toString(),
                            onChanged: (v) { setState(() => _antSize = v); _sendLiveUpdate(); },
                            onChangeEnd: (v) => _saveSettings(),
                          ),
                          const SizedBox(height: 16),
                          Center(child: Text('Ant Speed: ${_antSpeed.round()} px/sec')),
                          Slider(
                            value: _antSpeed,
                            min: 0.0, max: 300.0,
                            divisions: 60,
                            label: _antSpeed.round().toString(),
                            onChanged: (v) { setState(() => _antSpeed = v); _sendLiveUpdate(); },
                            onChangeEnd: (v) => _saveSettings(),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          // --- Dialogs overlay ---
          if (_isCheckingPermission) const Center(child: CircularProgressIndicator()),
          if (_showWelcome)
            _buildDialog(
              icon: const _WalkingAntWelcomeAnimation(),
              title: 'Welcome!',
              content: 'This is a harmless prank app that displays an ant walking on the screen.\nEnjoy the app. :)',
              buttonText: 'Continue',
              onPressed: _closeWelcome,
            ),
          if (_showPermission)
            _buildDialog(
              icon: null, // No icon for the permission dialog
              title: 'Permission Required',
              content: 'The app needs permission to "display over other apps" to work.',
              buttonText: 'Go to Settings',
              onPressed: () {
                if (mounted) setState(() => _showPermission = false);
                _requestOverlayPermission();
              },
              showDecline: true,
            ),
        ],
      ),
    );
  }

  Widget _buildDialog({
    required String title,
    required String content,
    required String buttonText,
    required VoidCallback onPressed,
    Widget? icon,
    bool showDecline = false,
  }) {
    return Container(
      color: Colors.black.withOpacity(0.6),
      child: Center(
        child: AlertDialog(
          title: icon,
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(title, style: Theme.of(context).textTheme.headlineSmall, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              Text(content, textAlign: TextAlign.center),
            ],
          ),
          actions: <Widget>[
            if (showDecline)
              TextButton(
                onPressed: _declinePermissionMain,
                child: const Text('Decline (Exit)'),
              ),
            FilledButton(
              onPressed: onPressed,
              child: Text(buttonText),
            ),
          ],
          actionsAlignment: MainAxisAlignment.center,
        ),
      ),
    );
  }
}

class _WalkingAntWelcomeAnimation extends StatefulWidget {
  const _WalkingAntWelcomeAnimation();

  @override
  State<_WalkingAntWelcomeAnimation> createState() => _WalkingAntWelcomeAnimationState();
}

class _WalkingAntWelcomeAnimationState extends State<_WalkingAntWelcomeAnimation> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Offset> _position;
  late final Animation<double> _rotation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(seconds: 4),
      vsync: this,
    )..repeat();

    _position = TweenSequence<Offset>([
      TweenSequenceItem(
        tween: Tween(begin: const Offset(-80, 0), end: const Offset(80, 0)),
        weight: 50,
      ),
      TweenSequenceItem(
        tween: Tween(begin: const Offset(80, 0), end: const Offset(-80, 0)),
        weight: 50,
      ),
    ]).animate(CurvedAnimation(parent: _controller, curve: Curves.linear));

    _rotation = TweenSequence<double>([
      TweenSequenceItem(
        tween: ConstantTween(pi / 2),
        weight: 50,
      ),
      TweenSequenceItem(
        tween: ConstantTween(3 * pi / 2),
        weight: 50,
      ),
    ]).animate(_controller);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 40,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          return Transform.translate(
            offset: _position.value,
            child: Transform.rotate(
              angle: _rotation.value,
              child: child,
            ),
          );
        },
        child: Image.asset('assets/images/ant.png', height: 35),
      ),
    );
  }
}
