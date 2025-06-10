import 'package:flutter/material.dart';
import 'home_screen.dart';

void main() {
  // This ensures that Flutter is ready before we run the app.
  WidgetsFlutterBinding.ensureInitialized();
  
  runApp(const AntApp());
}

class AntApp extends StatelessWidget {
  const AntApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      // Hides the "debug" banner
      debugShowCheckedModeBanner: false,
      // Sets the HomeScreen as the starting point of the app
      home: HomeScreen(),
    );
  }
}
