# Host catalogs (Tools, MCP servers, Skills, Web Search)

The desktop's tools, MCP servers and skills appear in the app as read-only lists. You can see what the assistant has access to, but you cannot install, turn on or off, approve or block any of them from Android. The one catalog setting you can change here is the active web-search provider.

All four lists are in **Settings**, under **Host catalogs**.

## Why these lists are read-only

A paired device can do almost everything the desktop can. Tools, MCP servers and skills control what the assistant can do on your computer, and a wrong tap on a phone could weaken the desktop's security, so these changes stay on the desktop.

## Tools

**Settings → Tools** shows one list with three sections:

- **Built-in**: tools that ship with Verzeta Studio
- **Custom**: tools you created on the desktop
- **MCP**: tools from your MCP servers

Each row shows the tool's name, description and parameters. Turned-off tools are dimmed and marked **off**.

## MCP Servers

**Settings → MCP Servers** lists each Model Context Protocol server set up on the desktop, with its status:

- **Connected**: running, and its tools can be used
- **Connecting**: starting up
- **Error**: failed, for example because its command exited
- **Disconnected** or **Disabled**: not running

Tap a server to see its tools. Each tool shows its name as `server:toolname`, which is how the assistant sees it, and its description.

## Skills

**Settings → Skills** lists the installed skills. Tap one to see its details:

- Review state, such as Approved, Unreviewed or Blocked
- Description, tags and declared tools
- Where it came from, when it was installed and updated, its content hash and its install path
- Any warnings from the desktop's safety scan, with the file and line they refer to

If a skill has warnings, fix them on the desktop. Each warning points to a specific file and line.

## Web Search

**Settings → Web Search** lists the desktop's web-search providers and shows which one is active. Tap a provider to make it the active one. API keys and server addresses are set on the desktop. A provider that still needs one shows "Needs an API key. Configure it on the desktop host." or "Needs a base URL. Configure it on the desktop host."

## Preferred skills

You can choose preferred skills for a project or organization. Open the **Edit folder** sheet and tap **Edit preferred skills**, or use the same row in **Conversation settings** for a chat inside a project.

Turn on **Expose only preferred skills** to hide all other skills from the assistant in that project. With it off, the assistant sees every skill and favours the preferred ones.

Per-conversation preferred skills can only be set on the desktop.

## What these screens do not do

- Install tools, MCP servers or skills
- Turn tools, servers or skills on or off
- Approve or block skills
- Change MCP server settings
- Delete custom tools or skills

Do all of these on the desktop.

## Custom OpenAI-compatible servers

On the desktop, you can add servers that implement the OpenAI `/v1/chat/completions` and `/v1/models` endpoints, such as vLLM, LM Studio, Jan, Llamafile, TabbyAPI, KoboldCpp, LocalAI, SGLang or text-generation-webui. You can add more than one.

Each one appears as a regular provider wherever you pick a provider in the app: the chat's model picker and the member settings in Project Rooms. It shows the name you gave it on the desktop, for example "LM Studio at home", and a caption that says "Custom server" and lists what it supports (streaming, tools, vision).

You cannot add, edit or delete these servers from Android. Manage them on the desktop in **Settings → Text Providers → Custom OpenAI-Compatible Servers**. The app picks up changes the next time it talks to the desktop.

API keys for custom servers are stored in the desktop's local settings and obscured the same way as the other provider keys. This is not encryption, so protect your desktop user account. The keys never reach your phone. The desktop uses them when it sends requests for you.
