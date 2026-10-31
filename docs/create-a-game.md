# Create a game

Make a new game on a blank hard disk: MS-DOS formats it, a game's installer copies itself onto it, and the disk is kept as a game in your library that starts by itself. The MS-DOS system floppy and the installer disks can stay wherever they are (for example in Download): the Disks page puts them in a drive from anywhere on the device.

## 1. Make a blank disk

Open the library menu › **Manage library**. Under **New game**, choose a disk: **Hard disk · 40 MB · SASI** here. **Floppy disk · 1.2 MB · 2HD** and the 20 MB hard disk are made the same way.

![New game](screenshots/create-a-game/01-new-game.png)

Name it and press **Create**.

![Naming the new game](screenshots/create-a-game/02-name.png)

The blank disk is saved in your games folder, in a folder of its own, and listed as a game.

![The new game in the library](screenshots/create-a-game/03-listed.png)

## 2. Start MS-DOS with the new disk attached

Press **Play**. With nothing on the hard disk yet, the PC-98 says so (このハードディスクイメージはフォーマットされていません).

![The hard disk is not formatted](screenshots/create-a-game/04-not-formatted.png)

Open the menu › **Disks** › **Floppy A** › **Choose file…** and pick your MS-DOS system floppy; put the game's installer disk in **Floppy B** the same way.

![Choosing a disk](screenshots/create-a-game/05-choose-file.png)

![MS-DOS in Floppy A, the installer in Floppy B, the new hard disk](screenshots/create-a-game/06-disks.png)

Menu › **Restart game** (the drives are kept): MS-DOS starts from the floppy.

![MS-DOS](screenshots/create-a-game/07-ms-dos.png)

## 3. Format it

Type `format` and choose **固定ディスク** (fixed disk). Run **初期化** (Initialize) on the whole disk, then **領域確保** (Allocate): the full size, **システム** (System) **転送する** (transfer), **実行** (Run).

![FORMAT](screenshots/create-a-game/08-format.png)

![Allocating the disk](screenshots/create-a-game/09-allocate.png)

![System transferred](screenshots/create-a-game/10-system-transferred.png)

Leave FORMAT with Esc and **Restart game**: `dir c:` shows the new drive with MS-DOS on it.

![The formatted hard disk](screenshots/create-a-game/11-dir-c.png)

## 4. Install your game on it

Empty the floppy drives in **Disks** and **Restart game**: the hard disk now starts MS-DOS by itself, and is drive A:. Press Enter at the date and time.

![MS-DOS from the hard disk](screenshots/create-a-game/12-hard-disk-boots.png)

Put the installer disk in **Floppy A** (it is drive B: now) and run its installer, here `b:` then `install a:`.

![The installer disk in Floppy A](screenshots/create-a-game/13-installer-disk.png)

![Installing to the hard disk](screenshots/create-a-game/14-install.png)

To start the game straight away whenever the disk boots, write a start-up file:

```
echo cd \blockout>a:\autoexec.bat
echo jelly>>a:\autoexec.bat
```

Type `:` and `>` from the on-screen keyboard's **?123** page: a hardware keyboard's US layout doesn't match the PC-98's.

## 5. It is kept

Empty Floppy A in **Disks** and return to the library. The hard disk keeps everything MS-DOS and the installer wrote to it. After closing and reopening Kairo98, the new game is under **Continue**, and **Play** starts it from the hard disk alone, straight into the game.

![Starting from the hard disk alone](screenshots/create-a-game/15-kept.png)

![The game](screenshots/create-a-game/16-title.png)
