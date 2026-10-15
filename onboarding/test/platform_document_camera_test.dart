import 'dart:async';
import 'dart:math';
import 'dart:typed_data';

import 'package:camera_platform_interface/camera_platform_interface.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'package:onboarding/identity/camera_geometry.dart';
import 'package:onboarding/identity/document_camera.dart';
import 'package:onboarding/identity/frame_quality.dart';

/// A camera plugin that records what the app asks of it, so the setup of the camera is tested without a phone.
class FakeCameraPlatform extends CameraPlatform with MockPlatformInterfaceMixin {
  FakeCameraPlatform({
    this.cameras = const [
      CameraDescription(name: 'front', lensDirection: CameraLensDirection.front, sensorOrientation: 270),
      CameraDescription(name: 'back', lensDirection: CameraLensDirection.back, sensorOrientation: 90),
    ],
    this.maxZoom = 10,
    Map<String, String>? failing,
    this.streaming = true,
  }) : failing = {...?failing};

  final List<CameraDescription> cameras;
  final double maxZoom;

  /// Whether the phone can give the live frames.
  final bool streaming;

  /// Calls that throw a [CameraException] with that code: `{'setFlashMode': 'x'}`.
  final Map<String, String> failing;

  final calls = <String>[];
  MediaSettings? settings;
  CameraDescription? opened;
  ImageFormatGroup? format;
  final frames = StreamController<CameraImageData>.broadcast();
  var streamListeners = 0;

  void _record(String call) {
    calls.add(call);
    final code = failing[call.split('(').first];
    if (code != null) throw CameraException(code, 'failed on purpose');
  }

  @override
  bool supportsImageStreaming() => streaming;

  @override
  Future<List<CameraDescription>> availableCameras() async => cameras;

  @override
  Future<int> createCameraWithSettings(CameraDescription description, MediaSettings settings) async {
    _record('createCamera');
    opened = description;
    this.settings = settings;
    return 7;
  }

  @override
  Future<void> initializeCamera(int cameraId, {ImageFormatGroup imageFormatGroup = ImageFormatGroup.unknown}) async {
    format = imageFormatGroup;
  }

  @override
  Stream<CameraInitializedEvent> onCameraInitialized(int cameraId) => Stream.value(
        CameraInitializedEvent(cameraId, 1280, 720, ExposureMode.auto, true, FocusMode.auto, true),
      );

  @override
  Stream<CameraErrorEvent> onCameraError(int cameraId) => StreamController<CameraErrorEvent>.broadcast().stream; // no error

  @override
  Stream<DeviceOrientationChangedEvent> onDeviceOrientationChanged() => const Stream.empty();

  @override
  Future<double> getMinZoomLevel(int cameraId) async => 1;

  @override
  Future<double> getMaxZoomLevel(int cameraId) async => maxZoom;

  @override
  Future<void> setZoomLevel(int cameraId, double zoom) async => _record('setZoomLevel($zoom)');

  @override
  Future<void> setFlashMode(int cameraId, FlashMode mode) async => _record('setFlashMode(${mode.name})');

  @override
  Future<void> setFocusMode(int cameraId, FocusMode mode) async => _record('setFocusMode(${mode.name})');

  @override
  Future<void> setFocusPoint(int cameraId, Point<double>? point) async =>
      _record('setFocusPoint(${point?.x}, ${point?.y})');

  @override
  Future<void> setExposurePoint(int cameraId, Point<double>? point) async =>
      _record('setExposurePoint(${point?.x}, ${point?.y})');

  @override
  Widget buildPreview(int cameraId) => const SizedBox(key: Key('platform-preview'));

  @override
  Future<XFile> takePicture(int cameraId) async => XFile.fromData(Uint8List.fromList([0xFF, 0xD8, 9]), name: 'p.jpg');

  @override
  Stream<CameraImageData> onStreamedFrameAvailable(int cameraId, {CameraImageStreamOptions? options}) {
    streamListeners++;
    return frames.stream;
  }

  @override
  Future<void> dispose(int cameraId) async => calls.add('dispose');
}

/// A frame of the live image where every pixel of the Y plane has the same [luma].
CameraImageData frame(int luma, {double? iso, int? exposureNs, double? aperture}) => CameraImageData(
      lensAperture: aperture,
      sensorExposureTime: exposureNs,
      sensorSensitivity: iso,
      format: const CameraImageFormat(ImageFormatGroup.yuv420, raw: 35),
      width: 64,
      height: 48,
      planes: [CameraImagePlane(bytes: Uint8List(64 * 48)..fillRange(0, 64 * 48, luma), bytesPerRow: 64)],
    );

void main() {
  late FakeCameraPlatform platform;

  Future<CameraSession> open([FakeCameraPlatform? fake]) async {
    platform = fake ?? FakeCameraPlatform();
    CameraPlatform.instance = platform;
    return const PlatformDocumentCamera().open();
  }

  group('setup of the camera', () {
    test('uses the rear camera without microphone, at 1080p', () async {
      await open();
      expect(platform.opened!.lensDirection, CameraLensDirection.back);
      expect(platform.settings!.resolutionPreset, ResolutionPreset.veryHigh);
      expect(platform.settings!.enableAudio, isFalse);
    });

    test('the flash is off: in the dark it must not fire and put a glare on the DUI', () async {
      await open();
      expect(platform.calls, contains('setFlashMode(off)'));
      expect(platform.calls, isNot(contains('setFlashMode(auto)')));
      expect(platform.calls, isNot(contains('setFlashMode(always)')));
      expect(platform.calls, isNot(contains('setFlashMode(torch)')));
    });

    test('the zoom is 2x, the focus is automatic and starts in the center', () async {
      await open();
      expect(platform.calls, containsAllInOrder(['setZoomLevel($documentZoom)', 'setFocusMode(auto)', 'setFocusPoint(0.5, 0.5)']));
    });

    test('the zoom never goes past what the camera allows', () async {
      await open(FakeCameraPlatform(maxZoom: 1.5));
      expect(platform.calls, contains('setZoomLevel(1.5)'));
    });

    test('if the flash cannot be set the camera still opens, with its zoom and focus', () async {
      await open(FakeCameraPlatform(failing: {'setFlashMode': 'unsupported'}));
      expect(platform.calls, contains('setZoomLevel($documentZoom)'));
      expect(platform.calls, contains('setFocusMode(auto)'));
    });

    test('if the zoom cannot be set the camera still opens with the flash off and the focus', () async {
      await open(FakeCameraPlatform(failing: {'setZoomLevel': 'unsupported'}));
      expect(platform.calls, contains('setFlashMode(off)'));
      expect(platform.calls, contains('setFocusMode(auto)'));
    });

    test('without permission it says so and asks to allow it', () async {
      CameraPlatform.instance = FakeCameraPlatform(failing: {'createCamera': 'CameraAccessDenied'});
      await expectLater(
        const PlatformDocumentCamera().open(),
        throwsA(isA<CameraUnavailable>().having((e) => e.permissionDenied, 'permissionDenied', isTrue)),
      );
    });

    test('with another error it is unavailable but not a permission problem', () async {
      CameraPlatform.instance = FakeCameraPlatform(failing: {'createCamera': 'SomethingElse'});
      await expectLater(
        const PlatformDocumentCamera().open(),
        throwsA(isA<CameraUnavailable>().having((e) => e.permissionDenied, 'permissionDenied', isFalse)),
      );
    });

    test('a phone without cameras is unavailable', () async {
      CameraPlatform.instance = FakeCameraPlatform(cameras: const []);
      await expectLater(const PlatformDocumentCamera().open(), throwsA(isA<CameraUnavailable>()));
    });

    test('with only a front camera it uses that one', () async {
      await open(FakeCameraPlatform(cameras: const [
        CameraDescription(name: 'front', lensDirection: CameraLensDirection.front, sensorOrientation: 270),
      ]));
      expect(platform.opened!.lensDirection, CameraLensDirection.front);
    });
  });

  group('the session', () {
    test('takes a photo and returns its bytes', () async {
      final session = await open();
      expect(await session.takePhoto(), [0xFF, 0xD8, 9]);
    });

    test('focusing sends the focus and the exposure point', () async {
      final session = await open();
      await session.focusAt(const Offset(0.25, 0.75));
      expect(platform.calls, containsAll(['setFocusPoint(0.25, 0.75)', 'setExposurePoint(0.25, 0.75)']));
    });

    test('focusing on a camera that cannot does not break', () async {
      final session = await open(FakeCameraPlatform());
      platform.failing['setFocusPoint'] = 'unsupported'; // the setup worked; now it fails
      await session.focusAt(const Offset(0.5, 0.5)); // does not throw
    });

    test('the preview keeps the proportion of the camera in portrait', () async {
      final session = await open();
      final preview = session.preview;
      expect(preview, isA<CoverPreview>());
      expect((preview as CoverPreview).size, const Size(720, 1280));
    });

    test('it analyzes the live image and says when it is very dark', () async {
      final session = await open();
      final hints = <FrameHint>[];
      session.hints.listen(hints.add);
      for (var i = 0; i < 4; i++) {
        platform.frames.add(frame(10));
        await Future<void>.delayed(const Duration(milliseconds: 280));
      }
      expect(hints, isNotEmpty);
      expect(hints.last, FrameHint.veryDark);
    });

    test('it says good when there is light and the image is steady', () async {
      final session = await open();
      final hints = <FrameHint>[];
      session.hints.listen(hints.add);
      for (var i = 0; i < 4; i++) {
        platform.frames.add(frame(150));
        await Future<void>.delayed(const Duration(milliseconds: 280));
      }
      expect(hints.last, FrameHint.good);
    });

    Future<List<FrameHint>> hintsFor(CameraImageData Function() make) async {
      final session = await open();
      final hints = <FrameHint>[];
      session.hints.listen(hints.add);
      for (var i = 0; i < 4; i++) {
        platform.frames.add(make());
        await Future<void>.delayed(const Duration(milliseconds: 280));
      }
      return hints;
    }

    test('with the exposure of the iPhone, a dark scene is blocked even if the image looks bright', () async {
      final hints = await hintsFor(() => frame(125, iso: 1096, exposureNs: 21300000, aperture: 1.78));
      expect(hints.last, FrameHint.veryDark);
    });

    test('with the exposure of the iPhone, a lit scene is good', () async {
      final hints = await hintsFor(() => frame(152, iso: 67, exposureNs: 13000000, aperture: 1.78));
      expect(hints.last, FrameHint.good);
    });

    test('with the exposure of the iPhone, a dim scene warns without blocking', () async {
      final hints = await hintsFor(() => frame(133, iso: 320, exposureNs: 21300000, aperture: 1.78));
      expect(hints.last, FrameHint.tooDark);
      expect(hints.last.blocksShutter, isFalse);
    });

    test('frames that come too fast are skipped to spare the phone', () async {
      final session = await open();
      var count = 0;
      session.hints.listen((_) => count++);
      for (var i = 0; i < 20; i++) {
        platform.frames.add(frame(150));
      }
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(count, lessThanOrEqualTo(1));
    });

    test('a phone that cannot give the live frames still works, just without hints', () async {
      final session = await open(FakeCameraPlatform(streaming: false));
      expect(platform.streamListeners, 0);
      expect(await session.takePhoto(), [0xFF, 0xD8, 9]);
      var count = 0;
      session.hints.listen((_) => count++);
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(count, 0);
    });

    test('disposing stops the analysis and closes the camera', () async {
      final session = await open();
      await session.dispose();
      expect(platform.calls, contains('dispose'));
    });
  });
}
