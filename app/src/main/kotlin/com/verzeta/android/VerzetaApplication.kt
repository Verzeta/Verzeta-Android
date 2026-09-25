// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaApplication.kt
 * @brief Application subclass anchoring process-wide state. Currently a
 *        plain Application root; process-scoped dependencies that must
 *        outlive any single activity attach here.
 * @layer Frontend
 * @dependencies android.app.Application.
 */

package com.verzeta.android

import android.app.Application

/**
 * Application root for process-wide dependencies that are safe to share.
 */
class VerzetaApplication : Application()
