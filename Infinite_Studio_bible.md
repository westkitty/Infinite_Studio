# Infinite Studio Project Bible

## 2026-11-20 - C01-C05 source overlay

### Repository state
- Intended target: westkitty/Infinite_Studio, main at db230c8a63f9acade76b25a6189ce5ae30188a14.
- Artifact type: native Android Kotlin/Jetpack Compose application with a separate loopback Python companion.
- Source-overlay application is a local filesystem mutation; this entry does not mean a Git commit or APK build occurred.

### Purpose and protected behavior
- Preserve the user's original browser artwork and all authored layer pixels.
- Protect S-Pen-first ink, local offline Futures, mask containment, undo/redo, portable .iastudio archives, explicit approvals and zero-secret Android client.

### Changes in this source-overlay package
- C01: verified-history legacy browser recovery into legacy-browser/.
- C02: bounded tile acceptance, Forensics, outpaint and preview paths; Foundry export remains a dense path.
- C03: read-only physical QA recorder; D01-D09 require real-device observation.
- C04: host-side image edits using strict opt-in, model allowlist and per-process request cap.
- C05: portable Canon Passport contract plus document/archive/Foundry metadata plumbing; no imported approval is automatically trusted.

### Validation limits
- Python adapter tests and shell syntax tests ran in the ChatGPT sandbox.
- Kotlin compile, Gradle tests, debug APK build, real-device stylus testing and paid inference were not run there because SDK/Gradle/device and permitted provider credentials were unavailable.
- Verify all regressions on an Android SDK host before release. Preserve source revision guards and do not publish without another instruction.

### Remaining work
1. Apply against pinned clean clone and inspect change set.
2. Run Gradle/Robolectric and Python suites and fix any compilation or regression failures.
3. Run D01-D09 on physical Samsung Tab S9 Ultra.
4. Finish character-passport UI import/export and evaluate remaining dense Foundry export paths.
5. Only then consider a release candidate; debug APK is not a signed production release.

## 2026-11-22 - C01-C05 implementation commit and validation evidence

- Implementation commit: 6e0f4158fed76ee9c8a1b7fe7bdcd723df11c425.
- Changed scope: exactly 25 intended source, historical-reference, schema, test, and build/QA script files; generated Python bytecode was excluded.
- The historical original browser source SHA-256 matched 0e1497037505c011d7837874f5a277c5b65cc030161e36fc33102218f9970b76. Historical whitespace was retained to preserve source bytes.
- Local Big Mac validation: six Python companion tests passed; shell syntax checks passed; staged diff check passed excluding unmodified historical whitespace.
- Kotlin compilation, Android Gradle tests, APK build, S-Pen hardware checks, paid provider execution and end-to-end creative workflows remain UNVERIFIED. These are not a release claim.
- Git remote verification and push outcome must be checked independently against GitHub after this documentation follow-up.
