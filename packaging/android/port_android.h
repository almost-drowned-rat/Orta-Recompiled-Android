/* SPDX-License-Identifier: GPL-3.0-or-later
 * port_android.h - Orta Recompiled's names for the option-c Android template (templates/android; since Android
 * 1.0.0, 2026-11-24 - before that Orta carried its own copy of what became the template). Next to it the build
 * script writes port_icon.h (assets/icon_64.bmp as a BMP array). */
#ifndef PORT_ANDROID_H
#define PORT_ANDROID_H

#define PORT_TAG              "ORTA"
#define PORT_TITLE            "Panzer Dragoon Orta Recomp"
#define PORT_GAME_LIB         "libORTA_game.so"
#define PORT_GAME_MAIN        "orta_game_main"        /* src/main.c on Android */
#define PORT_GAME_NAME        "ORTA"
#define PORT_CONFIG_PKG       "orta_config"           /* only the dev lift test (loader.c LIFTTEST) */
#define PORT_MEDIA_ENV        "ORTA_MEDIA_DIR"        /* src/main.c: the boot animation */
#define PORT_TOUCH_NAME       "Orta touch controls"
#define PORT_TOUCH_AIM        "orta_touch_aim"        /* src/recomp_manual.c: aiming by touch (Android v0.2.0) */

#define PORT_LAUNCHER_GAME    "Panzer Dragoon Orta"
#define PORT_LAUNCHER_DISC    "Panzer Dragoon Orta (Europe)"
#define PORT_TITLE_ID         0x4947002Bu
#define PORT_VERSIONS         { "Europe", "57213FF35A19807DEF664F9018CC2923815DFA1F" },

#endif
