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

## Input model

A mapping target may be a PC-98 key, a combination of keys, a mouse button or movement, or an app action such as opening the disk menu. Mappings are saved globally and may be overridden per game. Each source must produce matching press and release events; focus loss and controller disconnect release held virtual keys.

The flyout keyboard is optional and can be hidden during play. It must expose PC-98-specific keys, including STOP, COPY, GRPH, XFER, NFER, KANA, and function keys, as supported by the selected core.

## Storage and launch

The exported launch activity will accept a game URI or path from Android frontends. Android's storage permissions require resolving or importing the image before the native core opens it. Disk changes must work after launch for multi-disk games. User-supplied firmware stays outside the distributed app.

The exact intent contract and package ID will be fixed when the product name and Android project are created. LaunchBox documents its custom emulator intent format: <https://feedback.launchbox-app.com/en/help/articles/7096169-custom-emulator-with-code>.
