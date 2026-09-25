# Settings

The Settings tab is where you manage your paired desktops, the devices paired with each desktop, and the lists of what the desktop has installed.

## Hosts

Lists every desktop you have paired with. Each row shows the host's name, endpoint and status.

- **Add a desktop host** (or **Add another host** once you have one): opens the Pair device screen.
- **Connect**: connects to that host with its stored token.
- **Remove**: deletes the host from this device straight away, without asking. It does not cancel the token on the desktop, so revoke it first if you want a full cleanup.

## Revoke this device

Tap **Revoke this device** to make the desktop cancel this device's token at once. The app clears its data for that host and stays on the same screen. There is no confirmation step. To use the host again, tap **Connect**. The app opens the Pair device screen so you can enter a new code.

The same button appears at the bottom of the Home screen, next to **Ping**, which measures the round-trip time to the desktop.

Revoke before you uninstall the app, so the desktop does not keep a token that is no longer used.

## Devices paired with this host

When you are connected, this section lists every other device paired with the desktop, such as other Android devices and VS Code windows running Verzeta for VS Code. This device is not listed. Each row shows the device's name, when it was last seen and when it was added, and a **Revoke** button.

Tap **Revoke** to cancel that device's token. If the device is connected, the desktop disconnects it.

### Revoked devices

Devices you have revoked are not in the main list. If there are any, tap **Revoked devices** to see them. The list is read-only and helps you check which devices you revoked.

## Host catalogs

Four lists show what the connected desktop has set up:

- **Tools**: every tool the assistant can call (built-in, custom and MCP). Turned-off tools are dimmed.
- **MCP Servers**: the desktop's Model Context Protocol servers and their status.
- **Skills**: installed skills with their review state and details.
- **Web Search**: the desktop's web-search providers. You can choose which one is active.

Tools, MCP Servers and Skills are read-only. The Host catalogs page has the details.

## Session

Shows who this device is signed in as on the active desktop. Tap **Verify session** to confirm the desktop still accepts this device's token.

## Help & About

- **Help & docs**: opens these guides.
- **About Verzeta**: shows the app version, links to the website and privacy policy, the licence, and open-source credits. It also opens Help & docs.

## Settings that are not app-wide

- **Show thinking**: there is no app setting to hide the reasoning panel. The panel starts collapsed, except when the model produced reasoning but no reply. It does not appear when a reply has no reasoning. To stop a model producing reasoning, turn off **Thinking** in that chat's **Conversation settings**.
- **RAG**: set it for each conversation in **Conversation settings**. There is no app-wide RAG switch, because the desktop stores the setting on each conversation.
- **Colours**: on Android 12 and later, the app always uses your system colours. You cannot turn this off.

## More than one desktop

You can pair as many desktops as you like. Only one is active at a time. To switch, tap **Connect** next to a host here, or tap the host under **Other hosts** on the Home screen.

Each desktop has its own:

- Token
- Conversation list
- Tools, MCP servers and skills
- Heartbeats and activity timeline

When you switch desktops, the app reconnects and loads that desktop's data.
