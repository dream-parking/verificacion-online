import 'package:flutter/material.dart';

/// Color tokens shared with the web console (`webconsole/src/app/globals.css`), so the app and the
/// console look like the same bank.
abstract final class AppColors {
  static const ink = Color(0xFF1A1B1A);
  static const muted = Color(0xFF5B5B5B);
  static const placeholder = Color(0xFF6B6B6B);
  static const dark = Color(0xFF2C2A29);
  /// Main action (`--color-brand` in the console): one per screen, always with [ink] text.
  static const brand = Color(0xFF9DDFA6);
  static const brandHover = Color(0xFF84D291);

  /// Very light tint of [brand] for section headers.
  static const brandSoft = Color(0xFFE7F6E9);
  static const blue = Color(0xFF00448C);
  static const blueSoft = Color(0xFFE3EDF8);
  static const neutral100 = Color(0xFFF5F7F9);
  static const neutral200 = Color(0xFFF4F4F4);
  static const border = Color(0xFFCCCCCC);
  static const borderStrong = Color(0xFF989696);
  static const disabled = Color(0xFFE8E8E8);
  /// Errors, as in the console (`--color-danger` and the error notice).
  static const error = Color(0xFF8C1D18);
  static const errorDark = Color(0xFF8C1D18);
  static const errorBg = Color(0xFFFBEAEA);

  /// Colors of the Banco Tangamandapio logo (webconsole/public/brand). [brandGreen] also backs the
  /// welcome banner and the progress bar: white text on it has a contrast of 11:1.
  static const brandNavy = Color(0xFF081B39);
  static const brandGreen = Color(0xFF0A402B);
}

/// Text styles. The design uses Nunito for headings and Open Sans for body
/// text; until those fonts are added as assets, the system font is used
/// (San Francisco on iOS, Roboto on Android).
abstract final class AppText {
  static TextStyle heading(double size, {Color color = AppColors.ink, double? height}) =>
      TextStyle(fontSize: size, fontWeight: FontWeight.w800, color: color, height: height, letterSpacing: -0.2);

  static const body = TextStyle(fontSize: 16, height: 1.5, color: AppColors.ink);
  static const bodyBold = TextStyle(fontSize: 16, height: 1.5, fontWeight: FontWeight.w700, color: AppColors.ink);
  static const bodyMuted = TextStyle(fontSize: 16, height: 1.5, color: AppColors.muted);
  static const label = TextStyle(fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w600, color: AppColors.muted);
  static const small = TextStyle(fontSize: 14, height: 20 / 14, color: AppColors.muted);
  static const error = TextStyle(fontSize: 14, height: 20 / 14, fontWeight: FontWeight.w600, color: AppColors.error);

  /// Bank name in Cinzel, as in the web console (`font-marca`). Cinzel is a variable font:
  /// the weight is requested with [FontVariation] in addition to [FontWeight].
  static TextStyle brand(double size, {required Color color, int weight = 700, double letterSpacing = 0}) =>
      TextStyle(
        fontFamily: 'Cinzel',
        fontSize: size,
        height: 1.1,
        color: color,
        letterSpacing: letterSpacing,
        fontWeight: FontWeight.values[(weight ~/ 100) - 1],
        fontVariations: [FontVariation.weight(weight.toDouble())],
      );
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
        (states) => states.contains(WidgetState.selected) ? AppColors.brandGreen : Colors.white,
      ),
      side: const BorderSide(color: AppColors.muted, width: 2),
    ),
  );
}
