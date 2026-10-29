# Startup choices and screen hashes

[← Kairo98](../README.md) · [All documentation](../README.md#documentation)

Some cataloged games need a menu choice or a DOS command before play. Kairo98 can show a choice page before boot, then send the selected character through the normal PC-98 input router when the corresponding guest screen appears. **Play manually** leaves those choices to the player.

Startup profiles are keyed by extracted disk content ID in [the profile data](../catalog/startup-profiles-v1.json). A profile can contain ordered choices, accepted screen hashes, and optional disk-swap rules. A missing or changed screen match leaves the game running and offers retry, keyboard, or restart rather than sending a key to an unknown screen.

The `pc98-vram-v1` hash covers the video memory the guest is displaying, not the rendered picture, so the font, shader, renderer, and text cursor blink do not change it. It is FNV-1a 64: it starts at `14695981039346656037`, XORs each byte and multiplies by `1099511628211` with 64-bit wraparound, over these bytes in order:

1. A layer byte: bit 0 when text is displayed, bit 1 when graphics are displayed (both require the display to be on). Then the graphics mode byte, `gdc.analog & 3`, or 0 when graphics are off.
2. When text is displayed: the 8 KB of character codes at `A0000h`, then the attribute byte of each cell (even bytes from `A2000h` up to, not including, the memory switches at `A3FE0h`).
3. When graphics are displayed in 256-color mode: all PEGC VRAM, then the 256-entry palette. Otherwise: the displayed page's B, R, and G planes, then either the E plane and the 16-entry analog palette, or the 8-color digital palette.

Values are sixteen lowercase hex digits. A hash identifies a prompt, not the game as a whole. A game that draws its menu text into graphics VRAM from the font ROM still depends on the font, so record such a screen with each font it must match. Capture hashes with the debug extra `kairo98.traceScreenHashes`, which writes each new signature, with the time and the machine's status, to `files/startup-hash-trace.txt`. The shared frontend carries out startup choices and disk swaps for every Kairo app (`StartupAutomation`, Kairo#83); Kairo98's signatures are of its video memory (`kairo98_machine_screen_signature`), where the other apps sign the frame they show.

The [catalog builder](../tools/build_review_catalog.py) validates and packages profiles with metadata. Players can still use the manual keyboard and disk menu when an automatic profile does not fit their copy.
