import 'package:flutter/material.dart';
import 'home_screen.dart';

void main() {
  runApp(const AntApp());
}

class AntApp extends StatelessWidget {
  const AntApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: HomeScreen(),
    );
  }
}
