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
      +-> touch controller -----+    |
      +-> flyout keyboard ------+    |
                                v    v
                         native 21/W core
                                |
                        ymfm sound adapter
                                |
                        Android audio output
```

The Android layer should not depend on a particular emulator's host UI. It translates platform events into explicit emulator actions. The flyout keyboard, physical gamepad, and on-screen controller use the same owned guest-input routers so simultaneous presses release correctly.

The Android session flyout is an activity overlay above the 640×400 surface. It pauses the emulation worker while open and keeps its own pause choice when dismissed. A touch-only left-edge swipe, Android Back/Menu, or a delivered controller Mode event opens it; Android's system Home event is not delivered to applications. Graphics, base clock, and audio options persist in app preferences. The renderer writes nearest-neighbor RGB565 pixels into the selected viewport size. Integer scaling defaults to a contained whole-pixel multiple so every source pixel is visible. An optional cropped integer choice centers the next whole-pixel multiple and hides excess at the display edges. Fit uses the largest aspect-preserving fractional viewport.

## Input model

Controller input has two mappings: physical Android buttons, hats, and axes to a stable virtual controller, then virtual controls to one to four PC-98 keys, joystick 1, mouse directions or buttons, or an app action. The full-screen mapping page has Physical, Global, and This game tabs. Physical assignments are saved once across games; virtual-to-guest assignments have a global default and catalog or user game profiles. On the Physical tab, tapping a virtual control immediately listens for a controller button, D-pad direction, stick, or trigger and replaces that control's previous physical assignments. An advanced source-first flow adds alternate physical inputs; manual Android codes remain available. The Global and This game tabs use selectable PC-98 targets, so assigning a guest key never requires a physical keyboard. Each physical source produces matching press and release events; focus loss and controller disconnect release held guest input. The Android joystick host adapter exposes active-low status to the existing FM sound board and AMD-98 guest read paths.

The on-screen controller feeds the same virtual controls through separately owned press and release events, so game profiles apply to touch and physical input alike. Its global master switch is shared, while per-button visibility and normalized positions are stored separately for portrait and landscape. A previous single-layout preference migrates to the orientation active when the app first reads it. A full-screen arrangement mode pauses the guest while controls are dragged. The overlay itself passes touches between buttons to the emulated display; it hides under the library, session menu, keyboard, and mapping pages. It defaults on only when no physical gamepad is detected at first launch, and an explicit setting takes precedence afterward.

The optional keyboard slides up from the bottom without pausing the guest. Its fitted rows have ABC, ?123, and PC-98 pages; function, navigation, and numpad keys are on the PC-98 page. Shift and Caps update visible legends using the pinned BIOS key translation table. Switching pages clears latched Shift. A swipe inward from the right screen edge opens it in every input mode. Opening it reduces the available display area; the viewport is recomputed using the selected scaling policy.

When Android exposes a second logical display, `SecondaryKeyboardDisplay` opens an app-owned surface there as soon as the activity is visible. Its idle color follows the main screen: setup uses its dark background, while the library and emulator use black. During play it shows the keyboard on that surface. Its header holds the screen-swap button; swapping moves the game's render surface to the secondary display and shows the keyboard on the activity display without restarting the emulator. The key area has a capped size. Touch, controller, and physical-keyboard scan codes share `InputRouter`, which drives pressed-key feedback on whichever keyboard page is visible. It prefers displays marked for presentations and accepts another valid non-primary display on devices that omit that flag. The keyboard hides and releases held keys when the game menu opens; the second-screen surface closes when the app backgrounds or the display disconnects. The right-edge keyboard remains the single-display fallback. On RG DS, which reports the lower panel as the default display, startup requests the other display for the game and uses a companion activity for the lower panel: Android rejects a `Presentation` on display 0. The request is attempted once; if Android denies it, launch continues on the current display.

`android_host/input_telemetry.c` owns passive, monotonic counters for BIOS keyboard waits and polls and bus mouse port reads, plus a keyboard-waiting flag. Small hooks in the pinned core only publish observations. Neither those hooks nor the native telemetry buffer read Android settings, choose an input mode, inject input, or change emulation flow. A JNI snapshot exposes four plain numbers to Android. Reset, disk change, and machine start clear the buffer.

`InputModeDecider` alone resolves Auto. It starts in Keyboard; a current blocking BIOS keyboard wait or repeated keyboard polling selects Keyboard. Four or more bus mouse reads in a 250 ms observation window select Mouse only after 750 ms without keyboard activity. Mouse stays selected through idle periods; keyboard activity switches it back. Forced Keyboard and Mouse settings bypass the Auto result. The per-game catalog `input.mode` overrides the global default; absent metadata uses Auto. A screen tap in Keyboard opens the panel without sending a guest click. Mouse touch uses relative touchpad gestures: dragging moves the guest cursor, tapping clicks at its current position, and holding before dragging holds the left button. A 700 ms click pulse lets slow guest polling observe it. Arbitrary guest software stores its own cursor coordinates and may apply acceleration, so touchpad mode does not assume a guest cursor position. The optional Direct tap touch mouse setting maps the touch to a 640x400 guest pixel from the view's own coordinates, which already reflect scaling and any crop, then warps there: `android_host/mousemng.c` first moves the cursor 768 by 480 counts toward the top-left, where guest software clamps it, and then by the target in counts, before the click or hold is sent. The bus mouse latch keeps one signed byte per axis and drops the rest, so warp and movement counts are released only as the guest latches earlier ones. Direct tap lands on the touched point in software that clamps its cursor at the screen's top-left and moves one pixel per count without acceleration; other software lands offset or scaled. This is a conservative heuristic: guest software can bypass BIOS keyboard services, a resident driver can poll the mouse independently, and mixed-input games can remain Keyboard until a per-game override is set.

## Storage and launch

The exported launch activity will accept a game URI or path from Android frontends. Android's storage permissions require resolving or importing the image before the native core opens it. Disk changes must work after launch for multi-disk games. Imported firmware stays outside the distributed app.

The exact intent contract and package ID will be fixed when the Android project is created. LaunchBox documents its custom emulator intent format: <https://feedback.launchbox-app.com/en/help/articles/7096169-custom-emulator-with-code>. See [migration plan](migration-plan.md) for the sequence and verification gates.
