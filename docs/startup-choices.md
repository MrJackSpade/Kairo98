# Startup choices and screen hashes

Some cataloged games need a menu choice or a DOS command before play. Kairo98 can show a labeled choice before boot, then send the selected character through the normal PC-98 input router when the corresponding guest screen appears. **Play manually** leaves those choices to the player.

Startup profiles are keyed by extracted disk content ID in [the profile data](../catalog/startup-profiles-v1.json). A profile can contain ordered choices, accepted screen hashes, and optional disk-swap rules. A missing or changed screen match leaves the game running and offers retry, keyboard, or restart rather than sending a key to an unknown screen.

The `fnv1a64-rgb565-v1` hash covers the full 640×400 RGB565 guest framebuffer in row order, low byte then high byte per pixel. It starts at `14695981039346656037`, XORs each byte and multiplies by `1099511628211` with 64-bit wraparound. Values are sixteen lowercase hex digits. These hashes identify a prompt, not the game as a whole. Font or BIOS differences can change the match.

The [catalog builder](../tools/build_review_catalog.py) validates and packages profiles with metadata. Players can still use the manual keyboard and disk menu when an automatic profile does not fit their copy.
