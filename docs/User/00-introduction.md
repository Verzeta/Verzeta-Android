# What is Verzeta for Android?

Verzeta for Android is the companion app for **Verzeta Studio**, the multi-agent AI workspace that runs on your desktop. The app does not run AI or language models on your phone. Every conversation, tool call and project file lives on your own computer, where Verzeta Studio is installed.

You install Verzeta Studio on a desktop or laptop, turn on its Remote Access server, and pair this app with it. Your phone or tablet then shows and controls the workspace on your desktop.

## What you can do from your phone

- Chat with the AI agents on your desktop and watch their replies arrive as they are written.
- Browse and manage every conversation on the desktop: pinned chats, projects, group chats and direct agent chats.
- Create Project Rooms from templates, set up team members, and start group chats.
- View and edit canvases (code or text files the assistant produces), run AI actions on them, and run scripts on the desktop with live console output.
- Approve or deny tool calls from a confirmation dialog that appears on any screen.
- See the plans the assistant is running in a conversation, and step in on a single step.
- See generated images, play generated audio, and save artifacts to your device.
- Set up heartbeats (scheduled agent runs) for a project.
- See and revoke the devices paired with your desktop, and read a project's activity log.

## What it does not do

- **No standalone AI.** The app needs a paired desktop that it can reach. There is no offline mode.
- **No data goes to Verzeta servers.** The app connects only to the desktops you pair with. There is no Verzeta cloud service.
- **No analytics, crash reporting or advertising.** The app uses two Android permissions: `INTERNET`, for the connection to your desktop, and `POST_NOTIFICATIONS`, to tell you when an agent mentions you.

## About Verzeta Studio

Verzeta Studio is open source and runs on Linux (AppImage) and Windows (ZIP). You set up your AI providers on the desktop: Ollama for models that run on your own computer, or cloud providers such as OpenAI, Anthropic and Gemini with your own API keys. The Android app uses whatever the desktop is set up to use.

## What you need

- A computer running Verzeta Studio with the Remote Access server turned on.
- Both devices on the same network, or the desktop reachable over the internet with TLS encryption turned on.
- A 6-digit pair code from the desktop. Open **Settings → Remote Access**, click **Manage Remote Access**, then click **Generate pair code**.

The next page, Install and pair, walks through each step.
