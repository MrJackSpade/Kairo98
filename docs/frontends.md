# External frontend setup for Kairo98

Kairo98 can open a PC-98 disk image or ZIP sent by another Android frontend. You do not need to select a library folder in Kairo98 first. Keep archive extraction off in the frontend; Kairo98 reads disk images inside ZIPs. Choosing **Library** from a frontend-launched game closes Kairo98 and returns to the frontend.

## Setup guides

- [LaunchBox for Android](#launchbox-for-android)
- [ES-DE](#es-de)

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
