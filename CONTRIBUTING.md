<!--
SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
SPDX-License-Identifier: LGPL-3.0-or-later
-->

# Contributing to Verzeta for Android

Thank you for your interest in contributing to the Verzeta Android client. This document explains how to file issues, propose changes, and what is expected when you submit a patch.

Verzeta is released under a triple-license arrangement (GPL-3.0-or-later for the host's differentiated components, LGPL-3.0-or-later for supporting infrastructure, and commercial licenses for organisations that do not want either copyleft variant). This Android client is entirely in the LGPL infrastructure tier; see [LICENSING.md](LICENSING.md). Contributions of all sizes are welcome: bug fixes, new features, documentation, tests, translations, and feedback.

---

## Quick links

- **Found a bug?** Open an issue. See [Reporting bugs](#reporting-bugs).
- **Have an idea?** Open a feature-request issue first, before writing a patch.
- **Found a security vulnerability?** Do **not** open a public issue. See [SECURITY.md](SECURITY.md) for responsible disclosure.
- **Ready to submit a patch?** See [Submitting changes](#submitting-changes).

---

## Code of Conduct

We follow a written code of conduct: see [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Everyone participating in the project's spaces is expected to follow it.

---

## Reporting bugs

A useful bug report contains:

1. **What you did.** Step-by-step instructions, including the Android version / device, the app version (from the About screen), and the version of the Verzeta Studio host you paired with.
2. **What you expected to happen.**
3. **What actually happened**, including any error messages.

> **Privacy note**: please do not paste pairing tokens, full conversation contents, or other sensitive data into bug reports. Redact before posting.

---

## Submitting changes

### Branching

The project develops on `main`. Commits land directly on `main` when small and reviewable. Larger or sensitive changes may be developed in topic branches first.

### Setting up locally

```bash
# Build + compile-check the debug variant
./gradlew compileDebugKotlin

# Run the unit-test suite
./gradlew testDebugUnitTest
```

The Android UI itself has no automated coverage. The maintainer checks UI changes on a device, the same rule the host applies to its QML UI. The build must compile cleanly and unit tests must pass before merge.

### Commit discipline

Commit messages follow the project's pattern: a single-line summary, then a body explaining the **why**. Look at recent `git log --oneline` on `main` for the established style. When co-authored work is involved, append a `Co-Authored-By:` trailer.

### Code style

- **Language**: Kotlin, with Jetpack Compose for the UI.
- **Naming**: classes `CamelCase`, functions `camelCase`, constants `UPPER_SNAKE_CASE`.
- **Comments**: write WHY, not WHAT. Use KDoc `/** */` blocks for the public API surface. Keep internal development context (decision dates, issue numbers) in commit messages, not inline narrative.
- **Headers**: every source file needs SPDX metadata: `SPDX-FileCopyrightText` and an `SPDX-License-Identifier` set to `LGPL-3.0-or-later`. The project is [REUSE 3.0](https://reuse.software/) compliant; run `reuse lint` to verify.

### Testing

- New features come with new unit tests where the logic is testable in isolation.
- Bug fixes come with a regression test that fails without the fix where practical.

### Documentation

If your patch changes user-visible behaviour, update the relevant page under `docs/User/`. Release notes for each public release are written by the maintainer at publish time.

---

## Licensing and the CLA

Operating the commercial track requires the project owner to hold the right to relicense contributed code. Contributors therefore sign a Contributor License Agreement that:

- **Assigns** copyright in the contribution to the project owner. This is an assignment, not a license grant. You retain a non-exclusive right to use your own contribution in your own separate work under any license you choose.
- Grants a perpetual, irrevocable, royalty-free patent licence (with the standard patent-retaliation clause).
- Warrants that the contribution is your own work and free of third-party encumbrances.

The full text and the sign-off mechanism are in [CLA.md](CLA.md). Every pull request must be signed off by every author before merge; the CLA bot guides first-time contributors through the one-time acceptance.

---

## Reviews

Reviews happen as time allows, and response times vary. If your pull request has had no response, a follow-up comment is welcome.

---

## What is NOT a contribution we can take

- **Telemetry, analytics, fingerprinting, or "anonymous usage statistics".** Verzeta is local-first; see [PRIVACY.md](PRIVACY.md).
- **Auto-update / phone-home that the user did not explicitly enable.**
- **Forced cloud-account requirements.**
- **Removing SPDX headers or relicensing components.**
- **A push-notification, advertising, or analytics SDK.** The app deliberately ships none.

---

## Questions

- **About the license arrangement**: see [LICENSING.md](LICENSING.md). Commercial-license inquiries go to the address in [SECURITY.md](SECURITY.md) with the subject prefix `[Verzeta Commercial]`.
- **About privacy or data handling**: see [PRIVACY.md](PRIVACY.md).
- **About using the "Verzeta" name in your project**: see [TRADEMARKS.md](TRADEMARKS.md).

For anything else, open an issue.

---

Thank you for contributing.
