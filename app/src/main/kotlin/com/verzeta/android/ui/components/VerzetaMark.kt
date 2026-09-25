// SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
//
// SPDX-License-Identifier: LGPL-3.0-or-later

/**
 * @file VerzetaMark.kt
 * @brief Renders the Verzeta "V" brand mark — the same orange/slate logo
 *        that ships as the Android launcher icon foreground — in-app at a
 *        requested size.
 * @layer Frontend
 * @dependencies R.drawable.verzeta_mark; Jetpack Compose foundation
 *               Image.
 */

package com.verzeta.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.verzeta.android.R

/**
 * The Verzeta `V` brand mark — the orange/slate logo that ships as the
 * Android launcher icon foreground, rendered in-app at the requested size.
 *
 * The asset is `res/drawable-*dpi/verzeta_mark.png`, rendered from
 * `resources/icons/verzeta-studio.svg` on the host repo at
 * 48 / 72 / 96 / 144 / 192 px (mdpi → xxxhdpi). The PNG is padded to a
 * square so callers can keep passing a single `size` parameter; the V's
 * native aspect ratio is preserved with natural transparent whitespace
 * above and below.
 *
 * Used in:
 *  - HomeScreen top bar leading slot (default size 28).
 *  - ChatScreen assistant-message avatar (2 call sites, size 18).
 *
 * @param modifier Compose modifier chain.
 * @param size     Logical edge length in dp. Default 28 matches the
 *                 top-bar usage; the chat-thread variants call with 18.
 */
@Composable
fun VerzetaMark(
    modifier: Modifier = Modifier,
    size: Int = 28,
) {
    Image(
        painter = painterResource(R.drawable.verzeta_mark),
        contentDescription = "Verzeta",
        modifier = modifier.size(size.dp),
        contentScale = ContentScale.Fit,
    )
}
