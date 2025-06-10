import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Keys for SharedPreferences (can be defined in a common constants file later)
const String customAntXKey = 'custom_ant_x';
const String customAntYKey = 'custom_ant_y';
const String hasCustomAntPositionKey = 'has_custom_ant_position';

class PickLocationScreen extends StatelessWidget {
  const PickLocationScreen({super.key});

  Future<void> _saveCustomPosition(BuildContext context, Offset localPosition) async {
    final prefs = await SharedPreferences.getInstance();
    // Get screen dimensions to save relative or absolute screen coordinates
    // For simplicity with WindowManager, saving absolute coordinates is fine.
    // If you wanted to save relative (0.0-1.0), you'd divide by screen size here.

    await prefs.setDouble(customAntXKey, localPosition.dx);
    await prefs.setDouble(customAntYKey, localPosition.dy);
    await prefs.setBool(hasCustomAntPositionKey, true);

    print("[AntCrawlerFlutter] Custom stationary position saved: X=${localPosition.dx}, Y=${localPosition.dy}");

    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Stationary position set to: X=${localPosition.dx.round()}, Y=${localPosition.dy.round()}')),
      );
      Navigator.pop(context, true); // Pop and indicate success/change
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: GestureDetector(
        onTapDown: (TapDownDetails details) {
          // Using localPosition which is relative to this GestureDetector widget.
          // Since the GestureDetector fills the screen, this is effectively screen coordinates
          // if the Scaffold has no padding/appbar (or adjusted if it does).
          // For a truly full screen tap area, ensure this widget covers everything.
          _saveCustomPosition(context, details.localPosition);
        },
        child: Container(
          width: double.infinity,
          height: double.infinity,
          color: Colors.white, // Solid white background
          child: const Center(
            child: Text(
              'Tap anywhere on the screen\nto set the ant\'s stationary position.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 18, color: Colors.black54),
            ),
          ),
        ),
      ),
    );
  }
}