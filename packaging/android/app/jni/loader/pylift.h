/* pylift.h - the on-device lift in the embedded CPython (pylift.c). SPDX-License-Identifier: GPL-3.0-or-later */
#ifndef ORTA_PYLIFT_H
#define ORTA_PYLIFT_H

/* runs `python -m <module> <argv...>` in the one embedded interpreter (home = PYTHONHOME); the exit code */
int pylift_run_module(const char *home, const char *module, int argc, const char *const *argv);
void pylift_finish(void);

#endif
