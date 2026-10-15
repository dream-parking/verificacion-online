import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../onboarding/widgets.dart';
import '../theme.dart';
import 'document_camera.dart';
import 'frame_quality.dart';
import 'identity_document.dart';

/// DUI is 85.6 x 54 mm, so the frame has this width/height ratio.
const _duiRatio = 1.586;

/// Guided capture of the DUI (VDI-77): the camera with a frame, a live hint about the light and the steadiness of
/// the phone, tips, and one photo per side in [sides]. When the last one is taken it calls [onCaptured] with the
/// photos of the sides that were asked for. It takes the whole screen, with its own dark header.
class DuiCaptureView extends StatefulWidget {
  const DuiCaptureView({
    super.key,
    required this.camera,
    required this.sides,
    required this.onCaptured,
    this.onBack,
  });

  /// Background of the camera screen; the screen behind it uses it too so the status bar blends in.
  static const background = Color(0xFF141414);

  final DocumentCamera camera;

  /// Sides to photograph, in order: both the first time, or only the ones to take again.
  final List<DuiSide> sides;
  final ValueChanged<Map<DuiSide, Uint8List>> onCaptured;

  /// Back arrow of the header; `null` hides its action.
  final VoidCallback? onBack;

  @override
  State<DuiCaptureView> createState() => _DuiCaptureViewState();
}

class _DuiCaptureViewState extends State<DuiCaptureView> {
  CameraSession? _session;
  StreamSubscription<FrameHint>? _hints;
  CameraUnavailable? _unavailable;
  final _photos = <DuiSide, Uint8List>{};
  var _hint = FrameHint.analyzing;
  var _taking = false;
  var _disposed = false;

  /// Where the person tapped to focus, while the ring is shown.
  Offset? _focusRing;
  Timer? _focusRingTimer;

  DuiSide get _side => widget.sides[_photos.length];

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

  /// Focuses where the person tapped and shows a ring for a moment.
  void _focusAt(CameraSession session, Offset local, Size area) {
    if (area.isEmpty) return;
    unawaited(session.focusAt(Offset((local.dx / area.width).clamp(0.0, 1.0), (local.dy / area.height).clamp(0.0, 1.0))));
    _focusRingTimer?.cancel();
    setState(() => _focusRing = local);
    _focusRingTimer = Timer(const Duration(milliseconds: 900), () {
      if (mounted) setState(() => _focusRing = null);
    });
  }

  @override
  void dispose() {
    _focusRingTimer?.cancel();
    _disposed = true;
    _hints?.cancel();
    _session?.dispose();
    super.dispose();
  }

  Future<void> _take() async {
    final session = _session;
    if (session == null || _taking) return;
    setState(() => _taking = true);
    try {
      final photo = await session.takePhoto();
      if (!mounted) return;
      _photos[_side] = photo;
      if (_photos.length == widget.sides.length) {
        widget.onCaptured(Map.of(_photos));
      } else {
        // The other side is another scene: the hint starts over.
        setState(() => _hint = FrameHint.analyzing);
      }
    } on Exception catch (e) {
      debugPrint('Could not take the photo: $e');
      if (mounted) setState(() => _unavailable = const CameraUnavailable());
    } finally {
      if (mounted) setState(() => _taking = false);
    }
  }

  void _showHelp() {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colors.white,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SectionTitle('Para que salga bien'),
              const SizedBox(height: 12),
              for (var i = 0; i < UnreadablePhotos.tips.length; i++) ...[
                IconRow(leading: NumberBadge(i + 1), title: UnreadablePhotos.tips[i], description: ''),
                const SizedBox(height: 12),
              ],
              const SizedBox(height: 8),
              PrimaryButton(text: 'Entendido', onPressed: () => Navigator.of(context).pop()),
            ],
          ),
        ),
      ),
    );
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
          _Header(onBack: widget.onBack, onHelp: _showHelp),
          Expanded(child: body),
        ],
      ),
    );
  }

  Widget _camera(CameraSession session) {
    final front = _side == DuiSide.front;
    return Column(
      children: [
        _SideProgress(current: _side),
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
          child: Semantics(
            liveRegion: true,
            child: Text(
              front ? 'Coloca el frente de tu DUI dentro del marco' : 'Ahora coloca el reverso de tu DUI dentro del marco',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800, height: 1.25),
            ),
          ),
        ),
        Expanded(
          child: LayoutBuilder(
            builder: (context, c) => GestureDetector(
              key: const Key('camera-preview'),
              behavior: HitTestBehavior.opaque,
              onTapUp: (d) => _focusAt(session, d.localPosition, c.biggest),
              child: Stack(
                children: [
                  Positioned.fill(child: session.preview),
                  const Positioned.fill(child: _FrameOverlay()),
                  if (_focusRing != null) _FocusRing(key: const Key('focus-ring'), at: _focusRing!),
                  Positioned(left: 16, right: 16, bottom: 12, child: Center(child: _StatusPill(_hint))),
                ],
              ),
            ),
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
              _Tip(Icons.flare_outlined, 'Sin reflejos'),
              _Tip(Icons.crop_free, 'Se ven las 4 esquinas'),
            ],
          ),
        ),
        Padding(
          padding: EdgeInsets.fromLTRB(24, 16, 24, 20 + MediaQuery.paddingOf(context).bottom),
          child: _Shutter(busy: _taking, onPressed: _take),
        ),
      ],
    );
  }
}

/// Back arrow, «Paso 2 de 5 · Tu DUI» and help.
class _Header extends StatelessWidget {
  const _Header({required this.onBack, required this.onHelp});

  final VoidCallback? onBack;
  final VoidCallback onHelp;

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
              'Paso 2 de 5 · Tu DUI',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white70, fontSize: 16, fontWeight: FontWeight.w700),
            ),
          ),
          IconButton(
            tooltip: 'Ayuda para tomar la foto',
            onPressed: onHelp,
            icon: const Icon(Icons.help_outline, size: 26, color: Colors.white70),
          ),
        ],
      ),
    );
  }
}

/// Two segments, «1 · Frente» and «2 · Reverso», with the current one lit.
class _SideProgress extends StatelessWidget {
  const _SideProgress({required this.current});

  final DuiSide current;

  @override
  Widget build(BuildContext context) {
    Widget segment(String label, bool active) => Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                height: 4,
                decoration: BoxDecoration(
                  color: active ? AppColors.brand : Colors.white24,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                label,
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: active ? AppColors.brand : Colors.white54),
              ),
            ],
          ),
        );
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        children: [
          segment('1 · Frente', current == DuiSide.front),
          const SizedBox(width: 8),
          segment('2 · Reverso', current == DuiSide.back),
        ],
      ),
    );
  }
}

/// Ring where the person tapped to focus.
class _FocusRing extends StatelessWidget {
  const _FocusRing({super.key, required this.at});

  final Offset at;

  static const _size = 72.0;

  @override
  Widget build(BuildContext context) {
    return Positioned(
      left: at.dx - _size / 2,
      top: at.dy - _size / 2,
      child: IgnorePointer(
        child: Container(
          width: _size,
          height: _size,
          decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: AppColors.brand, width: 2)),
        ),
      ),
    );
  }
}

/// Dims what is outside the frame and marks the four corners where the DUI goes.
class _FrameOverlay extends StatelessWidget {
  const _FrameOverlay();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) {
        final width = c.maxWidth - 48;
        final rect = Rect.fromCenter(
          center: Offset(c.maxWidth / 2, c.maxHeight / 2),
          width: width,
          height: width / _duiRatio,
        );
        return CustomPaint(painter: _FramePainter(rect));
      },
    );
  }
}

class _FramePainter extends CustomPainter {
  const _FramePainter(this.frame);

  final Rect frame;

  @override
  void paint(Canvas canvas, Size size) {
    final rounded = RRect.fromRectAndRadius(frame, const Radius.circular(14));
    final mask = Path()
      ..addRect(Offset.zero & size)
      ..addRRect(rounded)
      ..fillType = PathFillType.evenOdd;
    canvas.drawPath(mask, Paint()..color = Colors.black54);

    final corner = Paint()
      ..color = AppColors.brand
      ..style = PaintingStyle.stroke
      ..strokeWidth = 5
      ..strokeCap = StrokeCap.round;
    const arm = 28.0;
    for (final (anchor, dx, dy) in [
      (frame.topLeft, 1.0, 1.0),
      (frame.topRight, -1.0, 1.0),
      (frame.bottomLeft, 1.0, -1.0),
      (frame.bottomRight, -1.0, -1.0),
    ]) {
      canvas.drawLine(anchor, anchor + Offset(arm * dx, 0), corner);
      canvas.drawLine(anchor, anchor + Offset(0, arm * dy), corner);
    }
  }

  @override
  bool shouldRepaint(_FramePainter old) => old.frame != frame;
}

/// «Bien, mantén el teléfono quieto», or what to fix: light, movement.
class _StatusPill extends StatelessWidget {
  const _StatusPill(this.hint);

  final FrameHint hint;

  @override
  Widget build(BuildContext context) {
    final (icon, background, foreground) = switch (hint) {
      FrameHint.good => (Icons.check, const Color(0xFF1C3A25), AppColors.brand),
      FrameHint.analyzing => (Icons.center_focus_strong_outlined, Colors.black87, Colors.white),
      FrameHint.tooDark => (Icons.light_mode_outlined, const Color(0xFF3D3010), const Color(0xFFFFD27A)),
      FrameHint.moving => (Icons.open_with, const Color(0xFF3D3010), const Color(0xFFFFD27A)),
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
              child: Text(
                hint.message,
                style: TextStyle(color: foreground, fontSize: 16, fontWeight: FontWeight.w700),
              ),
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

class _Shutter extends StatelessWidget {
  const _Shutter({required this.busy, required this.onPressed});

  final bool busy;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: 'Tomar foto',
      child: GestureDetector(
        key: const Key('shutter'),
        onTap: busy ? null : onPressed,
        child: Container(
          width: 80,
          height: 80,
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: Colors.white54, width: 4)),
          child: DecoratedBox(
            decoration: BoxDecoration(shape: BoxShape.circle, color: busy ? Colors.white38 : AppColors.brand),
          ),
        ),
      ),
    );
  }
}

/// The camera cannot be used: says why and how to fix it.
class _CameraProblem extends StatelessWidget {
  const _CameraProblem({required this.permissionDenied, required this.onRetry});

  final bool permissionDenied;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const ScreenTitle('Necesitamos tu cámara', color: Colors.white),
          const SizedBox(height: 12),
          Text(
            permissionDenied
                ? 'Para fotografiar tu DUI necesitamos usar la cámara. Permítelo en los ajustes de tu teléfono y vuelve aquí.'
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
