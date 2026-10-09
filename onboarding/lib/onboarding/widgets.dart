import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme.dart';

/// Yellow pill-shaped primary button (`.btn` in the design).
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({super.key, required this.text, required this.onPressed});

  final String text;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton(
        onPressed: onPressed,
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(Size.fromHeight(52)),
          padding: const WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 30)),
          shape: const WidgetStatePropertyAll(StadiumBorder()),
          elevation: const WidgetStatePropertyAll(0),
          backgroundColor: WidgetStateProperty.resolveWith((s) {
            if (s.contains(WidgetState.disabled)) return AppColors.disabled;
            if (s.contains(WidgetState.pressed) || s.contains(WidgetState.hovered)) return AppColors.brandHover;
            return AppColors.brand;
          }),
          foregroundColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.disabled) ? AppColors.muted : AppColors.ink,
          ),
          textStyle: const WidgetStatePropertyAll(
            TextStyle(fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: 0.3),
          ),
        ),
        child: Text(text.toUpperCase(), textAlign: TextAlign.center),
      ),
    );
  }
}

/// Outlined secondary button (`.btn2` in the design).
class SecondaryButton extends StatelessWidget {
  const SecondaryButton({super.key, required this.text, required this.onPressed});

  final String text;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton(
        onPressed: onPressed,
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(Size.fromHeight(48)),
          padding: const WidgetStatePropertyAll(EdgeInsets.symmetric(horizontal: 24)),
          shape: const WidgetStatePropertyAll(StadiumBorder()),
          side: const WidgetStatePropertyAll(BorderSide(color: AppColors.ink)),
          backgroundColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.pressed) ? AppColors.dark : Colors.transparent,
          ),
          foregroundColor: WidgetStateProperty.resolveWith(
            (s) => s.contains(WidgetState.pressed) ? Colors.white : AppColors.ink,
          ),
          textStyle: const WidgetStatePropertyAll(TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
        ),
        child: Text(text.toUpperCase(), textAlign: TextAlign.center),
      ),
    );
  }
}

/// Underlined blue link (`.lnk` in the design).
class LinkButton extends StatelessWidget {
  const LinkButton({super.key, required this.text, required this.onPressed, this.semanticLabel});

  final String text;
  final VoidCallback onPressed;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: semanticLabel,
      button: true,
      excludeSemantics: semanticLabel != null,
      child: TextButton(
        onPressed: onPressed,
        style: TextButton.styleFrom(
          minimumSize: const Size(44, 44),
          padding: const EdgeInsets.symmetric(horizontal: 4),
          foregroundColor: AppColors.blue,
          textStyle: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w700,
            decoration: TextDecoration.underline,
            decorationColor: AppColors.blue,
          ),
        ),
        child: Text(text),
      ),
    );
  }
}

/// Validation error message below a field.
class ErrorText extends StatelessWidget {
  const ErrorText(this.message, {super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Semantics(liveRegion: true, child: Text(message, style: AppText.error)),
    );
  }
}

/// Formatter that replaces the text with `format(text)` and puts the cursor at the end.
TextInputFormatter maskFormatter(String Function(String) format) {
  return TextInputFormatter.withFunction((_, newValue) {
    final t = format(newValue.text);
    return TextEditingValue(text: t, selection: TextSelection.collapsed(offset: t.length));
  });
}

/// Text field with a bottom line (`.fld` in the design).
class LabeledTextField extends StatelessWidget {
  const LabeledTextField({
    super.key,
    required this.controller,
    required this.placeholder,
    required this.error,
    this.label,
    this.keyboardType = TextInputType.text,
    this.autofillHints,
    this.formatters = const [],
    this.onChanged,
  });

  final TextEditingController controller;
  final String? label;
  final String placeholder;
  final String error;
  final TextInputType keyboardType;
  final Iterable<String>? autofillHints;
  final List<TextInputFormatter> formatters;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    final hasError = error.isNotEmpty;
    UnderlineInputBorder line(Color c, double w) =>
        UnderlineInputBorder(borderSide: BorderSide(color: c, width: w));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (label != null) Text(label!, style: AppText.label),
        TextField(
          controller: controller,
          keyboardType: keyboardType,
          autofillHints: autofillHints,
          inputFormatters: formatters,
          onChanged: onChanged,
          style: AppText.body,
          decoration: InputDecoration(
            hintText: placeholder,
            hintStyle: const TextStyle(color: AppColors.placeholder, fontSize: 16),
            isDense: true,
            contentPadding: const EdgeInsets.symmetric(vertical: 12),
            enabledBorder: hasError ? line(AppColors.error, 2) : line(AppColors.dark, 1),
            focusedBorder: hasError ? line(AppColors.error, 2) : line(AppColors.blue, 2),
          ),
        ),
        if (hasError) ErrorText(error),
      ],
    );
  }
}

/// Radio-style selectable option card (`.opt` in the design).
class OptionCard extends StatelessWidget {
  const OptionCard({
    super.key,
    required this.title,
    required this.selected,
    required this.onTap,
    this.description = '',
  });

  final String title;
  final String description;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final hasDescription = description.isNotEmpty;
    return Semantics(
      inMutuallyExclusiveGroup: true,
      checked: selected,
      child: Material(
        color: selected ? AppColors.brandSoft : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: selected
              ? const BorderSide(color: AppColors.brandGreen, width: 2)
              : const BorderSide(color: AppColors.borderStrong),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 56),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              child: Row(
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    width: 22,
                    height: 22,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.white,
                      border: Border.all(
                        color: selected ? AppColors.brandGreen : AppColors.muted,
                        width: selected ? 7 : 2,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: hasDescription
                        ? Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(title, style: AppText.bodyBold),
                              Text(description, style: AppText.small),
                            ],
                          )
                        : Text(title, style: AppText.body),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Segmented "Paso N de 4" progress bar.
class StepProgress extends StatelessWidget {
  const StepProgress({super.key, required this.step, this.total = 4});

  final int step;
  final int total;

  @override
  Widget build(BuildContext context) {
    final text = 'Paso $step de $total';
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 4, 24, 14),
      child: Semantics(
        label: text,
        excludeSemantics: true,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(text, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: AppColors.muted)),
            const SizedBox(height: 8),
            Row(
              children: [
                for (var i = 1; i <= total; i++) ...[
                  if (i > 1) const SizedBox(width: 6),
                  Expanded(
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 250),
                      height: 6,
                      decoration: BoxDecoration(
                        color: i <= step ? AppColors.brandGreen : AppColors.border,
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Banco Tangamandapio brand for the header: round emblem and bank name, as in the web
/// console sidebar (Logo.tsx), adapted to a white background.
class Logo extends StatelessWidget {
  const Logo({super.key});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Banco Tangamandapio',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // The emblem already has its own round frame: no extra ring, so it does not look like two circles.
          ClipOval(child: Image.asset('assets/brand/emblem.png', width: 40, height: 40, fit: BoxFit.cover)),
          const SizedBox(width: 10),
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('BANCO', style: AppText.brand(10, color: AppColors.brandGreen, weight: 600, letterSpacing: 3.2)),
              Text('TANGAMANDAPIO', style: AppText.brand(15, color: AppColors.brandNavy, weight: 700)),
            ],
          ),
        ],
      ),
    );
  }
}

/// Full logo with the tagline, for the welcome screen (same file as the console login).
class FullLogo extends StatelessWidget {
  const FullLogo({super.key, this.width = 150});

  final double width;

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      'assets/brand/logo-tangamandapio.webp',
      width: width,
      semanticLabel: 'Banco Tangamandapio. Confianza que nos une, futuro que construimos',
    );
  }
}

/// Numbered black circle for step lists.
class NumberBadge extends StatelessWidget {
  const NumberBadge(this.n, {super.key, this.size = 32});

  final int n;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: const BoxDecoration(color: AppColors.ink, shape: BoxShape.circle),
      child: Text('$n', style: AppText.heading(size >= 32 ? 16 : 14, color: Colors.white)),
    );
  }
}

/// Light gray box with explanatory text.
class InfoBox extends StatelessWidget {
  const InfoBox({super.key, required this.highlight, required this.text});

  final String highlight;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(color: AppColors.neutral100, borderRadius: BorderRadius.circular(8)),
      child: Text.rich(
        TextSpan(children: [
          TextSpan(text: highlight, style: const TextStyle(fontWeight: FontWeight.w700)),
          TextSpan(text: ' $text'),
        ]),
        style: AppText.body,
      ),
    );
  }
}

/// Colored header with an "eyebrow" and a title (income / expected activity screens).
class ColorHeader extends StatelessWidget {
  const ColorHeader({super.key, required this.color, required this.eyebrow, required this.title});

  final Color color;
  final String eyebrow;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      color: color,
      padding: const EdgeInsets.fromLTRB(24, 22, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(eyebrow, style: AppText.heading(16)),
          const SizedBox(height: 6),
          Semantics(header: true, child: Text(title, style: AppText.heading(28, height: 1.15))),
        ],
      ),
    );
  }
}

/// Screen title (h1).
class ScreenTitle extends StatelessWidget {
  const ScreenTitle(this.text, {super.key, this.size = 28});

  final String text;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(header: true, child: Text(text, style: AppText.heading(size, height: 1.15)));
  }
}

/// Section title (h2).
class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key, this.size = 18});

  final String text;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Semantics(header: true, child: Text(text, style: AppText.heading(size)));
  }
}

/// Icon + title + description row.
class IconRow extends StatelessWidget {
  const IconRow({super.key, required this.leading, required this.title, required this.description});

  final Widget leading;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        leading,
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: AppText.bodyBold),
              if (description.isNotEmpty) Text(description, style: AppText.bodyMuted),
            ],
          ),
        ),
      ],
    );
  }
}
