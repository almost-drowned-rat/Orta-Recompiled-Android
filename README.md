<p align="center">
  <img src="assets/icon_256.png" alt="Orta Recompiled for Android" width="160">
</p>

# Orta Recompiled - Android

An **experimental Android port** of [Orta Recompiled](https://github.com/almost-drowned-rat/Orta-Recompiled), the
static recompilation of *Panzer Dragoon Orta* (Xbox, Europe / PAL). One app: you pick your own disc image, the game
is translated and compiled **on your device** once, and then it plays - with touch controls or a gamepad.

> This project contains **no game data, no game code and no keys**. You need your own copy of the game (PAL disc
> image). The app never contains any of the game's code: it is built on your device from your own disc.

> **AI disclosure:**  AI assistance yes (Claude Code by Anthropic).

> **Just a small experiment.** This port was a small experiment to see how - and whether at all - the game would run
> on Android. It is not a finished release and it is not supported the way the PC version is.

> **I am sorry** for everyone whose device has no **Vulkan 1.3** and cannot use a **Turnip** driver either: the game
> needs one of the two, and on those devices it will not run. There is nothing I can do about that from the app's side.

> **A Turnip driver is required.** Use the app with a **Turnip** driver (Mesa's open Vulkan driver for Adreno GPUs),
> not with the phone's own driver: the system drivers showed graphics glitches or were too old. Tested with
> **purple-turnip T30** - [`turnip_mrpurple_T30-toasted.adpkg.zip`](https://github.com/MrPurple666/purple-turnip/releases/tag/vturnip_mrpurple_T30-toasted.adpkg)
> (Mesa Turnip 26.3.0, Vulkan 1.4.359, by MrPurple666). Download that file to your device and choose it in the app
> (see *How it works*). The driver is not part of the app.

> **Only tested on Snapdragon.** I only have Android devices with a **Snapdragon 8 Gen 1** and a **Snapdragon 8 Gen 2**
> (Adreno 730 / 740), so I cannot test any other chip (Exynos, Tensor, MediaTek / Mali, older Snapdragons). And since
> this is just a small experiment, I will probably not put as much work into it as into the other ports.

---

## Status

**Source only for now.** A ready-to-install APK will follow here as a release once the matching toolkit sources are
published and the third-party licence texts are inside the app.

Tested on:

| Device | Chip / GPU | Driver | Result |
|---|---|---|---|
| AYN Odin 2 Portal | Snapdragon 8 Gen 2 / Adreno 740 | purple-turnip T30 | runs, borderless, built-in controls work (the system driver, Vulkan 1.3.128 on Android 13: graphics glitches) |
| Samsung Galaxy Tab S8 | Snapdragon 8 Gen 1 / Adreno 730 | purple-turnip T30 | runs, ~60 fps (the system driver offers only Vulkan 1.1) |
| Samsung Galaxy S23 | Snapdragon 8 Gen 2 / Adreno 740 | system driver (Vulkan 1.3) | ran in the first tests, ~60 fps; use Turnip T30 here too |

## Requirements

- Android 10 or newer, **arm64** (64-bit ARM)
- An **Adreno GPU** and a **Turnip driver package** (`.adpkg.zip`) - **required**; tested with purple-turnip T30
  (link above). Without Turnip the game needs a system driver with **Vulkan 1.3**, and even then it may show
  graphics glitches. No driver is shipped with the app.
- Your own **Panzer Dragoon Orta (Europe)** disc image (`.iso`)
- Free storage: about **7 GB** during the first start (the image is copied in and extracted), about **3.5 GB** after it

## How it works

1. Install the app and start it.
2. Choose the Turnip driver package (`turnip_mrpurple_T30-toasted.adpkg.zip` from your Download folder): the app asks
   for it on devices whose own driver is too old; on all others open **BACK > Graphics driver > Choose a Turnip
   driver package** once the game runs. The app restarts with Turnip. You can switch back to the system driver there
   at any time.
3. Choose your disc image in the system file picker. It is copied into the app and extracted (the copy is deleted
   afterwards; your own file stays where it is).
4. The game's code is translated and compiled on the device - **once**, about 8-10 minutes. Keep the screen on.
5. The game starts. Every later start goes straight into the game.

**Frame rate at first:** while you play, the frame rate drops now and then at the beginning and gets better the longer
you play. That is the shader compilation: every new effect, scene or menu needs its shaders built once on the device,
and they are cached for the next time. After a while (and on every later start) it runs much smoother.

**Storage:** everything lives in the app's own storage. **Uninstalling the app deletes your saves** - keep that in mind.

## Controls

- **Touch:** a floating stick on the left, A B X Y, WHITE / BLACK, LT / RT and START. BACK, L3 / R3, the D-pad and
  a right stick exist too, hidden by default.
- **Gamepads** work out of the box. With the touch buttons on *Automatic* they hide while a gamepad is used. Every
  controller plays as player 1 (the game is single-player), so a handheld's own controls, a touch pad and an external
  pad all work at the same time.
- **BACK** opens a menu that slides in from the left: the graphics driver (switch to a Turnip package or back to the
  system driver at any time), the connected controllers, touch buttons on / off / automatic, move and resize them,
  button size, hide single buttons, opacity, stick sensitivity, and the licences. The game keeps running behind it.

## Building

The APK is built on Linux with `tools/build_android.sh` (Android SDK 36, NDK r30, JDK 21, Gradle 9.9.1; the script
lists every path it reads as environment variables). It needs the toolkit,
[xboxrecomp-option-c](https://github.com/almost-drowned-rat/xboxrecomp-option-c), and its Android runtime SDK and
on-device compiler (made by the toolkit's `tools/android` scripts). The Android parts of the toolkit will be
published there.

## Licence

GPL-3.0-or-later (see `LICENSE`). Third-party parts the app uses: SDL3 (zlib), CPython (PSF), capstone (BSD-3),
libadrenotools (BSD-2), LLVM / clang / lld (Apache-2.0 with LLVM exception), the xboxrecomp toolkit (MIT, with
LGPL-2.1 parts from xemu); origins in `docs/sources.md`. A Turnip driver (Mesa, MIT) is never shipped - you choose it.

*Panzer Dragoon Orta* is a trademark of SEGA. This project is not affiliated with or endorsed by SEGA or Microsoft.
