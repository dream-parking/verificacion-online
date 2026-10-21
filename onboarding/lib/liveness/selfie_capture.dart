import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../identity/document_camera.dart';
import '../identity/dui_capture.dart';
import '../onboarding/widgets.dart';
import '../theme.dart';
import 'face_camera.dart';
import 'liveness_challenge.dart';

/// Selfie with the liveness check (VDI-116): the front camera with an oval, the three steps of the check one after the
/// other and a live hint about the face. Nothing has to be tapped: when the steps are done the selfie is taken and
/// [onCaptured] gets it. If they are not done within [timeout], [onTimeout] is called. It takes the whole screen.
class SelfieCaptureView extends StatefulWidget {
  const SelfieCaptureView({
    super.key,
    required this.camera,
    required this.onCaptured,
    required this.onTimeout,
    this.onBack,
    this.timeout = const Duration(seconds: 45),
  });

  final FaceCamera camera;
  final ValueChanged<Uint8List> onCaptured;
  final VoidCallback onTimeout;

  /// Back arrow of the header; `null` hides its action.
  final VoidCallback? onBack;
  final Duration timeout;

  @override
  State<SelfieCaptureView> createState() => _SelfieCaptureViewState();
}

class _SelfieCaptureViewState extends State<SelfieCaptureView> {
  FaceSession? _session;
  StreamSubscription<FaceFrame>? _frames;
  CameraUnavailable? _unavailable;
  final _challenge = LivenessChallenge();
  Timer? _timer;
  var _taking = false;
  var _disposed = false;

  @override
  void initState() {
    super.initState();
    _open();
  }

  Future<void> _open() async {
    setState(() => _unavailable = null);
    try {
      final session = await widget.camera.open();
      if (_disposed) {
        await session.dispose();
        return;
      }
      _frames = session.frames.listen(_onFrame);
      // The time starts when the person can see themselves.
      _timer?.cancel();
      _timer = Timer(widget.timeout, () {
        if (mounted && !_taking) widget.onTimeout();
      });
      setState(() => _session = session);
    } on CameraUnavailable catch (e) {
      if (!_disposed) setState(() => _unavailable = e);
    }
  }

  void _onFrame(FaceFrame frame) {
    if (!mounted || _taking) return;
    setState(() => _challenge.add(frame));
    if (_challenge.ready) _take();
  }

  Future<void> _take() async {
    final session = _session;
    if (session == null || _taking) return;
    _timer?.cancel();
    setState(() => _taking = true);
    try {
      final photo = await session.takePhoto();
      if (mounted) widget.onCaptured(photo);
    } on Exception catch (e) {
      debugPrint('Could not take the selfie: $e');
      if (mounted) setState(() => _unavailable = const CameraUnavailable());
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    _frames?.cancel();
    _session?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final unavailable = _unavailable;
    final session = _session;
    final Widget body;
    if (unavailable != null) {
      body = _CameraProblem(permissionDenied: unavailable.permissionDenied, onRetry: _open);
    } else if (session == null) {
      body = const Center(child: CircularProgressIndicator(color: AppColors.brand));
    } else {
      body = _camera(session);
    }
    return ColoredBox(
      color: DuiCaptureView.background,
      child: Column(
        children: [
          _Header(onBack: widget.onBack),
          Expanded(child: body),
        ],
      ),
    );
  }

  Widget _camera(FaceSession session) {
    final step = _challenge.step;
    final lookAgain = _challenge.isDone(ChallengeStep.blink);
    return Column(
      children: [
        _ChallengeProgress(challenge: _challenge),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
          child: Semantics(
            liveRegion: true,
            child: Text(
              _taking ? 'Listo, no te muevas' : lookAgain ? 'Ahora mira al frente' : step.instruction,
              key: const Key('selfie-instruction'),
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800, height: 1.25),
            ),
          ),
        ),
        Expanded(
          child: Stack(
            children: [
              Positioned.fill(child: session.preview),
              Positioned.fill(child: _OvalOverlay(good: _challenge.hint.isGood)),
              Positioned(left: 16, right: 16, bottom: 12, child: Center(child: _StatusPill(_challenge.hint))),
            ],
          ),
        ),
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 14, 16, 0),
          child: Wrap(
            alignment: WrapAlignment.center,
            spacing: 8,
            runSpacing: 8,
            children: [
              _Tip(Icons.light_mode_outlined, 'Buena luz'),
              _Tip(Icons.face_outlined, 'Rostro completo'),
              _Tip(Icons.do_not_touch_outlined, 'Sin tocar la pantalla'),
            ],
          ),
        ),
        Padding(
          padding: EdgeInsets.fromLTRB(24, 14, 24, 20 + MediaQuery.paddingOf(context).bottom),
          child: const Text(
            'Se termina sola al completar los 3 pasos',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white70, fontSize: 14),
          ),
        ),
      ],
    );
  }
}

/// Back arrow and «Paso 3 de 6 · Tu selfie».
class _Header extends StatelessWidget {
  const _Header({required this.onBack});

  final VoidCallback? onBack;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 56,
      child: Row(
        children: [
          IconButton(
            tooltip: 'Volver al paso anterior',
            onPressed: onBack,
            icon: const Icon(Icons.arrow_back_ios_new, size: 22, color: Colors.white),
          ),
          const Expanded(
            child: Text(
              'Paso 3 de 6 · Tu selfie',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white70, fontSize: 16, fontWeight: FontWeight.w700),
            ),
          ),
          // Same width as the back arrow, so the title stays in the middle.
          const SizedBox(width: 48),
        ],
      ),
    );
  }
}

/// «1 · Mira al frente», «2 · Gira la cabeza», «3 · Parpadea»: the ones done and the current one lit.
class _ChallengeProgress extends StatelessWidget {
  const _ChallengeProgress({required this.challenge});

  final LivenessChallenge challenge;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        children: [
          for (final s in ChallengeStep.values) ...[
            if (s.index > 0) const SizedBox(width: 8),
            Expanded(
              child: Builder(builder: (context) {
                final lit = challenge.isDone(s) || challenge.step == s;
                return Semantics(
                  label: '${s.index + 1}. ${s.label}${challenge.isDone(s) ? ', hecho' : ''}',
                  excludeSemantics: true,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        height: 4,
                        decoration: BoxDecoration(
                          color: lit ? AppColors.brand : Colors.white24,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '${s.index + 1} · ${s.label}',
                        style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: lit ? AppColors.brand : Colors.white54),
                      ),
                    ],
                  ),
                );
              }),
            ),
          ],
        ],
      ),
    );
  }
}

/// Dims what is outside the oval where the face goes; the oval turns green when the face is well placed.
class _OvalOverlay extends StatelessWidget {
  const _OvalOverlay({required this.good});

  final bool good;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) {
        final width = (c.maxWidth * 0.68).clamp(0.0, c.maxHeight * 0.6);
        final oval = Rect.fromCenter(center: Offset(c.maxWidth / 2, c.maxHeight * 0.45), width: width, height: width * 1.3);
        return CustomPaint(key: Key(good ? 'oval-good' : 'oval'), painter: _OvalPainter(oval, good));
      },
    );
  }
}

class _OvalPainter extends CustomPainter {
  const _OvalPainter(this.oval, this.good);

  final Rect oval;
  final bool good;

  @override
  void paint(Canvas canvas, Size size) {
    final mask = Path()
      ..addRect(Offset.zero & size)
      ..addOval(oval)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(mask, Paint()..color = Colors.black54);
    canvas.drawOval(
      oval,
      Paint()
        ..color = good ? AppColors.brand : Colors.white70
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4,
    );
  }

  @override
  bool shouldRepaint(_OvalPainter old) => old.oval != oval || old.good != good;
}

/// «Bien, tu rostro está dentro del óvalo», or what to fix.
class _StatusPill extends StatelessWidget {
  const _StatusPill(this.hint);

  final FaceHint hint;

  @override
  Widget build(BuildContext context) {
    final (icon, background, foreground) = switch (hint) {
      FaceHint.good => (Icons.check, const Color(0xFF1C3A25), AppColors.brand),
      FaceHint.searching => (Icons.face_outlined, Colors.black87, Colors.white),
      FaceHint.tooDark => (Icons.light_mode_outlined, const Color(0xFF3D3010), const Color(0xFFFFD27A)),
      FaceHint.manyFaces => (Icons.group_outlined, const Color(0xFF4A1A17), const Color(0xFFFFB4AB)),
      FaceHint.tooFar || FaceHint.tooClose || FaceHint.offCenter =>
        (Icons.center_focus_strong_outlined, const Color(0xFF3D3010), const Color(0xFFFFD27A)),
    };
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: ShapeDecoration(color: background, shape: const StadiumBorder()),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 20, color: foreground),
            const SizedBox(width: 8),
            Flexible(
              child: Text(hint.message, style: TextStyle(color: foreground, fontSize: 16, fontWeight: FontWeight.w700)),
            ),
          ],
        ),
      ),
    );
  }
}

class _Tip extends StatelessWidget {
  const _Tip(this.icon, this.text);

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: ShapeDecoration(
        shape: StadiumBorder(side: BorderSide(color: Colors.white.withValues(alpha: 0.25))),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18, color: Colors.white70),
          const SizedBox(width: 6),
          Text(text, style: const TextStyle(color: Colors.white70, fontSize: 14)),
        ],
      ),
    );
  }
}

/// The front camera cannot be used: says why and how to fix it.
class _CameraProblem extends StatelessWidget {
  const _CameraProblem({required this.permissionDenied, required this.onRetry});

  final bool permissionDenied;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const ScreenTitle('Necesitamos tu cámara', color: Colors.white),
          const SizedBox(height: 12),
          Text(
            permissionDenied
                ? 'Para tomar tu selfie necesitamos usar la cámara. Permítelo en los ajustes de tu teléfono y vuelve aquí.'
                : 'No pudimos abrir la cámara. Cierra otras aplicaciones que la estén usando e inténtalo de nuevo.',
            style: const TextStyle(color: Colors.white, fontSize: 16, height: 1.5),
          ),
          const SizedBox(height: 24),
          PrimaryButton(text: 'Intentar de nuevo', onPressed: onRetry),
        ],
      ),
    );
  }
}
