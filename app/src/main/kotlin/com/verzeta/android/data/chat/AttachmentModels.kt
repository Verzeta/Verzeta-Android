// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file AttachmentModels.kt
 * @brief Attachment, artifact, and generated-media models. Wire shapes
 *        mirror what the host's `ChatController` Q_INVOKABLE forwarders
 *        emit (snake_case, matches the `msg.*` / `conv.*` convention).
 *        `path` fields are host-side absolute filesystem paths —
 *        informational only on Android, never passed back over the wire;
 *        downloads route through `{conv_id, file_name}` with the host
 *        resolving via the same extraction walk the desktop's
 *        ArtifactsModel uses.
 * @layer Model
 * @dependencies None — pure Kotlin data declarations.
 */

package com.verzeta.android.data.chat

/**
 * One artifact row from `artifact.list_for_conv` or `artifact.added`.
 * Mirrors the desktop's ArtifactsModel roles. `path` may be empty for
 * plan-artifact rows produced by submit_result — their content lives
 * only in the assistant message, not on disk.
 */
data class ArtifactRowUi(
    val sourceMsgId: String,
    val path: String = "",
    val fileName: String,
    val toolName: String = "",
    val planId: String = "",
    val stepId: String = "",
    val stepTitle: String = "",
    val planGoal: String = "",
    val submittedBy: String = "",
)

/**
 * One row from `image.list_for_conv` / `audio.list_for_conv` or its
 * `*.generated` event. `totalBytes` is -1 when the file vanished
 * between listing and the event firing.
 */
data class GeneratedFileUi(
    val fileName: String,
    val totalBytes: Long = 0L,
    val exists: Boolean = true,
)

/**
 * Persisted attachment metadata from `attachment.list_for_message`.
 * The current chat flows don't actually persist attachments (the
 * desktop's `sendMessageWithAttachments` reads files inline and feeds
 * the LLM directly), so this typically returns `[]`. Included for
 * forward-compat when host adds attachment-persisting flows.
 */
data class AttachmentMetaUi(
    val id: String,
    val messageId: String,
    val type: AttachmentType,
    val fileName: String,
    val mimeType: String = "",
    val hasInlineData: Boolean = false,
    val hasPath: Boolean = false,
    val totalBytes: Long = -1L,
    val createdAt: String = "",
)

/**
 * Persisted attachment category from the host's attachment table.
 * [Unknown] absorbs wire values this client version doesn't recognise.
 */
enum class AttachmentType(val wireValue: String) {
    Image("image"),
    Audio("audio"),
    File("file"),
    Code("code"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): AttachmentType =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/**
 * Generic download payload returned by `attachment.get` /
 * `artifact.download` / `image.download` / `audio.download`.
 *
 * `truncated` is true when the host clamped the response to
 * `max_bytes` (or the absolute 4 MiB cap). Render a warning + offer
 * "open on desktop" fallback in that case.
 */
data class DownloadPayloadUi(
    val fileName: String,
    val mimeType: String = "",
    val totalBytes: Long = 0L,
    val contentBase64: String = "",
    val truncated: Boolean = false,
)

/**
 * One outgoing attachment staged in the composer before send. The
 * composer base64-encodes the picked file's bytes inline so the
 * `msg.send_with_attachments` request can ship in one round-trip.
 *
 * Caps the UI enforces upfront:
 *   - per-attachment raw bytes ≤ 4 MiB (≈ 5.4 MiB base64)
 *   - total raw bytes across all attachments ≤
 *     [com.verzeta.android.remote.RemoteRepository.MAX_UPLOAD_RAW_BYTES]
 *     (11 MiB), so the base64 frame fits OkHttp's 16 MiB outgoing
 *     WebSocket queue
 *   - max 8 attachments per send
 *
 * The host re-validates each attachment and answers `invalid_params` when
 * one cannot be stored.
 */
data class OutgoingAttachment(
    val fileName: String,
    val mimeType: String,
    val rawBytes: Long,
    val contentBase64: String,
)
