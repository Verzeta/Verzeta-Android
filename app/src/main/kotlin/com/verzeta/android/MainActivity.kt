// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file MainActivity.kt
 * @brief Single-activity Compose entry point. Applies edge-to-edge system-bar
 *        theming, installs the platform mention notifier on the view model,
 *        requests the notification runtime permission, and routes
 *        mention-notification taps into the active conversation.
 * @layer Frontend
 * @dependencies MainViewModel, AndroidMentionNotifier, VerzetaApp shell,
 *               VerzetaTheme + window-size utilities, AndroidX Activity,
 *               Compose Material3 window-size-class, Jetpack WindowManager.
 */

package com.verzeta.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.verzeta.android.notifications.AndroidMentionNotifier
import com.verzeta.android.ui.shell.VerzetaApp
import com.verzeta.android.ui.theme.LocalVerzetaWindowSize
import com.verzeta.android.ui.theme.VerzetaTheme
import com.verzeta.android.ui.theme.rememberVerzetaWindowSize

/**
 * Single-activity entry point. The activity opts in to edge-to-edge so that the
 * Compose theme drives system-bar appearance — this is what fixes the previous
 * permanently-white status and navigation bars. All UI is owned by [VerzetaApp].
 *
 * Notification plumbing: installs the NotificationManager-backed
 * [AndroidMentionNotifier] on the view model, requests the API 33+
 * POST_NOTIFICATIONS runtime permission once, and routes
 * mention-notification taps (the conversation id rides the intent
 * extra) into MainViewModel.openConversationWhenConnected — covering both
 * the cold-start (onCreate) and already-running (onNewIntent) paths. On a
 * cold start the conversation opens once the host has reconnected; an
 * activity recreated after rotation does not handle the launch intent
 * again.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge with system bars that auto-flip light/dark per theme.
        // SystemBarStyle.auto reads the lightScrim/darkScrim — we provide
        // matching transparent values; the Compose Theme adjusts the appearance
        // light flags at runtime.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        viewModel.notifier = AndroidMentionNotifier(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Only a fresh launch carries a new tap. A recreated activity (for
        // example after rotation) gets the same launch intent again.
        if (savedInstanceState == null) handleMentionIntent(intent)

        setContent {
            VerzetaTheme {
                // Provide WindowSizeClass via CompositionLocal
                // so every screen can branch its layout based on the current
                // form factor (phone / tablet / TV / desktop). Recomputed
                // automatically on configuration change (DeX / ChromeOS /
                // foldable fold-unfold) because calculateWindowSizeClass
                // reads the activity's current window metrics.
                @OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
                val windowSizeClass = calculateWindowSizeClass(this)
                // A folding feature is reported only on a foldable's inner
                // screen, so this tells the unfolded inner screen apart from
                // the cover screen and from an ordinary phone or tablet.
                val activity = this
                val layoutInfo by remember(activity) {
                    WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
                }.collectAsStateWithLifecycle(initialValue = null)
                val hasFoldingFeature =
                    layoutInfo?.displayFeatures?.any { it is FoldingFeature } == true
                val verzetaWindow = rememberVerzetaWindowSize(windowSizeClass, hasFoldingFeature)
                CompositionLocalProvider(
                    LocalVerzetaWindowSize provides verzetaWindow,
                ) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    VerzetaApp(state = state, actions = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMentionIntent(intent)
    }

    /** Opens the conversation a tapped mention notification points at. */
    private fun handleMentionIntent(intent: Intent?) {
        val convId = intent?.getStringExtra(AndroidMentionNotifier.EXTRA_CONV_ID)
            ?: return
        if (convId.isNotBlank()) viewModel.openConversationWhenConnected(convId)
    }

    private companion object {
        const val TRANSPARENT = 0
    }
}
