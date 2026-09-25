# Verzeta™ Privacy Policy

_Last updated: 2026-09-25_

Verzeta for Android is the companion app for the Verzeta Studio desktop application. **The Android app does not run AI or language-model code on the device.** It pairs with your own copy of Verzeta Studio on your desktop, and all processing happens on that desktop, which you control.

This is the privacy policy for Verzeta for Android. In the app, the **Settings → About Verzeta → Privacy policy** row opens this policy on verzeta.com.

## Who publishes this app

Verzeta for Android is published by **Aditya Mehra** (sole developer). There is **no Verzeta cloud service, no intermediary server and no developer-accessible data store** between the Android app and your desktop. The app makes network connections **only to the desktops you pair it with**.

## What the app stores on the device

The app stores **two small files** in its private storage:

- **Pairing tokens.** One random 256-bit credential for each paired desktop, issued by Verzeta Studio on that desktop during pairing. Tokens are stored as plain text in the app's private `SharedPreferences` (Android's standard sandboxed storage). They are not wrapped in the Android Keystore, so that Android backup can carry them to a new phone without making you pair every desktop again. The desktop stores only a SHA-256 hash of each token and lets you revoke any device in one click (**Settings → Remote Access → Manage Remote Access**, under **Paired devices**), so a leaked token can be cancelled at once.
- **Host list.** The address, port and scheme (`ws://` or `wss://`) of each Verzeta Studio desktop you have paired with, the display name you chose, and, for `wss://` endpoints, the SHA-256 certificate fingerprint you entered. This file contains no tokens.

The app writes no database, analytics buffer, cookie or log file. When you play a generated audio clip, the app keeps a temporary copy in its private cache and deletes it when playback ends.

## What the app sends off the device

When you send a message, attach a file, upload a project document, edit a canvas or change a setting, the app sends that data **over a connection your phone opens to the desktop you paired with**. The desktop processes it, may pass it to the language-model providers you set up there (for example Ollama on your own computer, OpenAI, Anthropic or Gemini, as you choose on the desktop), and sends the responses back.

No data passes through Verzeta servers. The app connects directly to the desktop address you entered when pairing.

## What the app does not collect or send

The app does **not** read, store or send:

- Your name, email address, phone number or any government ID
- Your location (no `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` permission)
- Your contacts (no `READ_CONTACTS`)
- Your calendar (no `READ_CALENDAR`)
- Your SMS messages or call log (no `READ_SMS` or `READ_CALL_LOG`)
- Audio from your microphone (no `RECORD_AUDIO`)
- Images from your camera (no `CAMERA`)
- Your photo library (when you choose to attach a file, Android's file chooser hands the app only that file)
- The Android advertising ID
- The Android ID (`Settings.Secure.ANDROID_ID`)
- IMEI, IMSI, MAC address, device serial number or Wi-Fi BSSID
- Browser history, app activity or any background telemetry
- Any push-notification token (Firebase Cloud Messaging is not included in the app, so no push tokens are created)
- Any crash report or stack trace

The app contains no third-party analytics, crash reporting, advertising, push-notification or social-login code. The only library that uses the network is `OkHttp`, used only for the connection you open to your desktop.

## Notifications

If you allow notifications, the app shows a system notification when an agent mentions you in a conversation you do not have open. The notification shows the agent's alias and the start of the message, so it can appear on your lock screen, depending on your Android notification settings. Notifications are created on the device from the live connection to your desktop. No push service is used.

## What the developer sees

**Nothing.** The developer runs no server that receives your data. The developer cannot read your chat messages, see your desktop's settings or observe your activity, because there is no service that could.

## Security

- **Transport security.** When you pair over `wss://`, the app uses TLS. When you turn on TLS encryption in the desktop's Remote Access dialog, the desktop creates a self-signed certificate. The app pins that certificate by its SHA-256 fingerprint, which you copy from the desktop's dialog into the Pair device screen when you pair. The app refuses any later connection where the certificate does not match.
- **Unencrypted `ws://` connections.** Plain connections are permitted by the app's `network_security_config.xml`. Use them only on a network you trust, for example when pairing with a desktop on the same Wi-Fi, or when an Android emulator reaches the desktop at `ws://10.0.2.2:9180/ws`. For a desktop reachable from the internet, use `wss://` with either a certificate from a public certificate authority or a self-signed certificate with a pinned fingerprint.
- **Local file security.** The two files live in the app's private storage (`MODE_PRIVATE`), which only the Verzeta app can read under standard Android sandboxing.
- **Android backup.** Both files are included in Android's backup to your Google account. On Android 9 and later, Google encrypts the backup end to end when your device has a screen lock.

## Revoking access

Two ways revoke the pairing, and a third removes it from this device only:

1. **From the desktop.** In Verzeta Studio, open **Settings → Remote Access → Manage Remote Access**, find the Android device under **Paired devices**, and click **Revoke**. The desktop cancels the token at once, and the next request from that device is refused.
2. **From the Android app.** Open **Settings** and tap **Revoke this device**. The desktop cancels the token, and the app clears its data for that desktop.
3. **Uninstall the app**, or use Android's **Settings → Apps → Verzeta → Storage → Clear storage**. This removes both files from the device. The desktop keeps the token until you revoke it with option 1, because uninstalling does not revoke it.

## Children's privacy

This app sends what you type to language models on your desktop, which may pass it to third-party AI providers you have set up. The output of those models varies and depends on your setup. The app is intended for people aged 13 and over and is not marketed to children under 13. The app collects no information that would allow age verification and does not knowingly contain content directed at children.

## Your data rights

The developer runs no servers and stores no user data, so there is no data controller to make GDPR or CCPA requests to. All data the app produces lives either:

- on your own device (the two files), which you can wipe at any time by uninstalling the app or using "Clear storage", or
- on your own paired desktop, which you run and control completely.

For data stored on the desktop, see Verzeta Studio's own privacy policy.

## Changes to this policy

When this policy changes, we update the date at the top of this page and publish the new version at the link in the app's **About Verzeta → Privacy policy** row. Material changes (new kinds of data, new destinations or new third parties) may also be noted in the app's release notes.

## Contact

- Email: <hello@verzeta.com>
