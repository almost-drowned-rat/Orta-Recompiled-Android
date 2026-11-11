/* probe_dl.c - stand-in for the game library built on the device (O, 2026-11-11). The smoke test copies this
 * .so from nativeLibraryDir into the app's internal storage (filesDir) and dlopen()s it from there - the route the
 * real libORTA_game.so takes (Android 10+ W^X forbids execve from app data; dlopen should still be allowed).
 * SPDX-License-Identifier: GPL-3.0-or-later */
int orta_probe_answer(int x);
int orta_probe_answer(int x)
{
    return x * 2 + 1;
}
