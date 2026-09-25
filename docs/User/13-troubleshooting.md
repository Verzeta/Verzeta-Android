# Troubleshooting

The app has one network connection: the connection to your paired desktop. Almost every problem means the app cannot reach the desktop, the desktop does not accept this device's token, or the desktop refused a request. This page lists the messages you may see and how to fix each one.

When an action fails, on any screen, the app shows the reason in a message at the bottom of the screen. Tap the **×** to close it, or it closes by itself after about ten seconds. The same text also appears as a short line on the Home screen, the Pair device screen and the **Session** section of Settings. When the Home screen shows a red **Unavailable** label on the host card, tap it to read the full error in the **Host unavailable** dialog.

## Pairing fails

### "Enter the 6-digit pair code shown on the desktop"

The pair code must have exactly 6 digits. Enter the code from the desktop's Remote Access dialog.

### "Endpoint must start with ws:// or wss://", "Endpoint must include a host" or "Endpoint path must be /ws"

The Endpoint field needs the full URL from the desktop, for example `ws://192.168.1.42:9180/ws`. Copy it from **CLIENTS CAN REACH THIS HOST AT** in the desktop's Remote Access dialog. Include the port. If you leave it out, the app uses port 80 for `ws://` and port 443 for `wss://`, not the desktop's port 9180.

### "Pairing failed: invalid or expired pairing code"

The desktop could not match the code. Either it was mistyped, it is more than 5 minutes old, or it was already used. Each code works once. On the desktop, click **Generate pair code** again and enter the new code within 5 minutes.

### "Pairing failed" followed by a network error

The app could not reach the desktop. Check the following:

- Is Verzeta Studio running on the desktop, and does its Remote Access dialog say "Server is running"? Turn on **Auto-start at app launch** so the server starts with the app.
- Is the address in the endpoint correct? The desktop's Remote Access dialog lists the addresses under **CLIENTS CAN REACH THIS HOST AT**.
- Are both devices on the same network? If the desktop is on another network, is its port reachable from the internet?
- Does a firewall on the desktop or router block the port? The default port is 9180.

### "Trust anchor for certification path not found"

You used a `wss://` endpoint but left the fingerprint field empty. The desktop uses a self-signed certificate, so Android does not trust it on its own. Copy the **SHA-256 FINGERPRINT** from the desktop's Remote Access dialog into the **TLS certificate fingerprint (SHA-256)** field and pair again.

### "certificate fingerprint mismatch: expected ..., got ..."

The fingerprint you entered does not match the desktop's current TLS certificate. The value after "got" is the fingerprint the desktop is presenting now. Either:

- The fingerprint was copied wrongly. Copy it again from the desktop's Remote Access dialog, or
- The desktop's certificate changed since you paired, for example because someone clicked **Regenerate certificate**. Pair again with the new fingerprint.

If the certificate changed and nobody on your side changed it, someone may be intercepting the connection. Do not continue until you know why.

### Pairing fails right after you change TLS on the desktop

The error text depends on where the connection breaks. The endpoint must match the desktop's **TLS encryption** switch: use `ws://` when it is off and `wss://` when it is on. The desktop applies a TLS change when the server starts, so if you change it while the server is running, turn the server switch off and on again. The Remote Access dialog then shows the correct URL under **CLIENTS CAN REACH THIS HOST AT**.

## The connection drops

The dot in the chat header shows the connection state:

- Green: connected.
- Accent colour (the theme's main colour): connecting.
- Amber: the connection closed.
- Red: the connection failed.
- Grey: not connected to any desktop.

When the connection drops, the app reconnects with the stored token in the background. It keeps trying, waiting a little longer after each failed attempt (up to 30 seconds). When it is back, it reloads the open conversation so replies that finished while you were offline appear. To reconnect straight away, tap the host under **Other hosts** on the Home screen, or tap **Connect** next to it in Settings. If the desktop has revoked your token, pair again (see below).

## "The host did not answer in time. Check the connection and try again."

The app waited 30 seconds for the desktop to answer a request. The connection may have stopped working without closing, for example after the phone changed networks, or the desktop may have refused the request because too many arrived at once. Try again. If it keeps happening, check the connection dot and reconnect.

## "Reconnect failed: invalid or revoked token"

The desktop no longer accepts this device's token. It was revoked from the desktop or from another device. Pair again: tap **+** on the Home screen, enter the same endpoint, and enter a new code from the desktop's **Generate pair code** button.

## "Token missing for <host>. Enter a fresh pair code from the desktop."

This device has no token for that desktop, usually because you tapped **Revoke this device**. The app opens the Pair device screen with the host name and endpoint filled in. Generate a new code on the desktop, enter it, and tap **Pair**.

## "Verzeta Studio is not connected to the remote server. Check that it is running on the host."

This message, and "Connected, but Verzeta Studio is not running on the host. Start it on the desktop." right after connecting, mean the same thing. The host card on the Home screen then says "Verzeta Studio is not running on this computer".

The app reached the desktop's Remote Access server, but the Verzeta Studio app itself is not running or has not finished starting. The server runs as a separate process, so it can keep running after you close Verzeta Studio. Open Verzeta Studio on the desktop and try again. The message usually follows the name of what failed, for example "Message load failed:". In rare cases the error reads "bridge offline" instead, and the fix is the same.

## "unknown op: ..."

The desktop does not recognise a request the app sent. This usually means Verzeta Studio on the desktop is older than the app. Update Verzeta Studio to the same release as the app.

## Messages do not send

If you tap send and nothing seems to happen:

1. Check the connection dot in the chat header. If it is not green, the app is not connected.
2. If the message has more than 4096 characters, the app keeps it in the composer and says how long it is. Shorten it or send it in parts.
3. If the chat shows "Message queued until the host finishes its current turn", the desktop is still working on an earlier request. Your message is sent when it finishes.
4. If a reply seems stuck, tap the stop button, then send again.

## An attachment is refused

The composer shows one of these messages:

- "File too large (... KB; 4 MB max)": each file can be up to 4 MB.
- "Maximum 8 attachments per message": send the rest in another message.
- "Attachments can total at most 11 MB per message": all files in one message can add up to 11 MB.
- "Filename can't contain /, \, .. or start with .": rename the file and attach it again.

## "File too large. Project documents can be at most 11 MB." when uploading a project document

Project documents can be up to 11 MB each. Split or shrink the file, or upload it from the desktop. The same filename rule as for attachments applies.

## "Saved a truncated copy. The full file is ... bytes."

Only part of the artifact arrived from the desktop. Open the file on the desktop to get all of it.

## Mention notifications do not appear

Check that notifications are allowed for Verzeta in Android Settings → Apps → Verzeta → Notifications. The app gets mentions over its live connection to the desktop and uses no push service, so a mention arrives only while the app is open or was recently in the background.

## "Sandbox unavailable on host" in Canvas

The desktop runs canvas scripts in a bubblewrap sandbox on Linux. On Windows, or on Linux when bubblewrap is missing or cannot start, this banner appears and scripts run without a sandbox, at your own risk. Install bubblewrap on a Linux desktop to remove the banner.

## Tools, skills or MCP servers cannot be turned on or off

This is by design. Those changes are made on the desktop (see Host catalogs). In the app, these lists are read-only.

## The app crashes when it starts

Try these steps:

- Restart the app.
- Clear the app's cache (Android Settings → Apps → Verzeta → Storage → **Clear cache**). Clearing the cache keeps your pairings. **Clear storage** removes them.
- Reinstall the app.

If the problem continues, email hello@verzeta.com. Include the app version from **Settings → About Verzeta**.

## Remove everything

There are three steps, from least to most complete:

1. **Settings → Revoke this device** cancels this device's token on the desktop.
2. **Android Settings → Apps → Verzeta → Storage → Clear storage** removes the pairing tokens and host list from this device.
3. **Uninstall the app.** If Android backup is on, reinstalling the app later may restore the host list and tokens from your backup. A token you revoked in step 1 no longer works, even if it comes back.

For a complete cleanup, do all three in that order.
