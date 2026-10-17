import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/identity/camera_geometry.dart';

void main() {
  group('portraitSize', () {
    test('the camera reports its preview in landscape: the screen needs it in portrait', () {
      expect(portraitSize(const Size(1280, 720)), const Size(720, 1280));
      expect(portraitSize(const Size(1920, 1080)), const Size(1080, 1920));
      expect(portraitSize(const Size(640, 480)), const Size(480, 640));
    });

    test('a preview that already comes in portrait is left as it is', () {
      expect(portraitSize(const Size(720, 1280)), const Size(720, 1280));
    });

    test('a square preview stays square', () {
      expect(portraitSize(const Size(500, 500)), const Size(500, 500));
    });
  });

  group('initialZoom', () {
    test('the DUI is photographed at 2x: the phone can stay far enough to focus and still fill the frame', () {
      expect(documentZoom, 2.0);
      expect(initialZoom(1, 10), 2.0);
    });

    test('it never goes past what the camera can do', () {
      expect(initialZoom(1, 1.5), 1.5);
      expect(initialZoom(1, 1), 1.0);
    });

    test('it never goes under the minimum of the camera', () {
      expect(initialZoom(3, 6), 3.0);
    });
  });

  group('CoverPreview', () {
    Future<Rect> rectOf(WidgetTester tester, {required Size native, required Size area}) async {
      await tester.pumpWidget(Directionality(
        textDirection: TextDirection.ltr,
        child: Center(
          child: SizedBox(
            width: area.width,
            height: area.height,
            child: CoverPreview(size: native, child: const SizedBox.expand(key: Key('image'))),
          ),
        ),
      ));
      return tester.getRect(find.byKey(const Key('image')));
    }

    testWidgets('keeps the proportion of the camera: nothing is squashed or stretched', (tester) async {
      // A 16:9 camera in portrait is 9:16, whatever the shape of the area where it is shown.
      for (final area in const [Size(400, 300), Size(300, 700), Size(390, 520)]) {
        final rect = await rectOf(tester, native: const Size(720, 1280), area: area);
        expect(rect.width / rect.height, closeTo(720 / 1280, 0.001), reason: 'area $area');
      }
    });

    testWidgets('covers the whole area, cropping what does not fit', (tester) async {
      const area = Size(400, 300);
      final rect = await rectOf(tester, native: const Size(720, 1280), area: area);
      expect(rect.width, greaterThanOrEqualTo(area.width - 0.01));
      expect(rect.height, greaterThanOrEqualTo(area.height - 0.01));
      expect(find.byType(ClipRect), findsWidgets);
    });

    testWidgets('a 4:3 camera is also shown with its own proportion', (tester) async {
      final rect = await rectOf(tester, native: const Size(480, 640), area: const Size(390, 520));
      expect(rect.width / rect.height, closeTo(480 / 640, 0.001));
    });
  });
}
