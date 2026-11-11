/* launcher_android.c - the toolkit's launcher (option-c src/launcher) with Orta's names on Android (O, 2026-11-11).
 *
 * The twin of launcher/main.c (Windows / Linux): the same XrLauncherConfig. Android differences:
 *   - the root is the app's internal storage (no portable folder; maintainer 2026-11-11), the read-only tools
 *     folder <files>/tools (XR_LAUNCHER_TOOLS, set by loader.c);
 *   - --no-start: an app cannot start a second program as the game - the launcher builds libORTA_game.so into the
 *     root and returns, loader.c dlopen()s it in this process.
 * SPDX-License-Identifier: GPL-3.0-or-later */
#include "launcher.h"
#include "port_icon.h"

#include <SDL3/SDL.h>
#include <jni.h>

/* "Choose image...": SDL's Android file dialog returns a content:// URI; the installer reads by path. Java
 * (OrtaActivity.importContent) copies the document into <files>/data/import/<its name> and returns that path
 * (a /proc/self/fd/N path of the picker's descriptor was refused: "permission denied", S23 2026-11-11);
 * loader.c deletes the copy after the launcher. */
static int orta_open_uri(const char *uri, char *path, size_t n)
{
    JNIEnv *env = (JNIEnv *)SDL_GetAndroidJNIEnv();
    jobject act = (jobject)SDL_GetAndroidActivity();
    jclass c;
    jmethodID m;
    jstring ju, jr;
    const char *r;
    int ok = 0;
    path[0] = 0;
    if (!env || !act) return 0;
    c = (*env)->GetObjectClass(env, act);
    m = (*env)->GetStaticMethodID(env, c, "importContent", "(Ljava/lang/String;)Ljava/lang/String;");
    if (m) {
        ju = (*env)->NewStringUTF(env, uri);
        jr = (jstring)(*env)->CallStaticObjectMethod(env, c, m, ju);
        if (jr) {
            r = (*env)->GetStringUTFChars(env, jr, NULL);
            SDL_snprintf(path, n, "%s", r);
            ok = path[0] != 0;
            (*env)->ReleaseStringUTFChars(env, jr, r);
            (*env)->DeleteLocalRef(env, jr);
        }
        (*env)->DeleteLocalRef(env, ju);
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, c);
    (*env)->DeleteLocalRef(env, act);
    return ok;
}

int orta_android_launcher(int argc, char **argv)
{
    /* the supported versions (CLAUDE.md "Title decisions"; src/main.c orta_game_setup) */
    static const xr_game_version versions[] = {
        { "Europe", "57213FF35A19807DEF664F9018CC2923815DFA1F" },
    };
    XrLauncherConfig c = {
        "Panzer Dragoon Orta",
        "Panzer Dragoon Orta (Europe)",
        "Panzer Dragoon Orta Recomp",
        "libORTA_game.so",
        0x4947002Bu,
        versions,
        sizeof versions / sizeof versions[0],
        g_port_icon_bmp,
        sizeof g_port_icon_bmp,
        NULL,                           /* slug: no AppImage folder */
        orta_open_uri,
    };
    char *av[8];
    int n = 0, i;
    av[n++] = argc > 0 ? argv[0] : "orta";
    for (i = 1; i < argc && n < 6; i++)
        av[n++] = argv[i];
    av[n++] = "--no-start";
    av[n] = NULL;
    return xr_launcher_main(&c, n, av);
}
