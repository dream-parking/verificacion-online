import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../onboarding/widgets.dart';
import '../theme.dart';
import 'document_camera.dart';
import 'identity_document.dart';

/// DUI is 85.6 x 54 mm, so the frame has this width/height ratio.
const _duiRatio = 1.586;

/// Guided capture of the DUI (VDI-77): the camera with a frame and tips, one photo per side in [sides].
/// When the last one is taken it calls [onCaptured] with the photos of the sides that were asked for.
class DuiCaptureView extends StatefulWidget {
  const DuiCaptureView({
    super.key,
    required this.camera,
    required this.sides,
    required this.onCaptured,
  });

  final DocumentCamera camera;

  /// Sides to photograph, in order: both the first time, or only the ones to take again.
  final List<DuiSide> sides;
  final ValueChanged<Map<DuiSide, Uint8List>> onCaptured;

  @override
  State<DuiCaptureView> createState() => _DuiCaptureViewState();
}

class _DuiCaptureViewState extends State<DuiCaptureView> {
  CameraSession? _session;
  CameraUnavailable? _unavailable;
  final _photos = <DuiSide, Uint8List>{};
  var _taking = false;
  var _disposed = false;

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
      setState(() => _session = session);
    } on CameraUnavailable catch (e) {
      if (!_disposed) setState(() => _unavailable = e);
    }
  }

  @override
  void dispose() {
    _disposed = true;
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
      }
    } on Exception catch (e) {
      debugPrint('Could not take the photo: $e');
      if (mounted) setState(() => _unavailable = const CameraUnavailable());
    } finally {
      if (mounted) setState(() => _taking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final unavailable = _unavailable;
    if (unavailable != null) {
      return _CameraProblem(permissionDenied: unavailable.permissionDenied, onRetry: _open);
    }
    final session = _session;
    if (session == null) {
      return const ColoredBox(
        color: Colors.black,
        child: Center(child: CircularProgressIndicator(color: AppColors.brand)),
      );
    }
    final front = _side == DuiSide.front;
    return ColoredBox(
      color: Colors.black,
      child: Column(
        children: [
          const SizedBox(height: 12),
          _SideSteps(current: _side),
          Expanded(
            child: Stack(
              alignment: Alignment.center,
              children: [
                Positioned.fill(child: _FullPreview(child: session.preview)),
                const _Frame(),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
            child: Semantics(
              liveRegion: true,
              child: Text(
                front
                    ? 'Coloca el frente de tu DUI dentro del marco'
                    : 'Ahora coloca el reverso de tu DUI dentro del marco',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  height: 1.3,
                ),
              ),
            ),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
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
            padding: EdgeInsets.fromLTRB(
              24,
              20,
              24,
              24 + MediaQuery.paddingOf(context).bottom,
            ),
            child: _Shutter(busy: _taking, onPressed: _take),
          ),
        ],
      ),
    );
  }
}

/// The preview covers the whole area without stretching.
class _FullPreview extends StatelessWidget {
  const _FullPreview({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return ClipRect(
      child: FittedBox(
        fit: BoxFit.cover,
        child: SizedBox(width: 300, height: 400, child: child),
      ),
    );
  }
}

/// White frame with the corners marked, where the DUI goes.
class _Frame extends StatelessWidget {
  const _Frame();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: AspectRatio(
        aspectRatio: _duiRatio,
        child: DecoratedBox(
          decoration: BoxDecoration(
            border: Border.all(color: Colors.white, width: 3),
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    );
  }
}

/// "1 · Frente  2 · Reverso", with the current one highlighted.
class _SideSteps extends StatelessWidget {
  const _SideSteps({required this.current});

  final DuiSide current;

  @override
  Widget build(BuildContext context) {
    Widget chip(String text, bool active) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: ShapeDecoration(
        color: active ? AppColors.brand : Colors.white24,
        shape: const StadiumBorder(),
      ),
      child: Text(
        text,
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w700,
          color: active ? AppColors.ink : Colors.white,
        ),
      ),
    );
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        chip('1 · Frente', current == DuiSide.front),
        const SizedBox(width: 8),
        chip('2 · Reverso', current == DuiSide.back),
      ],
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
        color: Colors.white12,
        shape: const StadiumBorder(),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18, color: AppColors.brand),
          const SizedBox(width: 6),
          Text(text, style: const TextStyle(color: Colors.white, fontSize: 14)),
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
          width: 76,
          height: 76,
          padding: const EdgeInsets.all(5),
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 4),
          ),
          child: DecoratedBox(
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: busy ? Colors.white54 : Colors.white,
            ),
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
      padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const ScreenTitle('Necesitamos tu cámara'),
          const SizedBox(height: 12),
          Text(
            permissionDenied
                ? 'Para fotografiar tu DUI necesitamos usar la cámara. Permítelo en los ajustes de tu teléfono y vuelve aquí.'
                : 'No pudimos abrir la cámara. Cierra otras aplicaciones que la estén usando e inténtalo de nuevo.',
            style: AppText.body,
          ),
          const SizedBox(height: 24),
          PrimaryButton(text: 'Intentar de nuevo', onPressed: onRetry),
        ],
      ),
    );
  }
}
