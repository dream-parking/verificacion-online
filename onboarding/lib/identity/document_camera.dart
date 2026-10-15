import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'camera_geometry.dart';
import 'frame_quality.dart';

/// The camera could not be opened. [permissionDenied] tells the person to allow it in the settings.
class CameraUnavailable implements Exception {
  const CameraUnavailable({this.permissionDenied = false});

  final bool permissionDenied;

  @override
  String toString() => 'CameraUnavailable(permissionDenied: $permissionDenied)';
}

/// An open camera: live preview, what the live image says, and photo. Tests pass a fake one.
abstract class CameraSession {
  /// Live image of the camera, to be shown behind the frame.
  Widget get preview;

  /// Focuses (and measures the light) at [point], given from 0 to 1 over the preview: (0, 0) is its top left corner.
  /// It does nothing if the camera cannot.
  Future<void> focusAt(Offset point);

  /// Light and steadiness of the live image, a few times per second while the camera is open. It can stay silent
  /// if the phone cannot analyze the image: the photo can be taken anyway.
  Stream<FrameHint> get hints;

  /// Takes a photo and returns its JPEG bytes.
  Future<Uint8List> takePhoto();

  Future<void> dispose();
}

/// Opens the camera to photograph the DUI (VDI-77).
abstract class DocumentCamera {
  /// Throws [CameraUnavailable] if there is no camera or the permission was denied.
  Future<CameraSession> open();
}

/// Rear camera of the phone, through the `camera` plugin.
class PlatformDocumentCamera implements DocumentCamera {
  const PlatformDocumentCamera();

  static const _deniedCodes = {'CameraAccessDenied', 'CameraAccessDeniedWithoutPrompt', 'CameraAccessRestricted'};

  @override
  Future<CameraSession> open() async {
    final CameraController controller;
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) throw const CameraUnavailable();
      final rear = cameras.firstWhere((c) => c.lensDirection == CameraLensDirection.back, orElse: () => cameras.first);
      // No audio: the DUI is a photo, so the microphone is never requested. The live frames come as YUV on
      // Android and BGRA on iOS, where the brightness is cheap to read.
      controller = CameraController(
        rear,
        ResolutionPreset.veryHigh,
        enableAudio: false,
        imageFormatGroup: defaultTargetPlatform == TargetPlatform.android ? ImageFormatGroup.yuv420 : ImageFormatGroup.bgra8888,
      );
      await controller.initialize();
      await _prepare(controller);
    } on CameraException catch (e) {
      debugPrint('Could not open the camera: ${e.code}');
      throw CameraUnavailable(permissionDenied: _deniedCodes.contains(e.code));
    }
    final session = _PlatformSession(controller);
    await session.startAnalysis();
    return session;
  }

  /// Zoom and focus for photographing a document. Whatever the phone cannot do is skipped: the camera still works.
  static Future<void> _prepare(CameraController controller) async {
    try {
      final zoom = initialZoom(await controller.getMinZoomLevel(), await controller.getMaxZoomLevel());
      await controller.setZoomLevel(zoom);
      await controller.setFocusMode(FocusMode.auto);
      await controller.setFocusPoint(const Offset(0.5, 0.5));
    } on CameraException catch (e) {
      debugPrint('Could not set the zoom and the focus: ${e.code}');
    }
  }
}

class _PlatformSession implements CameraSession {
  _PlatformSession(this._controller);

  final CameraController _controller;
  final _hints = StreamController<FrameHint>.broadcast();
  final _monitor = FrameQualityMonitor();
  var _lastFrame = DateTime.fromMillisecondsSinceEpoch(0);

  /// The live frames are looked at a few times per second: enough to follow the person and cheap for the phone.
  static const _frameInterval = Duration(milliseconds: 250);

  /// Starts reading the live frames. If the phone cannot, there are no hints and the photo works the same.
  Future<void> startAnalysis() async {
    try {
      await _controller.startImageStream(_onFrame);
    } on Exception catch (e) {
      debugPrint('Could not analyze the live image: $e');
    }
  }

  void _onFrame(CameraImage image) {
    final now = DateTime.now();
    if (now.difference(_lastFrame) < _frameInterval || _hints.isClosed) return;
    _lastFrame = now;
    final plane = image.planes.first;
    final grid = image.format.group == ImageFormatGroup.bgra8888
        ? LumaGrid.fromBgra(plane.bytes, width: image.width, height: image.height, rowStride: plane.bytesPerRow)
        : LumaGrid.fromYPlane(plane.bytes, width: image.width, height: image.height, rowStride: plane.bytesPerRow);
    _hints.add(_monitor.add(grid));
  }

  @override
  Widget get preview {
    final size = _controller.value.previewSize;
    final camera = CameraPreview(_controller);
    return size == null ? camera : CoverPreview(size: portraitSize(size), child: camera);
  }

  @override
  Future<void> focusAt(Offset point) async {
    try {
      await _controller.setFocusPoint(point);
      await _controller.setExposurePoint(point);
    } on CameraException catch (e) {
      debugPrint('Could not focus: ${e.code}');
    }
  }

  @override
  Stream<FrameHint> get hints => _hints.stream;

  @override
  Future<Uint8List> takePhoto() async => (await _controller.takePicture()).readAsBytes();

  @override
  Future<void> dispose() async {
    await _hints.close();
    if (_controller.value.isStreamingImages) {
      try {
        await _controller.stopImageStream();
      } on Exception catch (e) {
        debugPrint('Could not stop the image stream: $e');
      }
    }
    await _controller.dispose();
  }
}
