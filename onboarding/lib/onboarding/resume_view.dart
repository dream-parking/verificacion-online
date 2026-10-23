import 'package:flutter/material.dart';

import '../api/resume.dart';
import '../theme.dart';
import 'application_form.dart';
import 'widgets.dart';

const _months = ['ene.', 'feb.', 'mar.', 'abr.', 'may.', 'jun.', 'jul.', 'ago.', 'sep.', 'oct.', 'nov.', 'dic.'];

/// «8 de oct. a las 9:42 p. m.», as the design shows when a step was saved. [local] is in the phone's time zone.
String formatSavedAt(DateTime local) {
  final hour = local.hour % 12 == 0 ? 12 : local.hour % 12;
  final minutes = local.minute.toString().padLeft(2, '0');
  return '${local.day} de ${_months[local.month - 1]} a las $hour:$minutes ${local.hour < 12 ? 'a. m.' : 'p. m.'}';
}

/// «¡Hola de nuevo!»: shown on opening the app when a request was left halfway (VDI-87). Lists the 6 steps with when
/// each was saved and where the person is; the buttons are in the action bar of the flow.
class ResumeView extends StatelessWidget {
  const ResumeView({super.key, required this.info, required this.current});

  final ResumeInfo info;

  /// Step the person will be taken to. It is not always the one the API says: the DUI photos are not kept in the phone.
  final Screen current;

  static const _names = {
    Screen.basicData: 'Datos básicos',
    Screen.identityDocument: 'Tu DUI',
    Screen.selfie: 'Tu selfie',
    Screen.income: 'Tus ingresos',
    Screen.expectedActivity: 'El dinero de tu cuenta',
    Screen.review: 'Revisar y enviar',
  };

  /// A step that was already done and whose data is not in the phone, so the DUI photos have to be taken again.
  static const redoDuiNote = 'Tendrás que volver a tomar las fotos de tu DUI.';

  @override
  Widget build(BuildContext context) {
    final screens = _names.keys.toList();
    final at = screens.indexOf(current);
    final savedAt = {for (final s in info.steps) s.screen: s.savedAt};
    final name = info.firstName;

    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Center(child: Padding(padding: EdgeInsets.symmetric(vertical: 8), child: Logo())),
          const SizedBox(height: 16),
          ScreenTitle(name == null ? '¡Hola de nuevo!' : '¡Hola de nuevo, $name!'),
          const SizedBox(height: 8),
          const Text(
            'Guardamos tu solicitud. Puedes seguir justo donde la dejaste, sin volver a escribir tus datos.',
            style: AppText.bodyMuted,
          ),
          const SizedBox(height: 20),
          Container(
            decoration: BoxDecoration(border: Border.all(color: AppColors.border)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  color: AppColors.brandSoft,
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Paso ${at + 1} de ${screens.length}', style: AppText.heading(16)),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          for (var i = 0; i < screens.length; i++) ...[
                            if (i > 0) const SizedBox(width: 6),
                            Expanded(
                              child: Container(
                                height: 6,
                                decoration: BoxDecoration(
                                  color: i <= at ? AppColors.brandGreen : AppColors.border,
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
                for (var i = 0; i < screens.length; i++)
                  _StepRow(
                    number: i + 1,
                    title: _names[screens[i]]!,
                    done: i < at,
                    current: i == at,
                    detail: i < at
                        ? (savedAt[screens[i]] == null ? 'Guardado' : 'Guardado el ${formatSavedAt(savedAt[screens[i]]!.toLocal())}')
                        : i == at
                            ? 'Aquí te quedaste'
                            : 'Pendiente',
                  ),
              ],
            ),
          ),
          if (current == Screen.identityDocument) ...[
            const SizedBox(height: 14),
            const Text(redoDuiNote, style: AppText.bodyMuted),
          ],
        ],
      ),
    );
  }
}

class _StepRow extends StatelessWidget {
  const _StepRow({required this.number, required this.title, required this.detail, required this.done, required this.current});

  final int number;
  final String title;
  final String detail;
  final bool done;
  final bool current;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: const BoxDecoration(border: Border(top: BorderSide(color: AppColors.border))),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: done ? AppColors.brandGreen : Colors.transparent,
              border: done ? null : Border.all(color: current ? AppColors.ink : AppColors.border, width: current ? 2 : 1),
            ),
            child: done
                ? const Icon(Icons.check, size: 18, color: Colors.white)
                : Text('$number', style: AppText.heading(14, color: current ? AppColors.ink : AppColors.muted)),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: AppText.body.copyWith(fontWeight: current ? FontWeight.w800 : FontWeight.w500)),
                Text(detail, style: AppText.bodyMuted.copyWith(fontSize: 14)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
