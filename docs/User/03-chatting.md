# Chatting

Open a conversation from the Conversations screen, or from the Recent list on the Home screen. Type in the composer at the bottom. Replies appear as the assistant writes them.

## The chat header

- **Title**: the conversation title. Tapping it, or the provider and model line below it, opens the model picker.
- **Connection dot**: green when connected. Another colour means the app is connecting, disconnected or failed. The Troubleshooting page explains each colour.
- **⋮ menu**: Conversation settings, Plans, Tool calls, Artifacts, Generated media, Canvas and Activity. **Activity** opens this conversation's activity timeline (see the Activity timeline page).

## Send a message

Type in the composer and tap the send button. The message goes to your desktop, and the reply appears as the assistant writes it.

A message can be up to 4096 characters. If it is longer, the app does not send it and keeps your text in the composer so you can shorten it or split it.

If a message cannot be sent, the app removes it from the chat, puts your text back in the composer and shows the reason at the bottom of the screen.

To attach a file or photo, tap the attachment icon. Android's file chooser opens, and the app receives only the file you pick. You can attach up to 8 files to a message. Each file can be up to 4 MB, and all files together up to 11 MB.

## Stop a reply

While a reply is being written, the send button becomes a stop button. Tap it to stop the reply. The desktop stops generating.

## Copy a message

Long-press a message to select text, then use **Copy**, **Select all** or **Share** in the toolbar that appears. Drag the handles to change the selection. This works on your messages, on the assistant's replies, and on the reasoning panel when it is open.

## The reasoning panel

Some models think through a problem before they answer, for example Qwen3, DeepSeek-R1 and Claude with extended thinking. When a model does this, the reply shows a collapsed **Reasoning (N chars)** row above the answer. Tap the row to read the reasoning, and tap it again to close it. Replies from other models look the same as before.

If the model produced reasoning but no reply, the message says "The model produced reasoning only. Expand below to see it." and the reasoning panel starts open.

The reasoning panel is taller on bigger screens. TalkBack reads its header as "Reasoning, expanded" or "Reasoning, collapsed", and you can open it with a TV remote.

The app only shows reasoning. It never sends it back to the desktop, never includes it in search, and never saves it on your phone. When you open a conversation again, the app loads the reasoning from the desktop.

To make a model produce reasoning, open the ⋮ menu, tap **Conversation settings**, and turn on **Thinking**. For models that cannot think before answering, the setting has no effect.

## Change the model

Tap the title or the provider and model line in the chat header. The model picker opens. Pick any provider and model set up on the desktop. Your choice is saved on this conversation and also becomes the current model selection in Verzeta Studio on the desktop.

The models you can pick are the ones set up on the desktop: Ollama models on your own computer, or cloud providers such as OpenAI, Anthropic and Gemini with your own API keys. The app cannot add or set up providers.

## Slash commands

Type `/` in the composer to see the desktop's commands with a short hint for each: `/help`, `/compact` (summarise older messages now), `/flashmemory` (wipe this conversation's messages, asks to confirm), `/artifacts`, `/showtools`, `/showmcptools`, `/tools` and `/clear`. Tap a suggestion to put it in the composer. The command's output appears in the chat as a system entry.

## The context gauge

Next to the send button, a percentage shows how full the model's context window was at the last exchange. Grey means there is plenty of room. Amber (70% or more) means older messages will soon stop fitting and the desktop will compact the conversation. Red (90% or more) means the window is nearly full. The gauge appears after the first reply in a session.

## System entries

Some entries in the chat come from the system, not from a person or an agent: compaction notes (`~Dynamic Compact Performed~`), turn notes (⏸ paused, ▶ continuing), and **sub-agent reports** (marked 🤖), which hold the result an agent's background worker posted when it finished a delegated task. Short notes appear as a centred line. Full reports appear as a card you can select and copy.

## Mention notifications

When an agent mentions you in a conversation you do not have open, Android shows a notification. On Android 13 and later, the app asks for permission to show notifications. Tap the notification to open that conversation. Mentions in the conversation you have open do not create a notification.

## Conversation settings

Open the ⋮ menu and tap **Conversation settings**. The settings are:

- **Group members** (group chats only): you manage group members in Verzeta Studio on the desktop.
- **Primary agent**
- **Model**: opens the same model picker as the chat header.
- **System prompt**
- **Parameters**: temperature, max tokens (-1 means no cap, the recommended default), context window, streaming, and thinking.
- **Sampling**: **Use app-recommended sampling**, which overrides the sliders below it while it is on.
- **Tools**: **Tools enabled**, and **Describe tools in system prompt**, which is off by default.
- **Conversation memory**: **Dynamic compaction**, and how often the summary refreshes.
- **Agent**: **Agent pattern**, and **Require confirmation**, which pauses for your approval before each tool call.
- **Preferred skills**: for a chat inside a project, edits the project's preferred skills. The change is saved to the desktop when you confirm it. Per-conversation preferred skills can only be set on the desktop.
- **Heartbeat**: whether heartbeat reports can post into this conversation, and a daily cap.
- **RAG**: turns retrieval-augmented generation on or off for this conversation.

## What this screen does not do

- **Edit or unsend messages.** You cannot edit or unsend a message.
- **React with emoji.** Not supported.
- **Voice input or dictation.** The app does not use the microphone.
- **In-app camera.** The app cannot take photos. Take the photo with your camera app first, then attach it.

## Scrolling

The newest message is at the bottom. When a new message arrives, the list scrolls to it. When you open a conversation, the list starts at the latest message.
