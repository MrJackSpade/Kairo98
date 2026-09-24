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

The Android session flyout is an activity overlay above the 640×400 surface. It pauses the emulation worker while open and keeps its own pause choice when dismissed. A touch-only left-edge swipe, Android Back/Menu, or a delivered controller Mode event opens it; Android's system Home event is not delivered to applications. Graphics, base clock, and audio options persist in app preferences. The renderer writes nearest-neighbor RGB565 pixels into the selected viewport size. Integer scaling defaults to a contained whole-pixel multiple so every source pixel is visible. An optional cropped integer choice centers the next whole-pixel multiple and hides excess at the display edges. Fit uses the largest aspect-preserving fractional viewport.

## Input model

Controller input has two mappings: physical Android buttons, hats, and axes to a stable virtual controller, then virtual controls to one to four PC-98 keys, one of the six joystick 1 controls, or an app action. The full-screen mapping page has Physical, Global, and This game tabs. Physical assignments are saved once across games; virtual-to-guest assignments have a global default and catalog or user game profiles. Tapping a row opens a target chooser, so assigning a PC-98 key never requires a physical keyboard. Optional controller capture and manual Android input codes cover unusual devices. Each physical source produces matching press and release events; focus loss and controller disconnect release held guest keys and joystick controls. The Android joystick host adapter exposes active-low status to the existing FM sound board and AMD-98 guest read paths.

The optional keyboard slides up from the bottom without pausing the guest. Its fitted rows have ABC, ?123, and PC-98 pages; function, navigation, and numpad keys are on the PC-98 page. Shift and Caps update visible legends using the pinned BIOS key translation table. Switching pages clears latched Shift. A swipe inward from the right screen edge opens it in every input mode. Opening it reduces the available display area; the viewport is recomputed using the selected scaling policy.

`android_host/input_telemetry.c` owns passive, monotonic counters for BIOS keyboard waits and polls and bus mouse port reads, plus a keyboard-waiting flag. Small hooks in the pinned core only publish observations. Neither those hooks nor the native telemetry buffer read Android settings, choose an input mode, inject input, or change emulation flow. A JNI snapshot exposes four plain numbers to Android. Reset, disk change, and machine start clear the buffer.

`InputModeDecider` alone resolves Auto. It starts in Keyboard; a current blocking BIOS keyboard wait or repeated keyboard polling selects Keyboard. Four or more bus mouse reads in a 250 ms observation window select Mouse only after 750 ms without keyboard activity. Mouse stays selected through idle periods; keyboard activity switches it back. Forced Keyboard and Mouse settings bypass the Auto result. The per-game catalog `input.mode` overrides the global default; absent metadata uses Auto. A screen tap in Keyboard opens the panel without sending a guest click. Mouse touch uses relative touchpad gestures: dragging moves the guest cursor, tapping clicks at its current position, and holding before dragging holds the left button. A 700 ms click pulse lets slow guest polling observe it. Arbitrary guest software stores its own cursor coordinates and may apply acceleration, so the Android layer does not assume a guest cursor position or promise that a touch point matches it. This is a conservative heuristic: guest software can bypass BIOS keyboard services, a resident driver can poll the mouse independently, and mixed-input games can remain Keyboard until the user sets a per-game override.

## Storage and launch

The exported launch activity will accept a game URI or path from Android frontends. Android's storage permissions require resolving or importing the image before the native core opens it. Disk changes must work after launch for multi-disk games. User-supplied firmware stays outside the distributed app.

The exact intent contract and package ID will be fixed when the Android project is created. LaunchBox documents its custom emulator intent format: <https://feedback.launchbox-app.com/en/help/articles/7096169-custom-emulator-with-code>. See [migration plan](migration-plan.md) for the sequence and verification gates.
