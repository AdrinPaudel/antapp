import 'package:flutter/material.dart';

class AntOverlayWidget extends StatelessWidget {
  const AntOverlayWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      ignoring: true, // So it doesn't block any touch
      child: Container(
        width: 200,
        height: 200,
        decoration: const BoxDecoration(
          color: Colors.black,
          shape: BoxShape.circle,
        ),
      ),
    );
  }
}
