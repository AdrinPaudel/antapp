import 'package:flutter/material.dart';
import 'home_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AntApp());
}

class AntApp extends StatelessWidget {
  const AntApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Ant Prank',
      // Define a new, light theme for the app
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue, // A good base color for a light theme
          brightness: Brightness.light,
        ),
        useMaterial3: true,
        scaffoldBackgroundColor: Colors.grey[100], // Off-white background
        appBarTheme: AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Colors.black87, // Black text on white app bar
          surfaceTintColor: Colors.transparent, // Removes tint on scroll
          elevation: 1,
          centerTitle: true,
        ),
        cardTheme: CardThemeData(
          elevation: 1,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          color: Colors.white, // White cards
        ),
      ),
      home: const HomeScreen(),
    );
  }
}
