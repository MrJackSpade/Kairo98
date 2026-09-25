# PC-98 boot survey

Run games on an Android device before assigning launch behavior. A disk's root
directory or `AUTOEXEC.BAT` alone does not establish what appears on screen: Rusty,
Ayayo, and Metajo all started without a root `AUTOEXEC.BAT` in the inspected
HDI. The first pass records a timed screen after a fresh launch; it may land
on an intro fade or loading transition. "Starts" does not mean a full
playthrough has been completed.

The debug APK accepts a direct ADB launch by exact content ID or unambiguous
title. For example:

```powershell
.\tools\adb_launch_game.ps1 -Title 'Starfire'
.\tools\adb_launch_game.ps1 -ContentId 'sha256-hdi-v1:a80b9658a8456535da828d38ca999999b272b451dcd4b688c25d029ae480649e'
```

The command restarts the app, prepares the selected disk, and begins emulation
without opening its library page. It is accepted by debug builds only. If a
title matches several revisions, use a content ID. The Retroid survey runner
uses the IDs in the app's local library cache:

```powershell
python tools/adb_boot_survey.py --start 0 --limit 20 --seconds 12
python tools/adb_boot_survey.py --indices 48,110,111,115 --capture-at 10,30,60
```

It writes an ignored plan, log, and numbered screenshots to
`.downloads/boot-survey/`. The second command keeps several frames from each
launch. Its times are wall-clock seconds after requesting the launch, including
disk preparation. A black or white frame alone does not establish a failure:
Tuned Heart and Viper CTR were later seen advancing past intro fades.

## Confirmed on Retroid

| Game / variant | Fresh-launch observation | Action |
| --- | --- | --- |
| Rusty, translated HDI `b336c58c` | Game intro appears without a catalog command. | Keep normal boot. |
| Ayayo's Love Affair, translated HDI `8533204b` | Title artwork appears without a catalog command. | Keep normal boot. |
| GaoGao! 2nd: Pandora no Mori, translated HDI `a80b9658` | Stops at `A:\>`; `CD PW`, then `GAO2` starts the game. The new catalog command sequence reproduced the game artwork from a fresh launch. | Shipped launch sequence `CD PW`, `GAO2`. |
| Dragon Knight, translated HDI `879f430c` | Menu: **1 Game**, **2 Bonus content**, **3 Quit**. The input line also lists 4, which has no displayed meaning. | Game and Bonus content are now labeled prelaunch choices; 4 remains unoffered. |
| Farland Story, translated HDI `262c6581` | Menu: **1 Start game**, **2 Watch opening**, **3 Quit**. | Start game and Watch opening are now labeled prelaunch choices. |
| Foxy, translated HDI `b9076012` | Menu: **1 Game**, **2 Special disk**, **3 Character edit**, **4 Quit**. | The three non-quit paths are now labeled prelaunch choices. |
| Starfire, translated HDI `8fde4af0` | Menu: **1 16-color version**, **2 256-color version**, **3 Quit**. | Both display versions are now labeled prelaunch choices. |
| Giten Megami Tensei, incomplete translation 0.3 HDI `e760379a` | HDI alone asks for a date and time, then reaches DOS; `CD DDS98`, `DDS98` returned to DOS in the earlier run. The archive also contains Boot Disk FDI `45ae7823`. With the HDI and FDI mounted before boot, the Retroid reached the title menu; Enter on New Game advanced to the translated opening scene. | Require Boot Disk FDI `45ae7823` by catalog hash. No DOS command. |
| Metajo, incomplete translation 0.1 HDI `c1df4b95` | Started a game logo and advanced to a graphical menu without a catalog command. The graphical menu text looked corrupted in the capture. | Keep normal boot; investigate the menu rendering separately. |

The later [startup-choice implementation](startup-choices.md) found that both
translated Night Slave images boot without a DOS command. The older `NS`
catalog command was removed. Screenshots for this pass remain local under
`.downloads/run-*.png` and are not part of a distributable build.

## Sequential capture review, archives 0–19

The archive numbers correspond to `.downloads/boot-survey/plan.json` and its
numbered PNGs. "Intro" means the machine advanced into game-specific graphics;
later prompts remain to be checked. These runs used the catalog before the
new Variable Geo GDC setting was installed.

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 0 | Exciting Milk, separate episodes | `SILENCE 1996` intro. |
| 1 | Innocent Tour, censored | Black at 12 s; retry longer. |
| 2 | Jewel Bem Hunter Lime, separate episodes | Black at 12 s; retry longer. |
| 3 | Nova, original textbox | `CAT'S PRO.` intro. |
| 4 | V.G. Variable Geo, SFW | Explicit “Please start with GDC clock set to 2.5 MHz” message. Set 2.5 MHz for both catalog hashes and retest. |
| 5 | Aegean Kai no Shizuku v1.0 | `ILLUSION` intro. |
| 6 | Aegean Kai no Shizuku v1.1, hardware fix | `ILLUSION` intro. |
| 7 | Aegean Kai no Shizuku v1.1 | `ILLUSION` intro. |
| 8 | Alice's Cottage II v1.0 | Mascot intro image. |
| 9 | Alice's Cottage II v1.1 | Mascot intro image. |
| 10 | Alice's Cottage | Game-specific picture and dialogue window. |
| 11 | Amy's Fantasies v1.0 | `C's ware presents` intro. |
| 12 | Amy's Fantasies v1.1 | `C's ware presents` intro. |
| 13 | Appareden v0.9.9.1 | Sound-source menu: Original FM, New 86, MIDI GS, CD, None. |
| 14 | Appareden v0.9.9.2 | Same sound-source menu. |
| 15 | Ayayo's Love Affair | Game artwork. |
| 16 | Azusa 999 v1.1 | Illustrated title screen. |
| 17 | Azusa 999 v1.2 | Illustrated title screen. |
| 18 | Brandish 2 Renewal | Game's title menu (opening, start, audio, disk utility, exit). |
| 19 | CRW Metal Jacket | `WIZ` intro. |

## Sequential capture review, archives 20–39

These captures waited ten seconds after each fresh launch. Black and DOS
startup captures remain unresolved until a longer run.

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 20 | Chitty Chitty Train | Illustrated title screen. |
| 21 | Cyber Arms Val-Kaizer | Text menu: **1 Set sound (FM Music)**, **2 Start game**. |
| 22 | D.P.S. | Illustrated title screen. |
| 23 | Dead of the Brain v1.0 | PMD music-driver text; check later screen. |
| 24 | Dead of the Brain v1.0b | PMD music-driver text; check later screen. |
| 25 | Dead of the Brain 2 | `elf` intro. |
| 26 | Dengeki Nurse | Black at 10 s; retry longer. |
| 27 | Desire v1.2.1 | Black at 10 s; retry longer. |
| 28 | Desire v1.2 | Black at 10 s; retry longer. |
| 29 | Dragon Knight II | Black at 10 s; retry longer. |
| 30 | Dragon Knight, Add variant | Game / bonus / quit menu as above. |
| 31 | Dragon Knight, other Add variant | Same menu. |
| 32 | Dragoon Armor for Adult | Illustrated title screen. |
| 33 | E.V.O. The Theory of Evolution | Game-specific intro text. |
| 34 | Eve Burst Error | `C's ware` intro in progress. |
| 35 | Exciting Milk, separate episodes | `SILENCE 1996` intro. |
| 36 | Exciting Milk, combined | Menu: **1 Episode 1**, **2 Episode 2**, **0 Quit**. |
| 37 | Farland Story II | Sound output selection: **FM**, **86**, **GS**, **CD**, **None**. |
| 38 | Farland Story III, original balance | Same sound output selection. |
| 39 | Farland Story III | DOS startup text and memory-driver prompt at 10 s; check later screen. |

## Sequential capture review, archives 40–59

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 40 | Farland Story IV, original balance | DOS startup text at 10 s; check later. |
| 41 | Farland Story IV | DOS startup text at 10 s; check later. |
| 42 | Farland Story V, original balance | DOS startup text at 10 s; check later. |
| 43 | Farland Story V | FM / 86 / GS / CD / None sound choice. |
| 44 | Farland Story VI, original balance | DOS startup text at 10 s; check later. |
| 45 | Farland Story VI | DOS startup text at 10 s; check later. |
| 46 | Farland Story | Start game / watch opening / quit menu as above. |
| 47 | Fatal Relations | DOS startup text at 10 s; check later. |
| 48 | Flix Mix | Black at 10 s; retry longer. |
| 49 | Foxy | Game / special disk / character edit / quit menu as above. |
| 50 | GaoGao! 1st | Repeated `RADICAL` tile pattern with a dark box; investigate whether this is intended graphics. |
| 51 | GaoGao! 2nd | DOS startup text in this early capture; a separate longer run confirmed the catalog command launches the game. |
| 52 | Grand Prix Circus | `PRE-SETUP` configuration screen (protected/bank memory, EMS, course drive); inspect its choices. |
| 53 | Guynarock II | Game-specific spacecraft intro. |
| 54 | Harlem Blade | Music choice: BGM, GS, FM, 86, No. |
| 55 | Holy Girl Force Lakers II | `APPLE PIE` intro. |
| 56 | Holy Girl Force Lakers | DOS startup text at 10 s; check later. |
| 57 | Horny Sweeper 2 | PMD music-driver text; check later. |
| 58 | Horny Sweeper | Japanese music-source menu: FM, GS, mute. |
| 59 | Innocent Tour | `K.S.S` intro. |

## Sequential capture review, archives 60–79

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 60 | Jan Jaka Jan | Monitor type selection: RGB or Grey. |
| 61 | Jewel Bem Hunter Lime v1.0, combined | Episode 1–6 menu, 9 next menu, 0 quit. |
| 62 | Jewel Bem Hunter Lime v1.0, separate episodes | `SILENCE 1993` intro for first selected episode. |
| 63 | Jewel Bem Hunter Lime v1.1, combined | Same episode menu. |
| 64 | Jewel Bem Hunter Lime v1.1, separate episodes | `SILENCE 1993` intro for first selected episode. |
| 65 | Lightning Warrior Raidy | DOS startup text at 10 s; check later. |
| 66 | Little Vampire v1.1 | `Little Vampire` text after DOS startup; check later. |
| 67 | Little Vampire v1.2 | Game-specific illustrated scene. |
| 68 | Love Potion | DOS startup with a CD driver warning; check later. |
| 69 | Macross Skull Leader Complete Pack | Game-specific warning screen. |
| 70 | Makyouden | `CREATE` intro. |
| 71 | Marble Cooking | Game-specific intro graphic. |
| 72 | Mime: Floating Dream | Display mode selector, currently Analog. |
| 73 | Night Slave, BabaJeanmel translation | Catalog `NS` reaches music-driver selection: 1 (or another key) regular driver, 2 disables MIDI output. |
| 74 | Night Slave, Retronomicon translation | Black at 10 s; retry longer. |
| 75 | Nova | Display configuration modal; inspect choices. |
| 76 | Peret em Heru | DOS startup text at 10 s; check later. |
| 77 | Progenitor | Game configuration modal: display color/mono, drive setup, volume. |
| 78 | Rance v1.1 | `Alice SOFT 01` intro. |
| 79 | Rance v2.0 | `Alice SOFT 01` intro. |

## Sequential capture review, archives 80–99

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 80 | Rance 4.1 v1.0 | DOS startup text at 10 s; check later. |
| 81 | Rance 4.1 v1.5 | DOS startup text at 10 s; check later. |
| 82 | Rance 4.2 v1.0 | DOS startup text at 10 s; check later. |
| 83 | Rance 4.2 v1.5 | DOS startup text at 10 s; check later. |
| 84 | Rance II + Hint Disk v1.5 | DOS startup text at 10 s; check later. |
| 85 | Rance II + Hint Disk v2.0 | Menu: **1 Game**, **2 Hint Disk**, **3 Quit**. |
| 86 | Rance II v1.1 | DOS startup text at 10 s; check later. |
| 87 | Rance III | DOS startup text at 10 s; check later. |
| 88 | Rance IV v1.2 | `Queen Alice` intro. |
| 89 | Rance IV v1.3 | `Queen Alice` intro. |
| 90 | Run Run Concerto | Game-specific illustrated dialogue. |
| 91 | Runaway City | `Club` logo intro. |
| 92 | Rusty | Black at 10 s in this run; a separate longer run reached its game intro. |
| 93 | Season of the Sakura | Monitor selection: Color or Monochrome. |
| 94 | Shangrlia 2 | Black at 10 s; retry longer. |
| 95 | Shizuku | Music mode: MIDI, FM, Mute. |
| 96 | Slayers | `BANPRESTO` intro. |
| 97 | Star Cruiser II | DOS startup text at 10 s; check later. |
| 98 | Starfire | 16-color / 256-color / quit menu as above. |
| 99 | Steam Heart's | Startup says GDC clock must be 2.5 MHz, then returns to DOS. Added catalog setting; retest after install. |

## Sequential capture review, archives 100–119

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 100 | Sword World PC | Display type choice: Analog Color or B&W LCD. |
| 101 | Three Sisters Story | Black at 10 s; retry longer. |
| 102 | Touhou 1 | `ZUN soft` intro. |
| 103 | Touhou 2 | `ZUN soft` intro. |
| 104 | Touhou 3 | `ZUN soft` intro. |
| 105 | Touhou 4 | Sound-board selection menu over title image. |
| 106 | Touhou 5, bugfix variant A | Sound-board selection menu over title image. |
| 107 | Touhou 5, bugfix variant B | DOS startup text at 10 s; check later. |
| 108 | Toushin Toshi | DOS startup text at 10 s; check later. |
| 109 | True Love | DOS startup text at 10 s; check later. |
| 110 | Tuned Heart | DOS startup text at 10 s; check later. |
| 111 | Tuned Heart, Add variant | DOS startup text at 10 s; check later. |
| 112 | Ultima VIII: Pagan | DOS startup text at 10 s; check later. |
| 113 | Usagi na Panic | Keyboard and video-mode setup modal. |
| 114 | V.G. Variable Geo | Same 2.5 MHz GDC warning as the SFW variant; retest after install. |
| 115 | Viper CTR | DOS startup text at 10 s; check later. |
| 116 | Viper GTS RS | Game-specific fiction notice. |
| 117 | Viper V-16 | Game-specific fiction notice. |
| 118 | Words Worth | Drive configuration menu: two drives, one RAM drive, or installed on HD. |
| 119 | Xenon | Game-specific intro graphic. |

## Sequential capture review, archives 120–139

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 120 | YU-NO v1.04 | Black at 10 s; retry longer. |
| 121 | YU-NO v1.6 | Black at 10 s; retry longer. |
| 122 | Yugekitai | Black at 10 s; retry longer. |
| 123 | Yugekitai Kakuto Hen | Game demonstration screen. |
| 124 | Acrojet, D88 | Illustrated game title. |
| 125 | Belloncho Body Inspection, FDI | BASIC asks “How many files (0–15)?”; investigate intended answer. |
| 126 | The Black Onyx, FDD | Color or monochrome monitor choice. |
| 127 | Cybernetic Hi-School 2.0, HDM | Loader asks for Disk B in drive 2. The two blinking full-frame hashes trigger an automatic mount of Disk 2 in B; the game advances to mouse/keyboard selection without a key. |
| 128 | Dragon Slayer: The Legend of Heroes, NFD | Japanese warning says GDC is in 5 MHz mode and asks for DIP 2-8 OFF/reset. Added 2.5 MHz catalog setting; retest. |
| 129 | Gage, D88 | Publisher intro. |
| 130 | Hacchake Ayayo-san, D88 | Illustrated game screen and dialogue. |
| 131 | Mad Paradox, HDM | `QUEEN SOFT` intro. |
| 132 | Madou Monogatari 1, HDM | Black at 10 s; retry longer. |
| 133 | Madou Monogatari I, HDM | Black at 10 s; retry longer. |
| 134 | Madou Monogatari II, HDM | Black at 10 s; retry longer. |
| 135 | Madou Monogatari III, HDM | Black at 10 s; retry longer. |
| 136 | Master of Monsters, HDM | Illustrated title screen. |
| 137 | Melpool Land, FDI | Game-specific intro narration and image. |
| 138 | Policewoman VX, HDM | Game-specific intro text. |
| 139 | Reserve 1-2, FDI | Illustrated game screen and dialogue. |

## Sequential capture review, archives 140–159

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 140 | Reserve, FDI | The Disk-1-only survey reported disk not ready (`ERR-71`). The translator's README specifies Disk 1 in A and Disk 2 in B. With both mounted before boot, Enter from the title advanced to the translated story scene. |
| 141 | The Screamer, FDI | Character setup screen. |
| 142 | The Sword of Kumdor, FDI | Black at 10 s; retry longer. |
| 143 | Wind's Seed, FDI | Game-specific sky intro. |
| 144 | Ys, FDI | Illustrated title. |
| 145 | Case of Dungeon, FDI | Game-specific logo intro. |
| 146 | Mah Saikou Jan, FDI | Analog / 8-color display choice in Japanese. |
| 147 | Prince of Persia, FDI | `Broderbund` intro and sound-source prompt. |
| 148 | Strush, FDI | Black at 10 s; retry longer. |
| 149 | Xak II, FDI | Setup menu in Japanese. |
| 150 | Dragon Knight, easy-mode hack | Game / bonus / quit menu as above. |
| 151 | Farland Story III, harder difficulty | Game information and “Hit any key to start.” |
| 152 | Farland Story III, original difficulty | “Hit any key to start.” |
| 153 | Farland Story IV, almost original difficulty | DOS startup text at 10 s; check later. |
| 154 | Farland Story IV, harder difficulty | DOS startup text at 10 s; check later. |
| 155 | Farland Story V, harder difficulty | FM / 86 / GS / CD / None sound choice. |
| 156 | Farland Story V, original difficulty | Same sound choice. |
| 157 | Farland Story VI, harder difficulty | DOS startup text at 10 s; check later. |
| 158 | Farland Story VI, original difficulty | DOS startup text at 10 s; check later. |
| 159 | Rusty, Butt Fix hack | `Club` intro; normal Rusty has been separately verified into game. |

## Sequential capture review, archives 160–172

| # | Archive | Screen after fresh launch |
| --- | --- | --- |
| 160 | Ultima VIII, voice patch | DOS startup text at 10 s; check later. |
| 161 | V.G. Variable Geo, SFW hack | 2.5 MHz GDC warning; retest with the new catalog setting. |
| 162 | Bomber Quest | Publisher intro. |
| 163 | Briganty | `GIGA` intro. |
| 164 | Canaan | Game-specific intro. |
| 165 | Gate of Souls | `KOEI PRESENTS` intro. |
| 166 | Giten Megami Tensei 0.3 | HDI alone: date prompt. HDI plus boot FDI: title menu and New Game scene. |
| 167 | Giten Megami Tensei 0.2 | HDI alone: date prompt. Paired boot FDI: title menu. |
| 168 | Holy Girl Force Lakers III | DOS startup text at 10 s; check later. |
| 169 | Metajo | Black at 10 s here; separate longer run reached its graphical menu. |
| 170 | Primal Space | DOS startup text at 10 s; check later. |
| 171 | X-Girl | DOS driver startup text at 10 s; check later. |
| 172 | Xenon: Mugen no Shitai | Game-specific intro graphic. |

After installing the corrected catalog, fresh device runs reached the Variable
Geo title screen for archive indices 4, 114, and 161; Dragon Slayer advanced
to its opening story text for index 128; and Steam Heart's reached its playable
title menu for index 99. Captures are under ignored
`.downloads/boot-survey-clock/` and `.downloads/boot-survey-clock2/`.

## Longer follow-up, first set

These fresh launches waited 22 seconds; captures are in ignored
`.downloads/boot-survey-long/`.

| Archive # | Result |
| --- | --- |
| 1 | Innocent Tour reaches 16-color / monochrome display choice. |
| 2 | Jewel Bem Hunter Lime separate episode reaches its opening story. |
| 23, 24 | Both Dead of the Brain versions reach New Game / Load Game / Option. |
| 26 | Dengeki Nurse reaches its game logo. |
| 27, 28 | Desire warns that DIP 2-8 must be OFF and 5 MHz GDC is not guaranteed. Added 2.5 MHz catalog setting; retest. |
| 29 | Dragon Knight II reaches its illustrated intro. |
| 39 | Farland Story III reaches its “Hit any key to start” screen. |
| 40, 42 | Farland Story IV reaches FM / 86 / GS / CD / None sound choice. |
| 41 | Farland Story IV original-balance variant reaches “Hit any key to start.” |
| 44, 45 | Farland Story VI reaches FM / 86 / GS / CD / None sound choice. |
| 47 | Fatal Relations reaches its game title menu. |
| 48 | White in the 22-second capture. A later live device run loaded the game after transient graphical errors; investigate those transitions separately. |
| 56 | Holy Girl Force Lakers reaches its title. |
| 57 | Horny Sweeper 2 reaches BGM / GS / FM / 86 / No choice. |
| 65 | Lightning Warrior Raidy reaches its title menu. |
| 68 | Love Potion warns that DIP 2-8 must be OFF and 5 MHz GDC is not guaranteed. Added 2.5 MHz catalog setting; retest. |

## Longer follow-up, second set

These are fresh 22-second device runs using the exact cached content ID for
each archive.

| Archive # | Result |
| --- | --- |
| 74 | Night Slave's other translation reaches its starfield intro. |
| 76 | Peret em Heru returns to DOS after its startup batch. Manually entering `GAME` repeats the FM driver messages and returns to DOS; this needs investigation, not a blind launch command. |
| 80, 81 | Rance 4.1 reaches its title/menu. |
| 82, 83 | Rance 4.2 reaches its title/menu. |
| 84 | Rance II + hint reaches Game / Hint Disk / Quit menu. |
| 86 | Rance II v1.1 reaches game story text. |
| 87 | Rance III reaches its story intro. |
| 94 | Shangrlia2 asks for color or monochrome monitor. |
| 97 | Star Cruiser II reaches an audio balance menu. |
| 101 | Three Sisters' Story reaches its JAST intro. |
| 107 | Touhou 5's second bugfix variant reaches a sound-board menu. |
| 108 | Toushin Toshi reaches its title. |
| 109 | True Love reaches its title screen. |
| 110, 111 | Black in the 22-second captures. A later live device test confirmed Tuned Heart advances through an intro fade. |
| 112 | The earlier build showed a persistent green/cyan pattern. With the portable desktop 21/W machine and graphics flags enabled, a fresh Android run reached the normal red/purple word-entry screen. |
| 115 | Black in the 22-second capture. A later live device test confirmed Viper CTR advances through an intro fade. |
| 120 | YU-NO v1.04 reaches color / monochrome choice. |

## Longer follow-up, third set

| Archive # | Result |
| --- | --- |
| 121 | The other YU-NO translation reaches color / monochrome choice. |
| 122 | Yugekitai reaches its translated story intro. |
| 132–135 | Black in the 22-second captures. Longer live runs and disk arrangement still need verification. |
| 142 | Sword of Kumdor reaches an illustrated name-entry prompt. |
| 148 | Strush asks for a sound source and recommends FM. |
| 153, 154 | Both Farland Story IV hack variants reach “Hit any key to start.” |
| 157, 158 | Both Farland Story VI hack variants reach the FM / 86 / GS / CD / None sound choice. |
| 160 | The earlier build showed the same green/cyan pattern. Retest this voice-patch variant with the updated core. |
| 168 | Holy Girl Force Lakers III reaches its game logo. |
| 170 | Primal Space reaches Japanese story text. |
| 171 | X-Girl reaches FM sound / Soundboard / No Sound choice. |

After installing the updated catalog, a fresh `-Title 'Starfire'` ADB command
went directly to Starfire's choice menu. Fresh 18-second boots with the 2.5 MHz
GDC setting reached Desire's New Game / Load Game / Extras menu and Love
Potion's opening artwork; neither showed the former GDC warning. Captures are
under ignored `.downloads/run-starfire-title-launch.png` and
`.downloads/boot-survey-clock3/`.

## Follow-up work

Check Madou Monogatari in a longer live run; the 22-second black captures do
not establish a failure. Investigate Flix Mix's transient graphics and retest
the Ultima VIII voice-patch variant. Investigate Peret em Heru's return to DOS.
Determine Belloncho Body Inspection's BASIC
file-count answer.
Typed-number startup menus have an [app choice-panel design](startup-choices.md).
Arrow-and-Enter menus remain on the guest screen. Some archives contain multiple
episodes/disks; this first pass boots the preferred disk for each archive
rather than every disk in each ZIP.
