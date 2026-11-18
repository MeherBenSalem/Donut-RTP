# DonutRTP 2.0.1

## Fixed

- **RTP zones with `cancel-on-move: true` cancelled right after you stepped in.** The countdown remembered the
  block you were standing on *before* entering the zone, so your next step on the pad counted as walking off and
  cancelled it at once. It now remembers the block where you entered, so the countdown runs until you actually walk
  off that block. Jumping still never cancels. Zones with `cancel-on-move: false` (the default) were not affected.

No config changes. Update by replacing the jar.
