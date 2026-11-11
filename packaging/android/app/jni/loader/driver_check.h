/* driver_check.h - before the first-start build: a Turnip driver for Adreno GPUs below Vulkan 1.3 (driver_check.c).
 * SPDX-License-Identifier: GPL-3.0-or-later */
#ifndef ORTA_DRIVER_CHECK_H
#define ORTA_DRIVER_CHECK_H

/* 1 = go on (the device is fine, or a driver was chosen); 0 = the player quit */
int driver_check(const char *files);

#endif
