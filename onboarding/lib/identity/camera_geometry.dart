import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Zoom with which the DUI is photographed. The phone cannot focus on something too close, and filling the frame
/// with the DUI at 1x needs the phone about 9 cm away. At 2x it can stay about twice as far, where it does focus.
/// A starting point to be tuned on real phones.
const documentZoom = 2.0;

/// [documentZoom] limited to what the camera can do.
double initialZoom(double min, double max) => documentZoom.clamp(min, math.max(min, max));

/// The size of the preview as the screen needs it, in portrait. The camera reports it in landscape (1280x720), so
/// using it as it comes would draw the image on its side or squashed.
Size portraitSize(Size preview) => Size(math.min(preview.width, preview.height), math.max(preview.width, preview.height));

/// Shows [child] (the live image) with the proportion of the camera, [size], covering the whole area and cropping
/// what does not fit. Without it, a camera image is stretched to whatever shape of area it is given.
class CoverPreview extends StatelessWidget {
  const CoverPreview({super.key, required this.size, required this.child});

  /// Size of the camera image in portrait; only its proportion matters.
  final Size size;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return ClipRect(
      child: SizedBox.expand(
        child: FittedBox(
          fit: BoxFit.cover,
          child: SizedBox(width: size.width, height: size.height, child: child),
        ),
      ),
    );
  }
}
