import 'package:flutter/material.dart';

/// Tokens de color de la guía de estilos (docs/styles.md) usados en el diseño
/// "App móvil · Onboarding".
abstract final class AppColors {
  static const ink = Color(0xFF1A1B1A);
  static const muted = Color(0xFF5B5B5B);
  static const placeholder = Color(0xFF6B6B6B);
  static const dark = Color(0xFF2C2A29);
  static const yellow = Color(0xFFFDDA24);
  static const yellowHover = Color(0xFFF2CE12);
  static const blue = Color(0xFF00448C);
  static const blueSoft = Color(0xFFE3EDF8);
  static const orange = Color(0xFFFF7F41);
  static const skyblue = Color(0xFF59CBE8);
  static const pink = Color(0xFFF5B6CD);
  static const neutral100 = Color(0xFFF5F7F9);
  static const neutral200 = Color(0xFFF4F4F4);
  static const border = Color(0xFFCCCCCC);
  static const borderStrong = Color(0xFF989696);
  static const disabled = Color(0xFFE8E8E8);
  static const error = Color(0xFFB3261E);
  static const errorDark = Color(0xFF8C1D18);
  static const errorBg = Color(0xFFFDECEA);
}

/// Estilos de texto. El diseño usa Nunito para títulos y Open Sans para el
/// cuerpo; mientras no se agreguen las fuentes como assets se usa la fuente
/// del sistema (San Francisco en iOS, Roboto en Android).
abstract final class AppText {
  static TextStyle heading(double size, {Color color = AppColors.ink, double? height}) =>
      TextStyle(fontSize: size, fontWeight: FontWeight.w800, color: color, height: height, letterSpacing: -0.2);

  static const body = TextStyle(fontSize: 16, height: 1.5, color: AppColors.ink);
  static const bodyBold = TextStyle(fontSize: 16, height: 1.5, fontWeight: FontWeight.w700, color: AppColors.ink);
  static const bodyMuted = TextStyle(fontSize: 16, height: 1.5, color: AppColors.muted);
  static const label = TextStyle(fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w600, color: AppColors.muted);
  static const small = TextStyle(fontSize: 14, height: 20 / 14, color: AppColors.muted);
  static const error = TextStyle(fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w600, color: AppColors.error);
}

ThemeData buildTheme() {
  return ThemeData(
    colorScheme: ColorScheme.fromSeed(
      seedColor: AppColors.blue,
      primary: AppColors.blue,
      error: AppColors.error,
      surface: Colors.white,
    ),
    scaffoldBackgroundColor: Colors.white,
    textSelectionTheme: const TextSelectionThemeData(cursorColor: AppColors.blue),
    checkboxTheme: CheckboxThemeData(
      fillColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected) ? AppColors.blue : Colors.white,
      ),
      side: const BorderSide(color: AppColors.muted, width: 2),
    ),
  );
}
