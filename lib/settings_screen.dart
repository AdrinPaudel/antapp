import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart'; // We still need this for other links
import 'package:share_plus/share_plus.dart';   // Import the new share package

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  // --- Helper function to launch any URL ---
  Future<void> _launchURL(String urlString) async {
    final Uri url = Uri.parse(urlString);
    if (!await launchUrl(url, mode: LaunchMode.externalApplication)) {
      print('Could not launch $urlString');
    }
  }

  @override
  Widget build(BuildContext context) {
    // --- URLs for your pages and forms ---
    const String privacyPolicyUrl = 'https://sites.google.com/view/antcrawlprivacypolicy/home';
    const String termsOfServiceUrl = 'https://sites.google.com/view/antcrawltermsandconditions/home';
    const String feedbackFormUrl = 'https://docs.google.com/forms/d/e/1FAIpQLSdXGvV4FZ7aI8nBqjbDU3dqDAcf__oEHnzUgJV1E7oLbgYDcQ/viewform';
    
    // Using the app link you provided
    const String appStoreUrl = 'https://play.google.com/store/apps/details?id=com.adrin.antapp&hl=en-US&ah=HxhfPPr1zKD-B5CnLj3EvLrNT_A';
    const String shareText = 'Check out this funny Ant Prank app: $appStoreUrl';


    return Scaffold(
      appBar: AppBar(
        title: const Text('Info & Help'),
      ),
      body: ListView(
        children: <Widget>[
          // --- Feedback and Share Section ---
          _buildSectionHeader('Feedback and Share', context),
          _buildInfoTile(
            icon: Icons.bug_report_outlined,
            title: 'Report a Bug',
            onTap: () => _launchURL(feedbackFormUrl),
          ),
          _buildInfoTile(
            icon: Icons.contact_mail_outlined,
            title: 'Contact Us',
            onTap: () => _launchURL(feedbackFormUrl),
          ),
          _buildInfoTile(
            icon: Icons.star_outline_rounded,
            title: 'Rate Us',
            onTap: () => _launchURL(appStoreUrl),
          ),
          // *** MODIFIED: This now uses the share_plus package ***
          _buildInfoTile(
            icon: Icons.share_outlined,
            title: 'Share App',
            onTap: () {
              // This function opens the native Android share dialog
              Share.share(shareText, subject: 'Check out this cool app!');
            },
          ),
          const Divider(height: 30),

          // --- About Section ---
          _buildSectionHeader('About', context),
          _buildInfoTile(
            icon: Icons.info_outline_rounded,
            title: 'Version',
            trailing: const Text('1.0.0'),
            onTap: () {},
          ),
          _buildInfoTile(
            icon: Icons.privacy_tip_outlined,
            title: 'Privacy Policy',
            onTap: () => _launchURL(privacyPolicyUrl),
          ),
          _buildInfoTile(
            icon: Icons.description_outlined,
            title: 'Terms of Service',
            onTap: () => _launchURL(termsOfServiceUrl),
          ),
        ],
      ),
    );
  }

  // Helper widget for section headers
  Widget _buildSectionHeader(String title, BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Text(
        title,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.bold,
            ),
      ),
    );
  }

  // Helper widget to avoid repeating code for each list tile
  Widget _buildInfoTile({
    required IconData icon,
    required String title,
    required VoidCallback onTap,
    Widget? trailing,
  }) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      trailing: trailing,
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16.0),
    );
  }
}
