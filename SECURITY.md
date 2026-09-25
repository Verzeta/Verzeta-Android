<!--
SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
SPDX-License-Identifier: LGPL-3.0-or-later
-->

# Security Policy: Verzeta for Android

## Reporting a vulnerability

If you discover a security vulnerability in this app, please report it
privately. Do not open a public issue for security-sensitive findings.

**Contact**: <hello@verzeta.com> with the subject prefix
`[Verzeta Security]` (or file a private security advisory on the
project's repository if the hosting platform offers one).

Please include:

- A description of the vulnerability
- Steps to reproduce
- The affected version of the app (the About screen shows the build version)
- The affected version of Verzeta Studio (if the issue spans the wire protocol)
- Any proof-of-concept code you have

## What to expect after you report

- Reports are reviewed as time allows. Response times vary, and we do not commit to fixed timelines.
- Whether a report is treated as a vulnerability, and whether and when it is fixed, is at the maintainers' discretion.
- Security fixes ship in normal releases, and may be released without any public description of the issue. Any public mention is at the maintainers' discretion.
- Please keep the details of what you report private. If you intend to publish anything about it, tell us first and give us a reasonable chance to release a fix.
- If an issue you reported is mentioned publicly, you can ask to be credited.
- There is no bug bounty.

## What we consider in scope

This policy covers the Verzeta for Android app and the dependencies it ships with.

## What we consider out of scope

- Vulnerabilities in Verzeta Studio (the desktop host). Report those to the
  host repository.
- Vulnerabilities in the Android platform itself. Report those to Google.
- User-error scenarios, such as a user pasting their pairing token into a
  public chat
- An LLM provider (configured on the desktop) mishandling content sent to
  it. Report that to the provider.

## Rules for testing

This policy does not give anyone permission to test against systems, devices or data that are not their own. Test only on your own installation, devices and data. Do not test against other people's Verzeta hosts or clients, the project's website or infrastructure, or third-party services such as LLM providers. No denial-of-service testing against anything but your own machine, and no social engineering. Stop once you have shown the issue, and do not keep, share or use any data you reached.

This policy is not a legal agreement. It does not authorise anything that is otherwise unlawful, and it does not waive any rights.
