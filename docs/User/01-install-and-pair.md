# Install and pair

## Install

Download the APK from the release page at github.com/Verzeta/Verzeta-Android and install it. The app is not on Google Play yet.

When you open the APK, Android asks you to allow installs from the app you downloaded it with, such as your browser or file manager. Allow it for that app, then tap **Install**.

The app needs **Android 8.0 (API 26)** or newer. It works on phones, tablets, foldables, Android TV, Chromebooks and Samsung DeX.

## Pair with your desktop

You pair this device once for each desktop. The pairing survives app restarts and device reboots. If Android backup is on, it also survives reinstalling the app or moving to a new phone.

### Step 1: Turn on Remote Access on the desktop

In Verzeta Studio on your desktop:

1. Open **Settings → Remote Access** and click **Manage Remote Access**. The Remote Access dialog opens.
2. Optional: to encrypt the connection, turn on **TLS encryption** before you start the server. The dialog then shows a **SHA-256 FINGERPRINT**, which you will copy into the app.
3. Turn on the server switch at the top of the dialog. It should say "Server is running".
4. Under **CLIENTS CAN REACH THIS HOST AT**, note the URL, for example `ws://192.168.1.42:9180/ws`. With TLS encryption on, it starts with `wss://`.
5. Under **Pair a device**, click **Generate pair code**. Keep the dialog open. The code is valid for 5 minutes.

To have the server start whenever Verzeta Studio opens, turn on **Auto-start at app launch** in the same dialog.

### Step 2: Open the app

If you have not paired before, the Home screen shows a **No active host** card with an **Add host** button. Tap it.

If you already have a paired desktop and want to add another, tap **+** at the top of the Home screen.

### Step 3: Fill in the Pair device screen

- **Host name**: a label for this desktop, for example "home-pc". If you leave it empty, the app uses the address from the endpoint.
- **Endpoint**: the URL from the desktop's Remote Access dialog, for example `ws://192.168.1.42:9180/ws`. Include the port.
- **TLS certificate fingerprint (SHA-256)**: shown only for `wss://` endpoints. Paste the SHA-256 FINGERPRINT from the desktop's Remote Access dialog. Leave it empty only if the desktop uses a certificate from a public certificate authority. When you pair again with a desktop you already saved, leave it empty to keep the fingerprint saved for it.
- **Client name**: a name for this phone or tablet, shown in the desktop's list of paired devices. It defaults to "Android".
- **Pair code**: the 6-digit code from the desktop.

Tap **Pair**. If pairing succeeds, the app stores the token from the desktop and opens the Home screen.

### Step 4: Check that it worked

The Home screen shows your desktop in a **CONNECTED** card with the host name, endpoint and round-trip time. Tap **Open chat** to see your conversations.

If pairing fails, the most common causes are:

- **The code expired or was already used.** Each code works once, for 5 minutes. Click **Generate pair code** on the desktop again for a new code.
- **The endpoint is wrong.** Check the IP address and port. The desktop's Remote Access dialog shows the exact URL to copy.
- **The endpoint does not match the TLS setting.** Use `ws://` when TLS encryption is off on the desktop and `wss://` when it is on.
- **The fingerprint does not match.** Copy the SHA-256 FINGERPRINT again from the desktop. The app ignores colons and letter case, but every character must match.

The Troubleshooting page lists each error message and its fix.

## What is stored on your phone

Two small files in the app's private storage:

- `verzeta_hosts.xml`: the desktops you have paired with
- `verzeta_remote_auth.xml`: the tokens those desktops issued

The app keeps no database, message cache or analytics. Tokens are stored as plain text in the app's private storage, so Android backup can carry them to a new phone.
