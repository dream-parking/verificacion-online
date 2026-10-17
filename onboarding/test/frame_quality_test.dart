import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/identity/frame_quality.dart';

/// A grid where every cell has the same luma.
LumaGrid flat(int luma) => LumaGrid(
      LumaGrid.gridColumns,
      LumaGrid.gridRows,
      Uint8List(LumaGrid.gridColumns * LumaGrid.gridRows)..fillRange(0, LumaGrid.gridColumns * LumaGrid.gridRows, luma),
    );

/// A grid with vertical stripes: [shift] moves them sideways, like a phone that moved.
LumaGrid stripes({int shift = 0, int light = 190, int dark = 90}) {
  final cells = Uint8List(LumaGrid.gridColumns * LumaGrid.gridRows);
  for (var y = 0; y < LumaGrid.gridRows; y++) {
    for (var x = 0; x < LumaGrid.gridColumns; x++) {
      cells[y * LumaGrid.gridColumns + x] = ((x + shift) ~/ 4).isEven ? light : dark;
    }
  }
  return LumaGrid(LumaGrid.gridColumns, LumaGrid.gridRows, cells);
}

void main() {
  group('hints', () {
    test('each state has its own message, the good one first reassures', () {
      expect(FrameHint.good.message, 'Bien, mantén el teléfono quieto');
      expect(FrameHint.tooDark.message, 'Hay poca luz. Busca un lugar más iluminado.');
      expect(FrameHint.veryDark.message, 'Está demasiado oscuro para tomar la foto. Busca un lugar con más luz.');
      expect(FrameHint.moving.message, 'Mueve menos el teléfono.');
      expect(FrameHint.analyzing.message, 'Mantén el teléfono quieto');
      expect(FrameHint.values.map((h) => h.message).toSet(), hasLength(FrameHint.values.length));
    });

    test('only a very dark image blocks the shutter: the other hints warn but never stop the photo', () {
      expect(FrameHint.veryDark.blocksShutter, isTrue);
      for (final h in FrameHint.values.where((h) => h != FrameHint.veryDark)) {
        expect(h.blocksShutter, isFalse, reason: '$h');
      }
    });

    test('only the good state is a confirmation', () {
      expect(FrameHint.good.isGood, isTrue);
      for (final h in FrameHint.values.where((h) => h != FrameHint.good)) {
        expect(h.isGood, isFalse, reason: '$h');
      }
    });
  });

  group('FrameQualityMonitor', () {
    test('starts analyzing and says nothing about the very first frame', () {
      final m = FrameQualityMonitor();
      expect(m.hint, FrameHint.analyzing);
      expect(m.add(stripes()), FrameHint.analyzing, reason: 'there is nothing to compare with yet');
    });

    test('a steady, lit scene becomes good after two consistent frames', () {
      final m = FrameQualityMonitor();
      m.add(stripes());
      expect(m.add(stripes()), FrameHint.analyzing, reason: 'one steady frame is not enough to be sure');
      expect(m.add(stripes()), FrameHint.good);
    });

    test('a dim scene is reported as too dark even if it is steady, but it is not blocked', () {
      final m = FrameQualityMonitor();
      final dim = flat(50);
      m.add(dim);
      m.add(dim);
      expect(m.add(dim), FrameHint.tooDark);
      expect(m.hint.blocksShutter, isFalse);
    });

    test('a scene that is almost black is very dark: the photo cannot be taken', () {
      final m = FrameQualityMonitor();
      final black = flat(10);
      m.add(black);
      m.add(black);
      expect(m.add(black), FrameHint.veryDark);
      expect(m.hint.blocksShutter, isTrue);
    });

    test('the limit between dim and very dark is where the monitor says', () {
      expect(FrameQualityMonitor().veryDarkMean, lessThan(FrameQualityMonitor().darkMean));
      for (final (luma, expected) in [(44, FrameHint.veryDark), (46, FrameHint.tooDark), (54, FrameHint.tooDark), (56, FrameHint.good)]) {
        final m = FrameQualityMonitor();
        m.add(flat(luma));
        m.add(flat(luma));
        expect(m.add(flat(luma)), expected, reason: 'luma $luma');
      }
    });

    test('the limit was set with an iPhone: a dim room reads 40 to 50 and counts as very dark', () {
      expect(FrameQualityMonitor().veryDarkMean, 45);
      final m = FrameQualityMonitor();
      for (final luma in [41, 43, 40, 44]) {
        m.add(flat(luma));
      }
      expect(m.hint, FrameHint.veryDark);
    });

    test('a scene hovering around the limit does not flicker: it stays blocked until it is clearly lit', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(flat(40));
      }
      expect(m.hint, FrameHint.veryDark);
      final seen = <FrameHint>{};
      for (final luma in [50, 40, 49, 43, 51, 42, 50]) {
        seen.add(m.add(flat(luma)));
      }
      expect(seen, {FrameHint.veryDark}, reason: 'it never let go in the zone of the limit');
    });

    test('it lets go once the light is clearly above the limit', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(flat(40));
      }
      expect(m.hint, FrameHint.veryDark);
      for (var i = 0; i < 3; i++) {
        m.add(flat(54));
      }
      expect(m.hint, FrameHint.tooDark, reason: 'lit enough to take the photo, still dim');
      for (var i = 0; i < 3; i++) {
        m.add(flat(70));
      }
      expect(m.hint, FrameHint.good);
    });

    test('the release margin only applies once it is blocked', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(flat(50));
      }
      expect(m.hint, FrameHint.tooDark, reason: '50 is above the limit when it was not blocked before');
    });

    test('it recovers once there is light again', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 3; i++) {
        m.add(flat(10));
      }
      expect(m.hint, FrameHint.veryDark);
      for (var i = 0; i < 4; i++) {
        m.add(flat(150));
      }
      expect(m.hint, FrameHint.good);
    });

    test('a scene that changes from frame to frame is reported as moving', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(stripes(shift: i * 4));
      }
      expect(m.hint, FrameHint.moving);
    });

    test('too dark wins over moving: first fix the light', () {
      final m = FrameQualityMonitor();
      // Dim (mean 50) and changing a lot from one frame to the next.
      for (var i = 0; i < 5; i++) {
        m.add(stripes(shift: i * 4, light: 80, dark: 20));
      }
      expect(m.hint, FrameHint.tooDark);
    });

    test('one odd frame does not flip the message, so it does not flicker', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(stripes());
      }
      expect(m.hint, FrameHint.good);
      m.add(stripes(shift: 4)); // one jolt
      expect(m.hint, FrameHint.good);
      m.add(stripes(shift: 4));
      m.add(stripes(shift: 4));
      expect(m.hint, FrameHint.good, reason: 'the scene is steady again');
    });

    test('it goes back to good once the phone is still again', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(stripes(shift: i * 4));
      }
      expect(m.hint, FrameHint.moving);
      for (var i = 0; i < 4; i++) {
        m.add(stripes(shift: 16));
      }
      expect(m.hint, FrameHint.good);
    });

    test('a bright flat scene is steady and lit', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 3; i++) {
        m.add(flat(150));
      }
      expect(m.hint, FrameHint.good);
    });

    test('reset forgets everything', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(stripes());
      }
      m.reset();
      expect(m.hint, FrameHint.analyzing);
    });
  });

  group('LumaGrid', () {
    test('takes the center of the Y plane and ignores the padding of each row', () {
      const width = 100, height = 80, stride = 128;
      final plane = Uint8List(stride * height);
      for (var y = 0; y < height; y++) {
        for (var x = 0; x < stride; x++) {
          plane[y * stride + x] = x < width ? 10 : 255; // padding is bright and must not count
        }
      }
      final grid = LumaGrid.fromYPlane(plane, width: width, height: height, rowStride: stride);
      expect(grid.columns, LumaGrid.gridColumns);
      expect(grid.rows, LumaGrid.gridRows);
      expect(grid.mean, 10);
    });

    test('looks only at the middle, where the DUI goes: a bright border outside does not count', () {
      const width = 200, height = 200;
      final plane = Uint8List(width * height)..fillRange(0, width * height, 255);
      for (var y = 50; y < 150; y++) {
        for (var x = 20; x < 180; x++) {
          plane[y * width + x] = 30;
        }
      }
      final grid = LumaGrid.fromYPlane(plane, width: width, height: height, rowStride: width);
      expect(grid.mean, closeTo(30, 1));
    });

    test('computes the luma of BGRA pixels', () {
      const width = 40, height = 40;
      final pixels = Uint8List(width * height * 4);
      for (var i = 0; i < width * height; i++) {
        pixels[i * 4] = 0; // blue
        pixels[i * 4 + 1] = 200; // green
        pixels[i * 4 + 2] = 0; // red
        pixels[i * 4 + 3] = 255;
      }
      final grid = LumaGrid.fromBgra(pixels, width: width, height: height, rowStride: width * 4);
      expect(grid.mean, closeTo(0.587 * 200, 2));
    });

    test('a frame that is too small for the grid still gives a grid', () {
      final grid = LumaGrid.fromYPlane(Uint8List.fromList([100, 100, 100, 100]), width: 2, height: 2, rowStride: 2);
      expect(grid.mean, 100);
    });

    test('the difference between two grids is the mean absolute change', () {
      expect(flat(100).differenceTo(flat(100)), 0);
      expect(flat(100).differenceTo(flat(130)), 30);
      expect(flat(130).differenceTo(flat(100)), 30);
    });
  });

  group('scene light from the exposure of the camera', () {
    // The three scenes of the iPhone 15 Pro Max (aperture f/1.78): the brightness of the image hides the dark because the
    // phone raises the ISO, but the exposure tells the truth.
    const aperture = 1.78;

    test('the light is computed from aperture, exposure time and ISO', () {
      expect(sceneEv(aperture: aperture, exposureNanoseconds: 13000000, iso: 67), closeTo(8.5, 0.1));
      expect(sceneEv(aperture: aperture, exposureNanoseconds: 21300000, iso: 320), closeTo(5.5, 0.1));
      expect(sceneEv(aperture: aperture, exposureNanoseconds: 21300000, iso: 1096), closeTo(3.8, 0.1));
    });

    test('a darker scene always gives a lower number', () {
      final bright = sceneEv(aperture: aperture, exposureNanoseconds: 13000000, iso: 67)!;
      final dim = sceneEv(aperture: aperture, exposureNanoseconds: 21300000, iso: 320)!;
      final dark = sceneEv(aperture: aperture, exposureNanoseconds: 21300000, iso: 1096)!;
      expect(bright, greaterThan(dim));
      expect(dim, greaterThan(dark));
    });

    test('without the data, or with impossible values, there is no number', () {
      expect(sceneEv(aperture: null, exposureNanoseconds: 1000000, iso: 100), isNull);
      expect(sceneEv(aperture: aperture, exposureNanoseconds: null, iso: 100), isNull);
      expect(sceneEv(aperture: aperture, exposureNanoseconds: 1000000, iso: null), isNull);
      expect(sceneEv(aperture: aperture, exposureNanoseconds: 0, iso: 100), isNull);
      expect(sceneEv(aperture: aperture, exposureNanoseconds: 1000000, iso: 0), isNull);
      expect(sceneEv(aperture: 0, exposureNanoseconds: 1000000, iso: 100), isNull);
      expect(sceneEv(aperture: aperture, exposureNanoseconds: -5, iso: 100), isNull);
    });

    test('the limits are the ones measured: block under 4.5, warn under 6.5', () {
      final m = FrameQualityMonitor();
      expect(m.veryDarkEv, 4.5);
      expect(m.darkEv, 6.5);
      expect(m.veryDarkEv, lessThan(m.darkEv));
    });

    FrameHint after(List<double> evs, {int luma = 130, FrameQualityMonitor? monitor}) {
      final m = monitor ?? FrameQualityMonitor();
      for (final ev in evs) {
        m.add(flat(luma), sceneEv: ev);
      }
      return m.hint;
    }

    test('the dark scene is blocked even though the image is bright: the phone raised the ISO', () {
      expect(after([3.8, 3.8, 3.8], luma: 125), FrameHint.veryDark);
    });

    test('the dim scene only warns, and the bright one is good', () {
      expect(after([5.5, 5.5, 5.5], luma: 133), FrameHint.tooDark);
      expect(after([8.5, 8.5, 8.5], luma: 152), FrameHint.good);
    });

    test('the limits are exact: 4.4 blocks, 4.6 warns, 6.4 warns, 6.6 is good', () {
      expect(after([4.4, 4.4, 4.4]), FrameHint.veryDark);
      expect(after([4.6, 4.6, 4.6]), FrameHint.tooDark);
      expect(after([6.4, 6.4, 6.4]), FrameHint.tooDark);
      expect(after([6.6, 6.6, 6.6]), FrameHint.good);
    });

    test('once blocked it needs a little more light to let go, so it does not flicker', () {
      final m = FrameQualityMonitor();
      expect(after([3.8, 3.8, 3.8], monitor: m), FrameHint.veryDark);
      final seen = <FrameHint>{};
      for (final ev in [4.7, 4.3, 4.8, 4.4, 4.9]) {
        seen.add(m.add(flat(130), sceneEv: ev));
      }
      expect(seen, {FrameHint.veryDark}, reason: 'it hovers around the limit and stays blocked');
      expect(after([5.6, 5.6, 5.6], monitor: m), FrameHint.tooDark);
    });

    test('with the exposure the brightness of the image is not used for the light', () {
      expect(after([8.5, 8.5, 8.5], luma: 10), FrameHint.good, reason: 'a dark image but a bright scene');
    });

    test('without the exposure it falls back to the brightness of the image', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 3; i++) {
        m.add(flat(10));
      }
      expect(m.hint, FrameHint.veryDark);
    });
  });

  group('movement', () {
    test('a hand-held phone with the 2x zoom does not trigger «Mueve menos» for ordinary jitter', () {
      expect(FrameQualityMonitor().movingDifference, 18);
      final m = FrameQualityMonitor();
      m.add(flat(120));
      for (var i = 0; i < 5; i++) {
        m.add(flat(i.isEven ? 134 : 120)); // a change of 14 between frames
      }
      expect(m.hint, FrameHint.good);
    });

    test('a real movement is still reported', () {
      final m = FrameQualityMonitor();
      for (var i = 0; i < 4; i++) {
        m.add(stripes(shift: i * 4));
      }
      expect(m.hint, FrameHint.moving);
    });
  });
}
