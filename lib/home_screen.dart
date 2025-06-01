import 'dart:io'; // For Platform and exit()
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart';
// import 'settings_screen.dart'; // You'll need to create this for the actual navigation

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  bool _isCheckingPermission = true;
  bool _showPermission = false;
  bool _showWelcome = false;
  bool _permissionCheckInProgress = false;

  bool _isAntCrawling = false;
  String _statusText = "Ant crawler is stopped.";

  static const platform = MethodChannel('overlay_permission');

  @override
  void initState() {
    super.initState();
    print("[AntCrawlerFlutter] initState called.");
    WidgetsBinding.instance.addObserver(this);
    _updateStatusText();
    _handleStartupLogic().then((_) {
      if (mounted && !_showWelcome && !_showPermission) {
        print("[AntCrawlerFlutter] initState: Startup logic complete, syncing overlay status.");
        _syncOverlayStatusWithNative();
      } else {
        print("[AntCrawlerFlutter] initState: Startup logic resulted in dialog or not mounted, delaying sync.");
      }
    });
  }

  Future<void> _handleStartupLogic() async {
    final prefs = await SharedPreferences.getInstance();
    bool isFirstTime = prefs.getBool('is_first_time') ?? true;

    if (isFirstTime) {
      if (mounted) {
        setState(() {
          _showWelcome = true;
          _isCheckingPermission = false;
        });
      }
      await prefs.setBool('is_first_time', false);
    } else {
      if (mounted) {
        setState(() {
          _isCheckingPermission = true;
        });
      }
      await _checkOverlayPermission();
    }
  }

  Future<void> _checkOverlayPermission() async {
    if (!mounted) {
      print("[AntCrawlerFlutter] _checkOverlayPermission called but widget not mounted. Skipping.");
      return;
    }
    if (_permissionCheckInProgress) {
      print("[AntCrawlerFlutter] Permission check already in progress. Skipping.");
      return;
    }
    print("[AntCrawlerFlutter] Starting _checkOverlayPermission...");
    _permissionCheckInProgress = true;

    if (mounted) {
        setState(() {
            if(!_isCheckingPermission) _isCheckingPermission = true;
        });
    }

    bool granted = await _hasOverlayPermission();
    print("[AntCrawlerFlutter] Permission granted status from _hasOverlayPermission: $granted");

    if (mounted) {
      setState(() {
        _showPermission = !granted;
        _isCheckingPermission = false;
        print("[AntCrawlerFlutter] _showPermission set to: ${!granted}. _isCheckingPermission set to false.");
      });
    }
    _permissionCheckInProgress = false;
    print("[AntCrawlerFlutter] Finished _checkOverlayPermission.");
  }

  Future<void> _syncOverlayStatusWithNative() async {
    if (!mounted) return;
    print("[AntCrawlerFlutter] Syncing overlay status with native...");
    try {
      final bool? isActive = await platform.invokeMethod<bool>('isOverlayActive');
      print("[AntCrawlerFlutter] Native isOverlayActive returned: $isActive");
      if (mounted && isActive != null) {
        setState(() {
          _isAntCrawling = isActive;
        });
        _updateStatusText();
      }
    } catch (e) {
      print("[AntCrawlerFlutter] Error calling native isOverlayActive: $e");
      if (mounted) {
        // setState(() { _isAntCrawling = false; }); // Optional: default if unsure
        // _updateStatusText();
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    print("[AntCrawlerFlutter] AppLifecycleState changed to: $state");
    if (state == AppLifecycleState.resumed) {
      print("[AntCrawlerFlutter] App resumed.");
      if (mounted && !_showWelcome && !_showPermission && !_isCheckingPermission && !_permissionCheckInProgress) {
         print("[AntCrawlerFlutter] App resumed and UI active, syncing overlay status with native...");
        _syncOverlayStatusWithNative();
      } else {
         print("[AntCrawlerFlutter] App resumed but conditions not met for immediate sync. "
               "Welcome: $_showWelcome, ShowPerm: $_showPermission, CheckingPerm: $_isCheckingPermission, InProgress: $_permissionCheckInProgress");
      }
    }
  }

  void _updateStatusText() {
    if (mounted) {
      setState(() {
        _statusText = _isAntCrawling ? "Ant crawl active" : "Ant crawler has been stopped";
      });
    }
  }

  Future<bool> _hasOverlayPermission() async {
    if (Platform.isAndroid) {
      try {
        print("[AntCrawlerFlutter] Calling native checkOverlayPermission...");
        final result = await platform.invokeMethod<bool>('checkOverlayPermission');
        print("[AntCrawlerFlutter] Native checkOverlayPermission returned: $result");
        return result ?? false;
      } catch (e) {
        print("[AntCrawlerFlutter] Error calling native checkOverlayPermission: $e");
        return false;
      }
    }
    print("[AntCrawlerFlutter] Not Android platform, returning false for overlay permission.");
    return false;
  }

  void _requestOverlayPermission() async {
    if (Platform.isAndroid) {
      try {
        print("[AntCrawlerFlutter] Requesting overlay permission from native...");
        await platform.invokeMethod('requestOverlayPermission');
        print("[AntCrawlerFlutter] Native requestOverlayPermission call complete.");
      } catch (e) {
        print("[AntCrawlerFlutter] Error calling native requestOverlayPermission: $e");
      }
    }
  }
  
  void _startCrawling() async {
    if (!mounted) return;
    bool granted = await _hasOverlayPermission();
    if (!granted) {
        if (mounted) { setState(() { _showPermission = true; }); }
        print("[AntCrawlerFlutter] Start crawling denied: Overlay permission not granted.");
        return;
    }
    try {
      print("[AntCrawlerFlutter] Calling native startOverlay...");
      await platform.invokeMethod('startOverlay');
      print("[AntCrawlerFlutter] Native startOverlay call complete.");
      if (mounted) {
        setState(() { _isAntCrawling = true; });
        _updateStatusText();
      }
    } catch (e) { print('[AntCrawlerFlutter] Failed to start overlay: $e'); }
  }

  void _stopCrawling() async {
    if (!mounted) return;
    print("[AntCrawlerFlutter] _stopCrawling: CALLED. Current flags -> _isAntCrawling: $_isAntCrawling, _isCheckingPermission: $_isCheckingPermission, _showWelcome: $_showWelcome, _showPermission: $_showPermission");
    try {
      print("[AntCrawlerFlutter] _stopCrawling: Calling native stopOverlay...");
      await platform.invokeMethod('stopOverlay');
      print("[AntCrawlerFlutter] _stopCrawling: Native stopOverlay call complete.");
      if (mounted) {
        setState(() { _isAntCrawling = false; });
        _updateStatusText();
        print("[AntCrawlerFlutter] _stopCrawling: setState COMPLETE. New flags -> _isAntCrawling: $_isAntCrawling, _isCheckingPermission: $_isCheckingPermission, _showWelcome: $_showWelcome, _showPermission: $_showPermission");
      }
    } catch (e) { print('[AntCrawlerFlutter] _stopCrawling: Failed to stop overlay: $e'); }
  }

  void _closeWelcome() async {
    if (!mounted) return;
    print("[AntCrawlerFlutter] Closing welcome dialog.");
    setState(() { _showWelcome = false; });
    // After welcome is closed, check permissions which might then trigger sync if UI is ready
    await _checkOverlayPermission().then((_) {
      if (mounted && !_showWelcome && !_showPermission) {
        _syncOverlayStatusWithNative();
      }
    });
  }

  void _declinePermissionMain() {
    print("[AntCrawlerFlutter] User declined main permission. Exiting app.");
    exit(0);
  }

  @override
  void dispose() {
    print("[AntCrawlerFlutter] HomeScreen disposed.");
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Widget _buildWelcomeDialogWidget() {
    print("[AntCrawlerFlutter] Building Welcome Dialog Widget.");
    return Center(
      child: AlertDialog(
        title: const Text('Welcome to Ant Crawler!'),
        content: const Text('This is a harmless prank app that shows an ant moving across the screen. Enjoy!'),
        actions: [ TextButton( onPressed: _closeWelcome, child: const Text('Okay'), ), ],
      ),
    );
  }

  Widget _buildPermissionDialogWidget() {
    print("[AntCrawlerFlutter] Building Permission Dialog Widget.");
    return Center(
      child: AlertDialog(
        title: const Text('Permission Needed'),
        content: const Text('This app needs permission to display over other apps to work. Please grant it in the system settings.'),
        actions: [
          TextButton( onPressed: _declinePermissionMain, child: const Text('Decline'), ),
          TextButton(
            onPressed: () {
              if (mounted) { setState(() { _showPermission = false; }); }
              _requestOverlayPermission();
            },
            child: const Text('Go to Settings'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    print("[AntCrawlerFlutter] build() method: ENTERED. Flags -> _isCheckingPermission: $_isCheckingPermission, _showWelcome: $_showWelcome, _showPermission: $_showPermission, _isAntCrawling: $_isAntCrawling");
    if (_isCheckingPermission && !_showWelcome && !_showPermission) {
      print("[AntCrawlerFlutter] build(): Showing global CircularProgressIndicator.");
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    print("[AntCrawlerFlutter] build(): Proceeding to build main content.");

    // This is where the AppBar with title and settings icon is defined
    Widget mainContent = Scaffold(
      appBar: AppBar(
        title: const Text('Ant Crawler'), // <<< YOUR TITLE TEXT IS HERE
        actions: [
          IconButton(
            icon: const Icon(Icons.settings), // <<< YOUR GEAR ICON IS HERE
            onPressed: () {
              // TODO: Navigate to Settings Screen
              // For example: Navigator.push(context, MaterialPageRoute(builder: (context) => SettingsScreen()));
              print("[AntCrawlerFlutter] Settings button pressed. Navigation to settings screen not yet implemented.");
            },
          )
        ],
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
              onPressed: _isAntCrawling || _showPermission || _showWelcome ? null : _startCrawling,
              style: ElevatedButton.styleFrom(
                backgroundColor: _isAntCrawling ? Colors.grey : Colors.green,
                foregroundColor: Colors.white,
              ),
              child: const Text('Start'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _isAntCrawling && !_showPermission && !_showWelcome ? _stopCrawling : null,
              style: ElevatedButton.styleFrom(
                backgroundColor: _isAntCrawling ? Colors.red : Colors.grey,
                foregroundColor: Colors.white,
              ),
              child: const Text('Stop'),
            ),
            const SizedBox(height: 20),
            Text(_statusText),
          ],
        ),
      ),
    );

    return Stack(
      children: [
        mainContent,
        if (_showWelcome || _showPermission) Positioned.fill(child: Container(color: Colors.black.withOpacity(0.5),),),
        if (_showWelcome) _buildWelcomeDialogWidget(),
        if (_showPermission) _buildPermissionDialogWidget(),
      ],
    );
  }
}