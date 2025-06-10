import 'package:flutter/material.dart';

class AntOverlayWidget extends StatelessWidget {
  const AntOverlayWidget({super.key});

  @override
  Widget build(BuildContext context) {
    // This widget is a placeholder for our ant.
    // It is a simple black square for now.
    // In the next step, we will replace this with an Image widget.
    return IgnorePointer(
      ignoring: true, // So it doesn't block any touch events on the screen.
      child: Container(
        // The color of our placeholder ant.
        color: Colors.black,
      ),
    );
  }
}
