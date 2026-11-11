/* touchpad.h - the touch controls as one virtual SDL gamepad (touchpad.c). SPDX-License-Identifier: GPL-3.0-or-later */
#ifndef ORTA_TOUCHPAD_H
#define ORTA_TOUCHPAD_H

/* after the launcher, before the game: the gamepad subsystem up + the virtual pad attached */
void touchpad_init(void);

/* external pads 10, built-in 5, the touch pad 0 - for the toolkit's xbox_InputSetPadPriority */
int touchpad_pad_priority(const char *name);

#endif
