import 'package:flutter/material.dart';

class WelcomeDialog extends StatelessWidget {
  final VoidCallback onContinue;

  const WelcomeDialog({super.key, required this.onContinue});

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text("Welcome to the App"),
      content: const Text("This is a harmless prank app that shows an ant crawling on the screen."),
      actions: [
        TextButton(
          onPressed: onContinue,
          child: const Text("Continue"),
        ),
      ],
    );
  }
}
