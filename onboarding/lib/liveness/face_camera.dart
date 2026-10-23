import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';

import '../identity/camera_geometry.dart';
import '../identity/document_camera.dart';
import '../identity/frame_quality.dart';

/// One face the phone found in the live image of the selfie (VDI-116).
class DetectedFace {
  const DetectedFace({required this.box, this.yaw, this.leftEyeOpen, this.rightEyeOpen, this.trackingId});

  /// Where the face is, from 0 to 1 over the upright image: (0, 0) is its top left corner.
  final Rect box;

  /// How much the head is turned to a side, in degrees; about 0 when looking at the phone. `null` if unknown.
  final double? yaw;

  /// Chance, from 0 to 1, that each eye is open. `null` if unknown.
  final double? leftEyeOpen;
  final double? rightEyeOpen;

  /// The same number while the phone follows the same face without losing it.
  final int? trackingId;
}

/// What the phone sees in one live frame: the faces and whether there is light.
class FaceFrame {
  const FaceFrame({this.faces = const [], this.light = FrameHint.analyzing});

  final List<DetectedFace> faces;

  /// Light of the scene; only the dark hints matter here, the head is expected to move.
  final FrameHint light;
}

/// An open front camera: live preview, the faces of the live image and the photo. Tests pass a fake one.
abstract class FaceSession {
  Widget get preview;

  /// A few times per second while the camera is open. It can stay silent if the phone cannot analyze the image.
  Stream<FaceFrame> get frames;

  /// Takes the selfie and returns its JPEG bytes.
  Future<Uint8List> takePhoto();

  Future<void> dispose();
}

/// Opens the front camera for the selfie with the liveness check (VDI-116).
abstract class FaceCamera {
  /// Throws [CameraUnavailable] if there is no camera or the permission was denied.
  Future<FaceSession> open();
}

/// Front camera of the phone through the `camera` plugin, with the faces found on the phone by ML Kit. Nothing of the
/// live image leaves the phone: only the selfie taken at the end goes to the API.
class PlatformFaceCamera implements FaceCamera {
  const PlatformFaceCamera();

  static const _deniedCodes = {'CameraAccessDenied', 'CameraAccessDeniedWithoutPrompt', 'CameraAccessRestricted'};

  @override
  Future<FaceSession> open() async {
    final CameraController controller;
    final CameraDescription camera;
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) throw const CameraUnavailable();
      camera = cameras.firstWhere((c) => c.lensDirection == CameraLensDirection.front, orElse: () => cameras.first);
      // ML Kit reads NV21 on Android and BGRA on iOS. A face does not need the resolution of a document.
      controller = CameraController(
        camera,
        ResolutionPreset.high,
        enableAudio: false,
        imageFormatGroup: defaultTargetPlatform == TargetPlatform.android ? ImageFormatGroup.nv21 : ImageFormatGroup.bgra8888,
      );
      await controller.initialize();
      try {
        await controller.setFlashMode(FlashMode.off);
      } on CameraException catch (e) {
        debugPrint('Could not turn the flash off: ${e.code}');
      }
    } on CameraException catch (e) {
      debugPrint('Could not open the front camera: ${e.code}');
      throw CameraUnavailable(permissionDenied: _deniedCodes.contains(e.code));
    }
    final session = _PlatformFaceSession(controller, camera);
    await session.startAnalysis();
    return session;
  }
}

class _PlatformFaceSession implements FaceSession {
  _PlatformFaceSession(this._controller, this._camera);

  final CameraController _controller;
  final CameraDescription _camera;
  final _frames = StreamController<FaceFrame>.broadcast();
  final _light = FrameQualityMonitor();
  final _detector = FaceDetector(
    options: FaceDetectorOptions(
      enableClassification: true, // eyes open or closed, for the blink
      enableTracking: true, // the same face from start to end
      minFaceSize: 0.15,
    ),
  );
  var _lastFrame = DateTime.fromMillisecondsSinceEpoch(0);
  var _detecting = false;

  /// Often enough to catch a blink, and one frame at a time so a slow phone does not pile them up.
  static const _frameInterval = Duration(milliseconds: 120);

  Future<void> startAnalysis() async {
    if (!_controller.supportsImageStreaming()) return;
    try {
      await _controller.startImageStream(_onFrame);
    } on Exception catch (e) {
      debugPrint('Could not analyze the live image: $e');
    }
  }

  Future<void> _onFrame(CameraImage image) async {
    final now = DateTime.now();
    if (_detecting || now.difference(_lastFrame) < _frameInterval || _frames.isClosed) return;
    _lastFrame = now;
    final input = _inputImage(image);
    if (input == null) return;
    _detecting = true;
    try {
      final faces = await _detector.processImage(input);
      if (_frames.isClosed) return;
      final upright = input.metadata!.rotation.rawValue % 180 == 0
          ? Size(image.width.toDouble(), image.height.toDouble())
          : Size(image.height.toDouble(), image.width.toDouble());
      _frames.add(FaceFrame(
        faces: [
          for (final f in faces)
            DetectedFace(
              box: Rect.fromLTRB(
                f.boundingBox.left / upright.width,
                f.boundingBox.top / upright.height,
                f.boundingBox.right / upright.width,
                f.boundingBox.bottom / upright.height,
              ),
              yaw: f.headEulerAngleY,
              leftEyeOpen: f.leftEyeOpenProbability,
              rightEyeOpen: f.rightEyeOpenProbability,
              trackingId: f.trackingId,
            ),
        ],
        light: _lightOf(image),
      ));
    } on PlatformException catch (e) {
      debugPrint('Could not look for faces: ${e.code}');
    } finally {
      _detecting = false;
    }
  }

  FrameHint _lightOf(CameraImage image) {
    final plane = image.planes.first;
    // The NV21 buffer starts with the Y plane, so its first rows are the luma.
    final grid = image.format.group == ImageFormatGroup.bgra8888
        ? LumaGrid.fromBgra(plane.bytes, width: image.width, height: image.height, rowStride: plane.bytesPerRow)
        : LumaGrid.fromYPlane(plane.bytes, width: image.width, height: image.height, rowStride: plane.bytesPerRow);
    final ev = sceneEv(aperture: image.lensAperture, exposureNanoseconds: image.sensorExposureTime, iso: image.sensorSensitivity);
    return _light.add(grid, sceneEv: ev);
  }

  static const _deviceRotation = {
    DeviceOrientation.portraitUp: 0,
    DeviceOrientation.landscapeLeft: 90,
    DeviceOrientation.portraitDown: 180,
    DeviceOrientation.landscapeRight: 270,
  };

  /// The frame as ML Kit wants it, turned upright. `null` for a format it cannot read.
  InputImage? _inputImage(CameraImage image) {
    final InputImageRotation? rotation;
    if (defaultTargetPlatform == TargetPlatform.android) {
      final device = _deviceRotation[_controller.value.deviceOrientation] ?? 0;
      rotation = InputImageRotationValue.fromRawValue((_camera.sensorOrientation + device) % 360);
    } else {
      rotation = InputImageRotationValue.fromRawValue(_camera.sensorOrientation);
    }
    final format = InputImageFormatValue.fromRawValue(image.format.raw as int);
    final expected = defaultTargetPlatform == TargetPlatform.android ? InputImageFormat.nv21 : InputImageFormat.bgra8888;
    if (rotation == null || format != expected || image.planes.length != 1) return null;
    final plane = image.planes.first;
    return InputImage.fromBytes(
      bytes: plane.bytes,
      metadata: InputImageMetadata(
        size: Size(image.width.toDouble(), image.height.toDouble()),
        rotation: rotation,
        format: format!,
        bytesPerRow: plane.bytesPerRow,
      ),
    );
  }

  @override
  Widget get preview {
    final size = _controller.value.previewSize;
    final camera = CameraPreview(_controller);
    return size == null ? camera : CoverPreview(size: portraitSize(size), child: camera);
  }

  @override
  Stream<FaceFrame> get frames => _frames.stream;

  @override
  Future<Uint8List> takePhoto() async => (await _controller.takePicture()).readAsBytes();

  @override
  Future<void> dispose() async {
    await _frames.close();
    if (_controller.value.isStreamingImages) {
      try {
        await _controller.stopImageStream();
      } on Exception catch (e) {
        debugPrint('Could not stop the image stream: $e');
      }
    }
    await _controller.dispose();
    await _detector.close();
  }
}
