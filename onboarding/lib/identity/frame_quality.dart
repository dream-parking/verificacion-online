import 'dart:typed_data';

/// What the camera tells the person about the live image while they frame the DUI (VDI-77).
enum FrameHint {
  /// Nothing is known yet.
  analyzing('Mantén el teléfono quieto'),

  /// Lit and steady: a good moment to take the photo.
  good('Bien, mantén el teléfono quieto'),

  /// Not enough light to read the DUI.
  tooDark('Hay poca luz. Busca un lugar más iluminado.'),

  /// Almost no light: a photo would come out black, so the shutter is off until there is light.
  veryDark('Está demasiado oscuro para tomar la foto. Busca un lugar con más luz.'),

  /// The image changes from one frame to the next: the phone is moving, so the photo would come out blurred.
  moving('Mueve menos el teléfono.');

  const FrameHint(this.message);

  final String message;

  bool get isGood => this == FrameHint.good;

  /// Only a very dark image stops the photo; every other hint warns and lets the person decide.
  bool get blocksShutter => this == FrameHint.veryDark;
}

/// Brightness of the middle of a frame, in a small fixed grid: cheap to compute on every frame and enough to tell
/// if there is light and if the scene is steady. Only luma is kept, nothing that could identify the DUI.
class LumaGrid {
  LumaGrid(this.columns, this.rows, this.cells);

  /// Size of every grid, so two of them can be compared cell by cell.
  static const gridColumns = 64;
  static const gridRows = 40;

  /// The middle of the frame where the DUI goes: 70 % of the width and 50 % of the height.
  static const _x0 = 0.15, _x1 = 0.85, _y0 = 0.25, _y1 = 0.75;

  final int columns;
  final int rows;

  /// Luma of each cell, 0 to 255, row by row.
  final Uint8List cells;

  /// From the Y plane of a YUV frame (Android). [rowStride] can be wider than [width]: the padding is ignored.
  factory LumaGrid.fromYPlane(Uint8List y, {required int width, required int height, required int rowStride}) {
    return _sample(width, height, (x, row) => y[row * rowStride + x]);
  }

  /// From a BGRA frame (iOS), computing the luma of each sampled pixel.
  factory LumaGrid.fromBgra(Uint8List bgra, {required int width, required int height, required int rowStride}) {
    return _sample(width, height, (x, row) {
      final i = row * rowStride + x * 4;
      return (29 * bgra[i] + 150 * bgra[i + 1] + 77 * bgra[i + 2]) >> 8;
    });
  }

  static LumaGrid _sample(int width, int height, int Function(int x, int row) luma) {
    final cells = Uint8List(gridColumns * gridRows);
    final x0 = width * _x0, spanX = width * (_x1 - _x0);
    final y0 = height * _y0, spanY = height * (_y1 - _y0);
    for (var r = 0; r < gridRows; r++) {
      final row = (y0 + (r + 0.5) * spanY / gridRows).floor().clamp(0, height - 1);
      for (var c = 0; c < gridColumns; c++) {
        final x = (x0 + (c + 0.5) * spanX / gridColumns).floor().clamp(0, width - 1);
        cells[r * gridColumns + c] = luma(x, row);
      }
    }
    return LumaGrid(gridColumns, gridRows, cells);
  }

  /// Mean luma, 0 to 255.
  double get mean {
    var sum = 0;
    for (final v in cells) {
      sum += v;
    }
    return sum / cells.length;
  }

  /// Mean absolute change per cell with respect to [other], 0 to 255.
  double differenceTo(LumaGrid other) {
    var sum = 0;
    for (var i = 0; i < cells.length; i++) {
      sum += (cells[i] - other.cells[i]).abs();
    }
    return sum / cells.length;
  }
}

/// Turns the grids of the live frames into a [FrameHint]. A message only changes after [confirmations] frames
/// agree, so it does not flicker with a single jolt.
///
/// The thresholds are a starting point to be tuned on real phones.
class FrameQualityMonitor {
  FrameQualityMonitor({this.darkMean = 55, this.veryDarkMean = 45, this.veryDarkRelease = 7, this.movingDifference = 8, this.confirmations = 2});

  /// Mean luma under which there is not enough light: the person is warned.
  final double darkMean;

  /// Mean luma under which the image is too dark to take the photo: the shutter is off. Set with an iPhone, where a
  /// dim room reads between 40 and 50.
  final double veryDarkMean;

  /// Once blocked, the light has to rise this much over [veryDarkMean] to let go, so a scene hovering around the
  /// limit does not make the shutter flicker.
  final double veryDarkRelease;

  /// Mean change between consecutive frames over which the phone is moving.
  final double movingDifference;

  final int confirmations;

  FrameHint hint = FrameHint.analyzing;
  LumaGrid? _previous;
  FrameHint? _candidate;
  var _count = 0;

  FrameHint add(LumaGrid grid) {
    final previous = _previous;
    _previous = grid;
    if (previous == null) return hint;

    final limit = hint == FrameHint.veryDark ? veryDarkMean + veryDarkRelease : veryDarkMean;
    final raw = grid.mean < limit
        ? FrameHint.veryDark
        : grid.mean < darkMean
        ? FrameHint.tooDark
        : grid.differenceTo(previous) > movingDifference
            ? FrameHint.moving
            : FrameHint.good;
    if (raw == hint) {
      _candidate = null;
      _count = 0;
      return hint;
    }
    if (raw == _candidate) {
      _count++;
    } else {
      _candidate = raw;
      _count = 1;
    }
    if (_count >= confirmations) {
      hint = raw;
      _candidate = null;
      _count = 0;
    }
    return hint;
  }

  void reset() {
    hint = FrameHint.analyzing;
    _previous = null;
    _candidate = null;
    _count = 0;
  }
}
