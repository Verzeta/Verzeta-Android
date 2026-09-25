# Canvas

Canvas is the assistant's workspace for code, scripts and long text. When the assistant produces something that is easier to work with as a file than as a chat message, it opens a canvas in the conversation.

## Open a canvas

When the assistant creates a canvas while you are looking at that chat, the canvas opens by itself. You can also open it from any chat: open the ⋮ menu and tap **Canvas**.

The screen shows the file in a monospace editor with syntax colouring and line numbers. The header shows the file name, language, line count and revision.

## Edit a canvas

Tap the editor and type. Your changes save one second after you stop typing, and other paired devices see them.

The wrap icon in the header turns word wrap on or off.

## Run code

If the canvas language can run, the header shows a play button. Tap it to run the code on your desktop. Output appears in the console at the bottom, and the play button becomes a stop button while the script runs. Tap **Console** at the bottom of the screen to show or hide the console.

**The code runs on the desktop, not on your phone.** If the desktop cannot sandbox the script (always on Windows, and on Linux when bubblewrap is missing or cannot start), a red banner says "Sandbox unavailable on host" and the script runs without a sandbox. Only run code you trust.

## Scripts that ask for input

Scripts that read from the keyboard work from your phone. When a Python script calls `input()`, the prompt appears in the console and an input row opens below it.

- Type your answer and tap the send icon.
- Sending an empty box submits a blank line, the same as pressing Enter at a real prompt.
- **End input** tells the script there is no more input, for scripts that keep reading until the input ends.
- The lines you type appear in the console, so you can read the whole run back.

The input row appears only while a script is running.

A Bash script that writes its prompt with `read -p` will not show that prompt, because that text goes to the error stream, which the console shows only when a run fails. Print the prompt first (for example `echo -n "Name: "`) and then call `read`.

## Open in your desktop IDE

If the canvas language is supported, the header shows an open-in-IDE icon. Tap it to open the canvas in the IDE set up on your desktop. The icon is hidden while a script runs.

## AI actions and tools

Tap **AI actions** at the bottom of the canvas to see what the assistant can do with this file, such as Explain or Refactor. Tap one to run it. It works on the canvas of the conversation you have open, and the result appears in that conversation. It may also change the canvas.

Quick tools such as Validate and Format are under the tune icon in the header. The icon appears only when the language has tools.

## Switch canvases

A conversation can have several canvases. Tap the history icon in the header and pick an earlier canvas to switch to it.

## Close a canvas

The X icon in the header closes the canvas without deleting it. To open it again, pick it from the history list, or ask the assistant to open it.

## What canvas does not do

- **Multi-file projects.** You work with one canvas at a time.
- **Editing together.** If two devices edit at once, the last save wins.
- **Running code on Android.** Running code and opening it in an IDE always happen on the desktop.
