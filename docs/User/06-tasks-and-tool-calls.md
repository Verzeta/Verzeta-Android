# Tasks, plans and tool calls

When the assistant takes on multi-step work, it makes a **plan** with steps. When it needs a tool, for example to run a shell command or fetch a web page, it makes a **tool call**. You can follow tool calls and, for some tools, approve them first.

## Plans

Open the ⋮ menu in a chat and tap **Plans**. The Plans screen lists every plan in this conversation, running or finished.

Each plan card shows:

- The status icon and the goal
- The status and progress, for example "Status: Executing • Steps 3/5"
- A **Stop** button while the plan runs
- When the plan started and when it was last updated

Each step shows its title, the alias of the member doing it, its status, any retry or rework counts, and the reason if the step was sent back.

## Start a task

At the top of the Plans screen, type a goal in **Start a task** and tap **Start task**. The desktop creates a plan, and it appears in the list.

## Step in on a step

When the desktop allows it, a step shows three actions:

- **Retry**: runs the step again. You can add a note telling the agent what to change.
- **Mark done**: marks the step as done. Use it with care.
- **Skip**: skips the step and moves on.

## Stop a plan

Tap **Stop** on a plan card to stop that plan.

## Tool calls

Open the ⋮ menu and tap **Tool calls** to see the Tool Activity Log for this conversation.

Each entry shows:

- A status dot: accent colour for success, a second accent colour while running, red for an error, grey while pending
- The tool name
- The time
- The arguments, as formatted JSON
- The result, in red if the call failed

The list updates live.

## Tool confirmation

When the assistant wants to use a tool that needs your approval, for example some shell commands or file writes, a dialog appears on whatever screen you are on. It is titled **Tool Call Confirmation** and shows the tool name and its arguments.

Tap **Approve** to run the tool, or **Deny** to refuse it.

If another paired device, or the desktop, answers first, the dialog closes on its own.

## What these screens do not do

- **Resume a stopped plan.** A stopped plan cannot restart. Start a new task with a similar goal.
- **Reorder steps.** You cannot change the order of steps.
- **Turn tools on or off for the whole desktop.** That is done on the desktop (see Host catalogs).
