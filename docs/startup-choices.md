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

1. Play launches the game normally, including any existing catalog DOS command. If this game has a verified typed startup question, the app opens a compact **Start choice** panel below the emulated display. The display remains fully visible above it, using the current scaling policy.
2. The panel gives large, labeled answer buttons. It stays visible through an intro or loading screen. The player taps an answer when the matching guest question is visible. D-pad changes the selected answer and controller A sends it; Back closes the panel without guest input. **Keyboard** opens the existing flyout keyboard for an unlisted answer.
3. The tap sends that option's cataloged key, releases it, and sends Enter only when that option's tested sequence requires Enter. Use the same bounded press/release cadence as the current DOS command sender, with a short fixed settling gap before Enter; do not add per-game timing scripts. The app closes the panel after the sequence. A **Send again** action remains in the session flyout until another game starts, so an early or missed press can be retried without restarting.
4. If the selected path presents another typed question, the next panel stage appears. The player again taps only when that guest question is visible. For example, Jewel Bem Hunter Lime can have a page-one “More episodes” answer (`9`) followed by a separately verified page-two episode question.

There is no countdown or guess based on elapsed boot time. The app does not infer that an arbitrary game menu is ready from keyboard polling: DOS, drivers, and games can all poll the keyboard. The player's tap is the readiness signal for these menus. This avoids sending a digit into an intro, a date prompt, or active gameplay. The panel is absent for games without a verified typed question.

## Catalog contract

Use an additive `startupChoices` field keyed by the same content ID as existing game metadata. It is independent of `launch`: a fixed DOS command such as Night Slave's `NS` still waits for the existing DOS prompt; a later typed question is presented to the player. Catalog source, user additions, and per-game overrides follow the current precedence and reset rules. Old catalog readers ignore the new field.

Proposed record for Starfire's tested sequences:

```json
{
  "startupChoices": [
    {
      "id": "display-version",
      "title": "Choose display version",
      "options": [
        {"id": "16-color", "label": "16-color version", "key": "1", "enter": true},
        {"id": "256-color", "label": "256-color version", "key": "2", "enter": true}
      ]
    }
  ]
}
```

For the first version, each option has one ASCII letter or digit and an explicit Boolean `enter`; there are no arbitrary scripts, delays, host commands, or mouse actions. A game may have several ordered question stages. Bounds: at most four stages, twelve options per stage, unique IDs within each stage, and short nonempty titles and labels. Validate the full field in the catalog builder and at runtime; reject malformed records individually. An override replaces this whole field, and Reset removes the override. No option is auto-selected or remembered by default, so different episodes remain easy to choose on each launch.

The Android `StartupChoiceController` owns the stage, chosen option, and launch generation. A small `GuestKeySequenceSender` converts the validated character and optional Enter to PC-98 scan codes and uses the existing `InputRouter`; both the panel and the old DOS command injector call this sender. Stop, restart, game switch, activity loss, or a dismissed panel cancel any queued presses and release held keys. The native emulator only receives ordinary key press/release events. No menu-specific logic or dependency on catalog data enters the core.

## Verification gate

Implement the panel and sender with one fully tested menu first. On the Retroid, verify both touch and controller selection, each option's exact key sequence, that an early press can be sent again, Back/keyboard fallback, cancellation on game switch, and no panel for Rusty or an arrow-driven menu. Then add each row above only after its numbered choice actually reaches the expected guest screen. Keep test captures and game files ignored locally; only the validated catalog mapping is shipped.
