import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

/// The camera could not be opened. [permissionDenied] tells the person to allow it in the settings.
class CameraUnavailable implements Exception {
  const CameraUnavailable({this.permissionDenied = false});

  final bool permissionDenied;

  @override
  String toString() => 'CameraUnavailable(permissionDenied: $permissionDenied)';
}

/// An open camera: live preview and photo. Tests pass a fake one.
abstract class CameraSession {
  /// Live image of the camera, to be shown behind the frame.
  Widget get preview;

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

  static const _deniedCodes = {
    'CameraAccessDenied',
    'CameraAccessDeniedWithoutPrompt',
    'CameraAccessRestricted',
  };

  @override
  Future<CameraSession> open() async {
    final CameraController controller;
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) throw const CameraUnavailable();
      final rear = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );
      // No audio: the DUI is a photo, so the microphone is never requested.
      controller = CameraController(
        rear,
        ResolutionPreset.high,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );
      await controller.initialize();
    } on CameraException catch (e) {
      debugPrint('Could not open the camera: ${e.code}');
      throw CameraUnavailable(permissionDenied: _deniedCodes.contains(e.code));
    }
    return _PlatformSession(controller);
  }
}

class _PlatformSession implements CameraSession {
  _PlatformSession(this._controller);

  final CameraController _controller;

  @override
  Widget get preview => CameraPreview(_controller);

  @override
  Future<Uint8List> takePhoto() async =>
      (await _controller.takePicture()).readAsBytes();

  @override
  Future<void> dispose() => _controller.dispose();
}
