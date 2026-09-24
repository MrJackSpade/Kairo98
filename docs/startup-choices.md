# Typed startup choices

This feature covers startup questions whose answer is a **typed digit or letter**, optionally followed by Enter. It lets a player tap a labeled choice instead of opening the PC-98 keyboard. Menus operated with arrows and Enter stay on the emulated screen and use the existing controller, touch keyboard, or touchscreen input.

## Observed candidates

Archive numbers refer to the [Retroid boot survey](boot-survey.md). A visible number establishes a candidate choice, not a verified key sequence. Add a catalog choice only after pressing its key on the device and observing the intended result. Do not include Quit in the initial sheet: Android Back and the session menu already provide exit, and Quit could terminate the guest unexpectedly.

| Archive # | Question to show | Visible answers | First implementation step |
| --- | --- | --- | --- |
| 21 | Start Cyber Arms Val-Kaizer? | Set sound (1), Start game (2) | Verify `2` and whether Enter is required. Sound setup may ask another question. |
| 30, 31, 150 | What part of Dragon Knight? | Game (1), Bonus content (2) | Verify both paths; the menu displays an unexplained `4` in its input prompt, so do not expose it. |
| 36 | Which Exciting Milk episode? | Episode 1 (1), Episode 2 (2) | Verify each single-digit response. |
| 46 | What to do in Farland Story? | Start game (1), Watch opening (2) | Verify each response. |
| 49 | What part of Foxy? | Game (1), Special disk (2), Character edit (3) | Verify that each option is available with the mounted media. |
| 61, 63 | Which Jewel Bem Hunter Lime episode? | Episodes 1–6 (digits 1–6), Next menu (9) | Verify page one. Treat page two as a separate question after testing its menu and keys. |
| 73 | Night Slave music driver | Regular driver (1 or other key), Disable MIDI (2) | Verify exact behavior; the existing `NS` DOS command runs first. |
| 84, 85 | What part of Rance II + Hint Disk? | Game (1), Hint Disk (2) | Verify both paths, especially whether Hint Disk needs companion media. |
| 98 | Which Starfire display version? | 16-color (1), 256-color (2) | `1`, then Enter reached the Starfire title; `2`, then Enter reached translated story on the Retroid. These are working sequences, though a shorter sequence may suffice. |

Belloncho Body Inspection asks BASIC for a file count, and Giten Megami Tensei asks for date and time before returning to DOS. Neither has a known correct startup answer, and both may need more than one character. Sword of Kumdor asks for a freeform name. Keep these out of this single-character choice feature. Grand Prix Circus's setup screen explicitly uses arrows, Enter, and Escape, so it is outside this feature. The sound, display, and title menus in the survey that use a cursor or highlighted row are outside it as well.

## Player flow

1. Play opens a touch-friendly **Start choices** page before the machine boots. It shows large labeled answer buttons for each cataloged typed question. D-pad and controller A work here too. No choice is silently selected or remembered. **Play without answers** is available for manual keyboard use.
2. After the player chooses, the app starts the machine and queues each fixed launch command and selected answer in order. Each queued step waits for its own cataloged screen hash set. The native Android host hashes the complete guest framebuffer every 100 ms while the queue is nonempty. The first matching hash releases that step's input; the queue then advances to the next step.
3. A menu answer sends its cataloged letter or digit and sends Enter only when that option's tested sequence requires it. Use the current bounded key press/release cadence. The player sees the actual guest screen throughout startup; no app panel covers it while booting.
4. If no accepted hash appears before a bounded timeout, leave the answer unsent and show a visible recovery action: retry the wait, open the keyboard, or restart. Stop, reset, game switch, and exit cancel the queue and release held keys.

The screen match is the readiness signal. It depends on emulated pixels, so a slower Android frame, a different CPU clock, or extra boot frames do not shift the trigger. No keyboard-poll heuristic or elapsed-frame guess selects a menu. A game without cataloged typed questions starts as it does today.

The previous frame-count experiment demonstrated why a fixed frame is unsuitable: separate cold Starfire boots paused at frame 450 showed the numbered menu in one run and DOS startup in another. Four captures of the eventual menu differed only in the 16×32 blinking cursor region. Both full-frame cursor states are valid matches and should be recorded as accepted hashes; there is no need to crop or mask the screen.

## Catalog contract

Use an additive `startupChoices` field keyed by the same content ID as existing game metadata. It is independent of `launch`: a fixed DOS command such as Night Slave's `NS` comes before its driver-choice answer in the ordered queue. Both kinds of queued input use screen-hash readiness once their hashes have been captured. Existing `guestCommand` records continue using their current DOS-prompt signal until their screen hashes have been measured and added. Catalog source, user additions, and per-game overrides follow the current precedence and reset rules. Old catalog readers ignore the new field.

Internally, every queued item is just **accepted full-screen hashes → guest keys**. A game with two DOS commands needs a separate accepted-hash set for each prompt, such as `A:\>` before `CD PW` and `A:\PW>` before `GAO2`; the second command must not fire merely because the first was sent. The existing `launch.commands` data can be compiled into this ordered form as its hashes are collected. The same queue then accepts a chosen menu answer at a later matching screen.

Proposed record for Starfire's tested keys; the hash strings are placeholders until captured from the raw guest framebuffer:

```json
{
  "startupChoices": [
    {
      "id": "display-version",
      "title": "Choose display version",
      "screenHashes": ["0123456789abcdef", "fedcba9876543210"],
      "options": [
        {"id": "16-color", "label": "16-color version", "key": "1", "enter": true},
        {"id": "256-color", "label": "256-color version", "key": "2", "enter": true}
      ]
    }
  ]
}
```

Hash the full 640×400 RGB565 framebuffer produced by the native emulator, before Android scaling or overlays. Define one deterministic byte order and a versioned 64-bit hash algorithm (`fnv1a64-rgb565-v1`); store its fixed-width lowercase hexadecimal result. `screenHashes` is a nonempty set of accepted complete-frame values. A blinking cursor normally contributes two values, both of which match. Sample at 100 ms intervals only while a queued step waits. Hash on the emulation worker after it finishes a frame, then publish the immutable value for Android's queue controller to compare. Reset the published value on each machine start or reset so a previous game cannot satisfy a new step.

For the first version, each option has one ASCII letter or digit and an explicit Boolean `enter`; there are no arbitrary scripts, host commands, or mouse actions. A game may have several ordered question stages. Bounds: at most four stages, twelve options per stage, unique IDs within each stage, and short nonempty titles and labels. Validate the full field in the catalog builder and at runtime; reject malformed records individually. An override replaces this whole field, and Reset removes the override. Different episodes remain easy to choose on each launch.

The Android `StartupChoiceController` owns the ordered queue, selected options, matching hash sets, timeout, and launch generation. A small `GuestKeySequenceSender` converts the validated character and optional Enter to PC-98 scan codes and uses the existing `InputRouter`. The native host supplies only a current framebuffer hash when sampling is enabled; the PC-98 core still only produces pixels and receives ordinary key events. No catalog or menu decisions enter the core.

## Verification gate

Implement the full-frame hash sampler and sender with Starfire first. Capture both cursor-phase hashes from the native buffer; verify that either hash triggers each selected option from repeated cold boots, including boots with different frame counts at the prompt. Verify touch and controller selection, timeout recovery, cancellation on game switch, and no choice page for Rusty or an arrow-driven menu. Then add each row above only after its numbered choice actually reaches the expected guest screen. Keep test captures and game files ignored locally; only validated catalog mappings are shipped.
