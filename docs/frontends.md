# External frontend setup for Kairo98

Kairo98 can open a PC-98 disk image or ZIP sent by another Android frontend. You do not need to select a library folder in Kairo98 first. Keep archive extraction off in the frontend; Kairo98 reads disk images inside ZIPs. Choosing **Library** from a frontend-launched game closes Kairo98 and returns to the frontend.

## Setup guides

- [LaunchBox for Android](#launchbox-for-android)
- [ES-DE](#es-de)
- [iiSU](#iisu)
- [Beacon](#beacon)

## LaunchBox for Android

Import your PC-98 files into LaunchBox's **NEC PC-9801** platform. Open that platform, tap the **top-right three-dot menu → Emulator Settings**, and choose **Custom Emulator** as the default emulator (without “With Code”). Enter:

| Setting | Value |
| --- | --- |
| Custom Emulator Package Name | `com.loxifi.kairo98` |
| Custom Emulator Activity Name | `com.loxifi.kairo98.Launch` |
| Custom Emulator ROM Path Key | `ROM` |

Turn **Extract ROM Archives** off. No separate launch command is needed.

## ES-DE

Add this Android package rule to ES-DE's custom `es_find_rules.xml`:

```xml
<emulator name="KAIRO98">
  <rule type="androidpackage">
    <entry>com.loxifi.kairo98/com.loxifi.kairo98.Launch</entry>
  </rule>
</emulator>
```

In the PC-98 system's custom `es_systems.xml` configuration, add this command and select it as the emulator:

```xml
<command label="Kairo98">%EMULATOR_KAIRO98% %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%</command>
```

See the [ES-DE Android configuration guide](https://gitlab.com/es-de/emulationstation-de/-/blob/master/INSTALL.md) for custom file locations and system override syntax. Pass the original disk image or ZIP to Kairo98.

## iiSU

iiSU reads its emulator list from two files in `Android/media/com.iisulauncher/iiSULauncher/Emuladores/` on shared storage. If your file manager cannot open `Android/media`, use Android's built-in Files app. Back up both files before editing them.

In `emuladores.json`, find the console whose `shortName` is `pc98` and add this object to its `emulators` array:

```json
{
  "id": "KAIRO98",
  "name": "Kairo98 (Standalone)",
  "routeType": "uri",
  "commands": [
    {
      "description": "Kairo98",
      "command": "com.loxifi.kairo98/com.loxifi.kairo98.Launch -a android.intent.action.VIEW -d %ROM_URI%"
    }
  ],
  "packages": [
    "com.loxifi.kairo98"
  ]
}
```

Add this object to the array in `supported_emulators.json`:

```json
{
  "name": "KAIRO98",
  "packages": [
    "com.loxifi.kairo98"
  ]
}
```

Put your games in the `pc98` folder of your iiSU ROM folder. Select the NEC PC-9800 Series console, open **Quick Settings → Console Settings → Edit Console Settings**, and choose **Kairo98 (Standalone)** as the emulator. An iiSU update can replace these files; add the entries again if Kairo98 disappears from the list.

## Beacon

Open **Settings** and add a platform:

| Setting | Value |
| --- | --- |
| Platform type | **Custom** |
| Name / Short name | Any, for example `PC-98` / `PC98` |
| Player app | **Kairo98** |
| ROMs folder | Your PC-98 game folder |

Scroll down, open **Advanced**, turn on **Use custom launch**, accept the warning, and enter this **am start command**:

```text
am start -n com.loxifi.kairo98/com.loxifi.kairo98.Launch -a android.intent.action.VIEW -d {file_uri}
```

Leave **File handling** at **Default** and save. Without the custom launch command, Beacon opens Kairo98's library instead of the game.

The app does not register a generic Android “Open with” file handler. Select games inside the app or use the explicit component configured above; APKs and unrelated documents should not offer the emulator as a handler.
