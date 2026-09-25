# Privacy at a glance

For the full policy, open **Settings → About Verzeta → Privacy policy**, which opens the policy on verzeta.com. This page is a short summary.

## Short version

Verzeta for Android sends data only to **your own desktop**. There is no Verzeta cloud service. The developer cannot read your data, because no developer-run service receives it.

## What the app stores on this device

Two small files in the app's private storage:

- Pairing tokens (one for each paired desktop)
- The host list (names, addresses and certificate fingerprints)

The app keeps no message cache, analytics, database or log files. When you play a generated audio clip, it keeps a temporary copy in its private cache and deletes it when playback ends.

## What the app sends off this device

Everything you type or attach in the app goes to **the desktop you paired with**, over a connection your phone opens. The desktop then processes it, and may pass it to the AI providers you set up on the desktop. Nothing passes through a Verzeta server.

## Notifications

If you allow notifications, the app shows one when an agent mentions you in a conversation you do not have open. The notification shows the agent's alias and the start of the message, so it can appear on your lock screen, depending on your Android notification settings. The app creates notifications on the device from its live connection to your desktop. No push service is used.

## What the app does not collect

The app uses two permissions: `INTERNET`, for the connection to your desktop, and `POST_NOTIFICATIONS`, for mention notifications. It does not access:

- Your location
- Your contacts
- Your calendar
- Your SMS, call log or phone state
- Your microphone or camera
- Your photo library (when you attach a file, Android's file chooser gives the app only the file you pick)
- Your advertising ID, Android ID, IMEI, MAC address or device serial number
- Any browsing or app-usage history

## Third-party code

The app contains no analytics, crash reporting, advertising, push-notification, Firebase or social-login code. The only network library is OkHttp, used only for the connection to your desktop.

## How to delete your data

Three steps, from least to most complete:

1. **Settings → Revoke this device** cancels this device's token on the desktop.
2. **Android Settings → Apps → Verzeta → Storage → Clear storage** removes the two local files from the device.
3. **Uninstall the app.** If Android backup is on, reinstalling later may restore the two files from your backup. A token you revoked in step 1 no longer works, even if it comes back.

For a complete cleanup, do all three in that order.

## Android backup

Both local files are included in Android's backup, so your pairings survive moving to a new phone. On Android 9 and later, Google encrypts the backup end to end when your device has a screen lock.
