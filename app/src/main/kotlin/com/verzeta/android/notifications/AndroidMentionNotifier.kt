// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AndroidMentionNotifier.kt
 * @brief NotificationManager-backed implementation of the MentionNotifier
 *        seam. Posts one updatable notification per conversation when an
 *        agent @-mentions the user and deep-links taps back into the
 *        conversation.
 * @layer Service
 * @dependencies MainActivity (tap deep-link target), MentionNotifier
 *               interface, AndroidX NotificationCompat /
 *               NotificationManagerCompat, Android NotificationChannel +
 *               PendingIntent.
 */

package com.verzeta.android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.verzeta.android.MainActivity
import com.verzeta.android.MentionNotifier
import com.verzeta.android.R

/**
 * NotificationManager-backed [MentionNotifier].
 * Raises one notification per conversation (the conversation id hashes
 * to the notification id, so a second mention in the same chat updates
 * in place instead of stacking). Tapping deep-links back into
 * [MainActivity] with the conversation id as an extra; the activity
 * forwards it to MainViewModel.openConversation.
 *
 * Channel: "mentions" (default importance — audible but not intrusive).
 * On API 33+ the POST_NOTIFICATIONS runtime permission gates delivery;
 * [MainActivity] requests it once on first launch. When the permission
 * is missing we drop silently — never crash a chat over a notification.
 */
class AndroidMentionNotifier(private val context: Context) : MentionNotifier {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mentions",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "An agent @-mentioned you in a conversation"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    override fun showMention(convId: String, alias: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CONV_ID, convId)
        }
        val pending = PendingIntent.getActivity(
            context,
            convId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (alias.isBlank()) "You were mentioned" else "@$alias mentioned you"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text.take(240))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000)))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context)
            .notify(convId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "mentions"

        /** Intent extra carrying the conversation to open on tap. */
        const val EXTRA_CONV_ID = "com.verzeta.android.extra.CONV_ID"
    }
}
