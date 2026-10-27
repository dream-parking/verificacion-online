import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../identity/document_camera.dart';
import '../identity/dui_capture.dart';
import '../identity/frame_quality.dart';
import '../onboarding/widgets.dart';
import '../theme.dart';

/// Selfie for the liveness check (VDI-116): the front camera with an oval, a live hint about the light and a
/// shutter, as in the DUI. The person takes the photo; the API judges it. [onCaptured] gets it. It takes the whole
/// screen.
class SelfieCaptureView extends StatefulWidget {
  const SelfieCaptureView({super.key, required this.camera, required this.onCaptured, this.onBack});

  final DocumentCamera camera;
  final ValueChanged<Uint8List> onCaptured;

  /// Back arrow of the header; `null` hides its action.
  final VoidCallback? onBack;

  @override
  State<SelfieCaptureView> createState() => _SelfieCaptureViewState();
}

class _SelfieCaptureViewState extends State<SelfieCaptureView> {
  CameraSession? _session;
  StreamSubscription<FrameHint>? _hints;
  CameraUnavailable? _unavailable;
  var _hint = FrameHint.analyzing;
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
      _hints = session.hints.listen((hint) {
        if (mounted) setState(() => _hint = hint);
      });
      setState(() => _session = session);
    } on CameraUnavailable catch (e) {
      if (!_disposed) setState(() => _unavailable = e);
    }
  }

  Future<void> _take() async {
    final session = _session;
    if (session == null || _taking || _hint.blocksShutter) return;
    setState(() => _taking = true);
    try {
      final photo = await session.takePhoto();
      if (mounted) widget.onCaptured(photo);
    } on Exception catch (e) {
      debugPrint('Could not take the selfie: $e');
      if (mounted) setState(() => _unavailable = const CameraUnavailable());
    } finally {
      if (mounted) setState(() => _taking = false);
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _hints?.cancel();
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

  Widget _camera(CameraSession session) {
    return Column(
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(24, 8, 24, 8),
          child: Text(
            'Coloca tu rostro dentro del óvalo y mira a la cámara',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800, height: 1.25),
          ),
        ),
        Expanded(
          child: Stack(
            children: [
              Positioned.fill(child: session.preview),
              const Positioned.fill(child: _OvalOverlay()),
              Positioned(left: 16, right: 16, bottom: 12, child: Center(child: FrameStatusPill(_hint))),
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
              CaptureTip(Icons.light_mode_outlined, 'Buena luz'),
              CaptureTip(Icons.face_outlined, 'Rostro completo'),
              CaptureTip(Icons.face_retouching_off_outlined, 'Sin lentes ni gorra'),
            ],
          ),
        ),
        Padding(
          padding: EdgeInsets.fromLTRB(24, 16, 24, 20 + MediaQuery.paddingOf(context).bottom),
          child: Shutter(busy: _taking, blocked: _hint.blocksShutter, onPressed: _take),
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

/// Dims what is outside the oval where the face goes.
class _OvalOverlay extends StatelessWidget {
  const _OvalOverlay();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) {
        final width = (c.maxWidth * 0.68).clamp(0.0, c.maxHeight * 0.6);
        final oval = Rect.fromCenter(center: Offset(c.maxWidth / 2, c.maxHeight * 0.45), width: width, height: width * 1.3);
        return CustomPaint(painter: _OvalPainter(oval));
      },
    );
  }
}

class _OvalPainter extends CustomPainter {
  const _OvalPainter(this.oval);

  final Rect oval;

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
        ..color = AppColors.brand
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4,
    );
  }

  @override
  bool shouldRepaint(_OvalPainter old) => old.oval != oval;
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
