# Frequently asked questions

## Does Verzeta have a cloud service?

**No.** There is no Verzeta server. The app talks only to the desktops you pair it with. The developer (Aditya Mehra) runs no service that receives your data.

## Where do the AI models run?

On your desktop. Verzeta Studio, the desktop app, runs language models on your own computer through Ollama, or calls cloud providers such as OpenAI, Anthropic and Gemini with the API keys you set up there. The Android app sends what you type to the desktop and shows the desktop's replies.

## Can I use Verzeta for Android without a desktop?

No. Without a paired desktop, the app can only pair with one. Verzeta Studio is free, open source, and runs on Linux and Windows.

## Which version of Verzeta Studio do I need?

Use the same release number on the desktop and the phone. If the app shows "unknown op", update Verzeta Studio.

## Is the connection secure?

When you pair over `wss://`, the connection uses TLS. When you turn on TLS encryption on the desktop, it creates a self-signed certificate, and the app pins it by its SHA-256 fingerprint. If the fingerprint does not match, the app refuses the connection. Plain `ws://` is unencrypted, so use it only on a network you trust, such as your home Wi-Fi. For a desktop reachable from the internet, always use `wss://`.

## What is stored on my phone?

Two files in the app's private storage:

- The list of desktops you have paired with (`verzeta_hosts.xml`)
- The tokens those desktops issued (`verzeta_remote_auth.xml`)

The app keeps no message cache, database or analytics. When you play a generated audio clip, it keeps a temporary copy until playback ends.

## Are my pairings backed up?

Yes, if Android backup is on. Both files are included in the backup, so your pairings survive reinstalling the app or moving to a new phone. On Android 9 and later, Google encrypts the backup end to end when your device has a screen lock.

## Why is the pairing token stored as plain text instead of in the Android Keystore?

So that Android backup can carry it to a new phone. The Android Keystore does not include its keys in backups, so a token encrypted with the Keystore could not be read on a new device. Each token is a random 256-bit credential for one device. The desktop stores only a hash of it. If a token leaks, revoke it from the desktop in one click.

## Can I pair one Android device with several desktops?

Yes, as many as you like. The Home screen shows the active desktop at the top and the others under **Other hosts**. Tap one to connect to it. Each desktop has its own token, conversations, tools, skills and heartbeats.

## Does the app use my advertising ID?

No. The app does not read the advertising ID. It also does not read the Android ID, IMEI, MAC address or any other permanent device identifier.

## Does the app run anything in the background?

No. The app's only network connection is the one to your desktop, and it stays open only while the app is in the foreground, or briefly in the background until Android suspends the app. There is no background sync, no scheduled job and no push service. Because of this, mention notifications arrive only while the app is open or was recently in the background.

## Why does the app ask to show notifications?

So it can tell you when an agent mentions you in a conversation you do not have open. You can say no, and everything else still works.

## What permissions does the app use?

Two: `INTERNET`, for the connection to your desktop, and `POST_NOTIFICATIONS`, for mention notifications. The app does not use location, contacts, the microphone, the camera, phone state or any other permission.

## Why can't I install skills or tools from the app?

Changing the desktop's tools, MCP servers and skills is kept on the desktop. A paired device can do almost everything else the desktop can, so these security-sensitive changes stay where you can see them. Install, turn on and approve them on the desktop.

## Can I pair with a QR code?

No. The app does not support QR codes. Type the 6-digit code.

## Can I export a conversation?

Not from Android. You can export conversations on the desktop.

## What happens if I uninstall without revoking?

The desktop keeps the device's token until you revoke it. In Verzeta Studio, open **Settings → Remote Access**, click **Manage Remote Access**, and click **Revoke** next to the device under **Paired devices**. Uninstalling removes only the files on your phone. For a complete cleanup, revoke first (from the app or the desktop), then uninstall.

## Can two phones be paired with the same desktop?

Yes. Each device gets its own client ID and token, and both can be used at the same time. A tool-call confirmation appears on every connected device. When one device approves or denies it, it closes on the others.

## Does the desktop need to be on the same Wi-Fi as my phone?

For `ws://`, your phone must be able to reach the desktop's address, which usually means the same network. For `wss://` with a pinned fingerprint or a certificate from a public certificate authority, the desktop can be anywhere your phone can reach over the internet.

## Where can I report a bug or ask a question?

Email hello@verzeta.com. Include the app version from **Settings → About Verzeta**.
