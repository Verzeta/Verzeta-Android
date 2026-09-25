# Artifacts and generated media

Two screens show files the assistant and its tools produce: **Artifacts**, for any file a tool creates, and **Generated media**, for images and audio. You can save artifacts to your device.

## Artifacts

Open the ⋮ menu in a chat and tap **Artifacts**.

The screen lists every file created by tools in this conversation. Each row shows:

- An icon and the file name
- A "Task: <plan goal> · <step title>" label, if the file came from a plan step
- "by @alias", if a specific member produced it
- The file's path on the desktop, for reference
- A save icon, which opens Android's save dialog so you choose where the file goes

Rows created when an agent finishes a plan step may have no file. Their text is in the chat, so those rows have no save icon.

## Generated media

Open the ⋮ menu and tap **Generated media**. The screen has two tabs.

### Images

A two-column grid of the conversation's generated images. Tap a tile to download that image and show it in the tile. If only part of the image arrived from the desktop, the tile is marked **Partial**. If the download fails, the reason appears at the bottom of the screen.

### Audio

A list of generated audio clips. Tap the play icon to play a clip. The app downloads the clip and plays it, and the play icon becomes a stop icon. Tap it to stop. One clip plays at a time: starting another clip, or leaving the screen, stops the current one. The app deletes its temporary copy when playback ends or stops.

You cannot save images or audio to your device from this screen. To keep a file, save it from **Artifacts** if it is listed there, or copy it from the desktop.

## How saving an artifact works

When you tap the save icon:

1. Android's save dialog opens. Pick where to save the file, for example Downloads or a cloud drive.
2. The app asks your desktop for the file.
3. The file arrives over the connection to your desktop.
4. The app writes it to the place you chose and shows "Saved N bytes".

The app writes nothing until you pick a place, and it needs no storage permission. If you see "Saved a truncated copy. The full file is N bytes.", only part of the file arrived. Open the file on the desktop to get all of it.

## Live updates

The lists update when a tool creates a file, image or audio clip in this conversation.

## What these screens do not do

- **Edit images or audio.** The screens only show and play them.
- **Download several files at once.** Save one file at a time.
- **Download in the background.** Stay on the screen until the save finishes. Leaving it can interrupt the download.
- **Preview large files.** To look through a large artifact, open the conversation on the desktop.
