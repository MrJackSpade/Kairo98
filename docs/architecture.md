# Architecture

## Boundaries

The Android app owns the user interface, game launch intent, storage access, rendering, audio output, and physical and touch input. The native emulator library owns PC-98 machine state, disk devices, video frames, sound chip register writes, and virtual keyboard and mouse events.

```text
LaunchBox or Android file picker
             |
             v
Android activity -> content access -> disk image adapter
      |                              |
      +-> input mapping --------+    |
      +-> flyout keyboard ------+    |
                                v    v
                         native 21/W core
                                |
                        ymfm sound adapter
                                |
                        Android audio output
```

The Android layer should not depend on a particular emulator's host UI. It translates platform events into explicit emulator actions. The flyout keyboard and gamepad mapper use the same virtual key API so a key behaves identically from either source.

The current Android session flyout is an activity overlay above the 640×400 surface. It pauses the emulation worker while open and keeps its own pause choice when dismissed. A touch-only left-edge swipe, Android Back/Menu, or a delivered controller Mode event opens it; Android's system Home event is not delivered to applications. Graphics, base clock, and audio options persist in app preferences. The renderer writes nearest-neighbor RGB565 pixels into the selected viewport size. Integer scaling defaults to a contained whole-pixel multiple so every source pixel is visible. An optional cropped integer choice centers the next whole-pixel multiple and hides excess at the display edges. Fit uses the largest aspect-preserving fractional viewport. This session menu is separate from the future optional PC-98 flyout keyboard.

## Input model

A mapping target may be a PC-98 key, a combination of keys, a mouse button or movement, or an app action such as opening the disk menu. Mappings are saved globally and may be overridden per game. Each source must produce matching press and release events; focus loss and controller disconnect release held virtual keys.

The flyout keyboard is optional and can be hidden during play. It must expose PC-98-specific keys, including STOP, COPY, GRPH, XFER, NFER, KANA, and function keys, as supported by the selected core.

## Storage and launch

The exported launch activity will accept a game URI or path from Android frontends. Android's storage permissions require resolving or importing the image before the native core opens it. Disk changes must work after launch for multi-disk games. User-supplied firmware stays outside the distributed app.

The exact intent contract and package ID will be fixed when the Android project is created. LaunchBox documents its custom emulator intent format: <https://feedback.launchbox-app.com/en/help/articles/7096169-custom-emulator-with-code>. See [migration plan](migration-plan.md) for the sequence and verification gates.
