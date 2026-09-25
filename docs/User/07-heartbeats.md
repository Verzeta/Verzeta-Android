# Heartbeats (scheduled agent runs)

A heartbeat runs an agent on a schedule. Heartbeats run on **your desktop** at the set times, whether or not the Android app is open. Use them for agents that should check, summarize or report on something regularly, for example "every morning at 8:00, read the project documents and write a status update".

## When to use heartbeats

- A daily or weekly status report from one of your project's team members
- Regular monitoring, for example an agent that checks news feeds for new items
- A summary of what happened since the last run

Heartbeats are optional.

## Create a heartbeat

Open the **Edit folder** sheet for a project or organization (see "Change a folder's settings" on the Projects and rooms page), scroll to **Project heartbeats**, and tap **Add heartbeat**. The project needs at least one member first.

- **Member**: the project member who does the work.
- **Goal**: what the agent should do on each run.
- **Schedule**: when to run. See the formats below.
- **Max runs per day**: a limit from 1 to 1440.
- **Surface criteria (optional)**: a plain-language rule for which reports should be posted, for example "only post when there's a real anomaly".
- **Auto-surface target (optional)**: a conversation to post reports to, or **Overlay only (no target conversation)** to keep them out of your chats.
- **Enabled**: turn the heartbeat on or off without deleting it. You can still run a disabled heartbeat by hand.
- **Allow self-config**: lets the agent change its own goal, schedule, surface criteria and on/off state. The schedule format and the daily limit still apply, and every change is recorded.

## Schedule formats

- `@hourly`: every hour, on the hour.
- `@daily at HH:MM`: once a day at that local time, for example `@daily at 08:00`.
- `@weekly`: once a week, on Monday at midnight.
- `@weekly on DAY at HH:MM`: once a week at that day and time, for example `@weekly on FRI at 17:00`. Use MON, TUE, WED, THU, FRI, SAT or SUN.
- `@interval N`: every N minutes. N must be 5 or more, for example `@interval 30`.
- Empty: the heartbeat runs only when you start it.

## Run, edit or remove a heartbeat

Each heartbeat row in **Project heartbeats** has three icons: run now (the play icon), edit, and remove. After you tap run now, the run appears on the Heartbeat activity screen.

You cannot cancel a running heartbeat from Android. Cancel it on the desktop.

## Heartbeat activity

In the **Edit folder** sheet, tap **Activity** next to **Project heartbeats**. The Heartbeat activity screen is read-only and has three sections:

- **Next fires**: what is scheduled to run soon
- **Recent runs**: what ran recently, and the outcome
- **Audit log**: changes to heartbeat settings

## Heartbeat reports

Each run produces a report. If the heartbeat has a target conversation and the report meets its surface criteria, the report is posted there. Otherwise it waits on the desktop, where you can post or dismiss it.

## Limit reports in a conversation

In **Conversation settings**, the **Heartbeat** section has **Auto-surface heartbeat reports**, which lets heartbeat reports post into this conversation, and a **Daily cap**. The first time you turn it on, the app suggests a cap.
