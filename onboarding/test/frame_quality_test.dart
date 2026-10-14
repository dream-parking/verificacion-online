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
      expect(FrameHint.moving.message, 'Mueve menos el teléfono.');
      expect(FrameHint.analyzing.message, 'Mantén el teléfono quieto');
      expect(FrameHint.values.map((h) => h.message).toSet(), hasLength(FrameHint.values.length));
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

    test('a dark scene is reported as too dark even if it is steady', () {
      final m = FrameQualityMonitor();
      final dark = flat(20);
      m.add(dark);
      m.add(dark);
      expect(m.add(dark), FrameHint.tooDark);
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
      for (var i = 0; i < 5; i++) {
        m.add(flat(i.isEven ? 10 : 40));
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
}
