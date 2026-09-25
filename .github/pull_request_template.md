<!--
SPDX-FileCopyrightText: 2026 Aditya Mehra <aix.m@outlook.com>
SPDX-License-Identifier: LGPL-3.0-or-later

Do NOT delete any section or checklist item. A PR that removes the template or
leaves the checklist blank does not meet the requirements and is flagged
automatically. If an item genuinely does not apply, tick it and add
"(N/A: reason)".
-->

## Summary

<!-- One or two sentences: what does this change do? -->

## Why

<!-- The reasoning. What problem does it solve? -->

## Linked issue

<!-- Required. Every PR must resolve an existing issue. -->
Closes #

## Type of change

- [ ] Bug fix (non-breaking)
- [ ] New feature (non-breaking)
- [ ] Breaking change
- [ ] Documentation only

## Build and test proof

<!--
REQUIRED. Paste the real output from your machine: the debug variant must
compile cleanly and unit tests must pass (CONTRIBUTING.md).
-->

```text
$ ./gradlew compileDebugKotlin testDebugUnitTest
# ... paste the tail showing BUILD SUCCESSFUL and the unit-test results ...
```

## Contributor checklist

- [ ] `./gradlew compileDebugKotlin` builds cleanly and `./gradlew testDebugUnitTest` passes (proof above).
- [ ] **New feature → new unit tests** where testable in isolation, or **bug fix → a regression test that fails without the fix**.
- [ ] UI changes are noted for the maintainer to verify on a device (there is no automated UI coverage).
- [ ] Every new file carries the **SPDX headers** (`LGPL-3.0-or-later`); `reuse lint` passes.
- [ ] Wire types stay in sync with the host `wire-protocol` (no client-only ops the host does not serve).
- [ ] If user-visible behaviour changed, I updated the relevant page under `docs/User/`.
- [ ] Commit messages follow the project style (summary + why) and each commit is **signed off** (`git commit -s`, DCO).
- [ ] I signed the [CLA](https://github.com/Verzeta/Verzeta-Android/blob/main/CLA.md).
- [ ] This PR does **one thing** and is not a declined category (telemetry, phone-home, forced accounts, SPDX removal, or a style-only mass change).

## Notes for the reviewer

<!-- Screenshots or a screen recording for UI changes are appreciated. -->
