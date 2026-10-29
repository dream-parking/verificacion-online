import 'package:flutter/material.dart';

import 'env.dart';
import 'onboarding/onboarding_flow.dart';
import 'theme.dart';

void main() {
  runApp(apiBaseUrl.trim().isEmpty ? const MissingApiUrl() : const OnboardingApp());
}

class OnboardingApp extends StatelessWidget {
  const OnboardingApp({super.key, this.home = const OnboardingFlow()});

  final Widget home;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Banco Tangamandapio · Abre tu cuenta',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: home,
    );
  }
}

/// Shown instead of the flow when the app was built without `API_URL` (VDI-141): it is a build mistake, so the message
/// is for the developer.
class MissingApiUrl extends StatelessWidget {
  const MissingApiUrl({super.key});

  static const message = 'API_URL is missing. Run the app with --dart-define=API_URL=<api url>. See onboarding/README.md.';

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        body: SafeArea(
          child: Padding(padding: EdgeInsets.all(24), child: Center(child: Text(message))),
        ),
      ),
    );
  }
}
