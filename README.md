<!--
SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
SPDX-License-Identifier: LGPL-3.0-or-later
-->

<div align="center">

<img src="resources/icons/verzeta-studio.png" width="128" alt="Verzeta">

# Verzeta™ for Android

**Chat with your Verzeta Studio agents from your phone.**

[![License: LGPL-3.0-or-later](https://img.shields.io/badge/License-LGPL%203.0%2B-blue.svg)](LICENSING.md)
[![Platform: Android 8.0+](https://img.shields.io/badge/Platform-Android%208.0%2B-success.svg)](#requirements)
[![Built with Kotlin + Jetpack Compose](https://img.shields.io/badge/Built%20with-Kotlin%20%E2%80%A2%20Jetpack%20Compose-orange.svg)](https://developer.android.com/jetpack/compose)
[![REUSE 3.0 compliant](https://img.shields.io/badge/REUSE-3.0-brightgreen.svg)](https://reuse.software/)

[![Website](https://img.shields.io/badge/Website-verzeta.com-2563eb.svg)](https://verzeta.com)
[![Follow @VerzetaAI](https://img.shields.io/badge/Follow-%40VerzetaAI-000000.svg?logo=x&logoColor=white)](https://x.com/VerzetaAI)
[![Reddit r/Verzeta](https://img.shields.io/badge/Reddit-r%2FVerzeta-FF4500.svg?logo=reddit&logoColor=white)](https://www.reddit.com/r/Verzeta/)

**[verzeta.com](https://verzeta.com) · [X / Twitter](https://x.com/VerzetaAI) · [Reddit](https://www.reddit.com/r/Verzeta/)**

</div>

The Android companion for **[Verzeta Studio](https://verzeta.com)**. Talk to your multi-agent project rooms from your phone or tablet. Pair the app with your own Verzeta Studio desktop host and chat with named-alias agent teams (`@Coord`, `@Researcher`, `@Writer`, `@Critic`) wherever you are.

This is one of two Verzeta wire clients; the other is **[Verzeta for VS Code](https://github.com/Verzeta/Verzeta-VSCode-Extension)**. Both connect to the same Verzeta Studio host over the same WebSocket wire protocol. The desktop host stays authoritative and does all the model, tool, and agent work.

Everything runs on your paired desktop. There is no cloud account, telemetry or advertising.

---

## What it does

- **1:1 and group conversations** with named-alias agents. Each agent's reply is attributed inline.
- **Project rooms.** Folders that bundle an agent roster, documents, and group chats.
- **Attachments.** Share images and files into a conversation.
- **Canvas, artifacts, and generated media,** surfaced live from the host.
- **Tasks, tool-call timelines, polls, and activity history.**
- **Inline reasoning disclosure** for models that produce visible thinking.
- **`@user` notifications** when an agent mentions you.

## What it does NOT do

- Does not run AI, tools, or agents on the device. Everything runs on your paired desktop host.
- Does not talk to any LLM provider directly.
- Does not collect telemetry or analytics, and contains no advertising, crash-reporting, or push SDK.
- Does not require any cloud account. It connects only to the host you pair with.

---

## Requirements

- **Android 8.0 (API 26) or newer.**
- **A reachable Verzeta Studio desktop host** with Remote Access enabled. See the main [Verzeta Studio](https://verzeta.com) documentation to set one up.
- The phone and host on the same network, or the host reachable over the internet with TLS.

## Install

Download the APK from the [release page](https://github.com/Verzeta/Verzeta-Android/releases) and install it. The app is not on Google Play yet. To build from source:

```sh
./gradlew assembleDebug      # debug APK
./gradlew bundleRelease      # release App Bundle (AAB) for the Play Store
```

An Android SDK (API 36 build tools) and JDK 17 are required.

## Pair with your host

1. On the desktop, open **Settings → Remote Access**, click **Manage Remote Access**, and turn on the server switch. Turn on **TLS encryption** first if you want an encrypted connection.
2. In the app, tap **Add host** (or **+** on the Home screen) and enter the URL the desktop lists under **CLIENTS CAN REACH THIS HOST AT**: `ws://<desktop-address>:9180/ws`, or `wss://…` with TLS encryption on.
3. For a `wss://` host, paste the SHA-256 fingerprint shown in the desktop's Remote Access dialog.
4. Click **Generate pair code** on the desktop and enter the code in the app.
5. Once connected, tap **Open chat** to start a conversation.

---

## Documentation

End-user guides live in **[docs/User/](docs/User/)** and are bundled in-app under **Settings → Help & docs**.

| Topic                                                 | Page                                                         |
| ----------------------------------------------------- | ------------------------------------------------------------ |
| What Verzeta for Android is and how the pieces fit    | [Introduction](docs/User/00-introduction.md)                 |
| Installing the app and pairing with a host            | [Install and pair](docs/User/01-install-and-pair.md)         |
| Browsing and managing conversations                   | [Conversations](docs/User/02-conversations.md)               |
| Chatting with agents and teams                        | [Chatting](docs/User/03-chatting.md)                         |
| Project rooms: rosters, documents, group chats        | [Projects and rooms](docs/User/04-projects-and-rooms.md)     |
| The live canvas editor                                | [Canvas](docs/User/05-canvas.md)                             |
| Plans, tasks and tool calls                           | [Tasks and tool calls](docs/User/06-tasks-and-tool-calls.md) |
| Scheduled agent runs                                  | [Heartbeats](docs/User/07-heartbeats.md)                     |
| Artifacts and generated media                         | [Artifacts and media](docs/User/08-artifacts-and-media.md)   |
| Host catalogs: tools, MCP servers, skills, web search | [Host catalogs](docs/User/09-host-catalogs.md)               |
| The audit log: who did what, when                     | [Activity timeline](docs/User/10-activity-timeline.md)       |
| Team decisions on the record                          | [Polls](docs/User/11-polls.md)                               |
| App settings                                          | [Settings](docs/User/12-settings.md)                         |
| Error messages and their fixes                        | [Troubleshooting](docs/User/13-troubleshooting.md)           |
| Frequently asked questions                            | [FAQ](docs/User/14-faq.md)                                   |
| What stays on your device                             | [Privacy summary](docs/User/15-privacy-summary.md)           |

---

## Privacy & security

- **No telemetry, no analytics, no ads.** The app connects only to hosts you explicitly pair with.
- **No direct LLM calls.** All model, tool, and agent work happens on your desktop host.
- **No cloud account.** There is no Verzeta-operated backend.
- Pairing tokens are stored in the app's private storage. Transport uses TLS for `wss://` hosts, with SHA-256 certificate pinning for self-signed certificates. The only library that touches the network is OkHttp, used solely as the WebSocket transport.

Full details: **[PRIVACY.md](PRIVACY.md)** · **[SECURITY.md](SECURITY.md)**

Security issues: please follow the responsible-disclosure process in [SECURITY.md](SECURITY.md). Do not open a public issue.

---

## Community

- **Website:** [verzeta.com](https://verzeta.com)
- **X / Twitter:** [@VerzetaAI](https://x.com/VerzetaAI)
- **Reddit:** [r/Verzeta](https://www.reddit.com/r/Verzeta/)
- **Verzeta Studio (desktop host):** [verzeta.com](https://verzeta.com)
- **Verzeta for VS Code:** [github.com/Verzeta/Verzeta-VSCode-Extension](https://github.com/Verzeta/Verzeta-VSCode-Extension)

---

## Contributing

Bug fixes, features, documentation, tests, and translations are all welcome.

- **[CONTRIBUTING.md](CONTRIBUTING.md):** the contribution guide (style, testing, commit conventions).
- **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md):** we adopt the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

By contributing you accept the [Contributor License Agreement](CLA.md).

---

## License

Verzeta for Android is licensed under **LGPL-3.0-or-later**. Every source file declares it inline via an `SPDX-License-Identifier` header, and the project is [REUSE 3.0](https://reuse.software/) compliant.

As a wire-protocol client and UI, this app is the infrastructure tier of Verzeta's wider triple-license framework (the GPL-licensed components live only in the desktop host). See **[LICENSING.md](LICENSING.md)** for the full arrangement, [`LICENSES/`](LICENSES/) for the license texts, [CLA.md](CLA.md) for the contributor terms, and [TRADEMARKS.md](TRADEMARKS.md) for naming and logo guidance.
