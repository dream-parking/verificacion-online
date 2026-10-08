import 'package:flutter/material.dart';

import 'onboarding/onboarding_flow.dart';
import 'theme.dart';

void main() {
  runApp(const OnboardingApp());
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
