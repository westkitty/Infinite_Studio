# Infinite Studio C01-C05 - source upgrade notes

Pinned target: `westkitty/Infinite_Studio` at commit `db230c8a63f9acade76b25a6189ce5ae30188a14`.
The overlay applies ONLY to a clean local clone with that exact HEAD and verified owner URL.
The overlay intentionally makes no Git commit, push, deployment, or paid network inference call.

## C01 - Legacy source provenance
Restores nine exact original browser files from commit `242534065f7035f57a44bb400cb30bc2cd74387d` beneath `legacy-browser/`, retaining their original bytes.
Checks original_source.html against SHA-256 `0e1497037505c011d7837874f5a277c5b65cc030161e36fc33102218f9970b76` before any source mutation.
Never replaces or removes the Kotlin Android app.

## C02 - Bounded bitmap paths
Adds renderer functions for bounded previews and bounded single-layer regions. Replaces full canvas bitmap allocation in Futures acceptance, Forensics heatmap, mask comparison and local edge-outpainting. Effects remain editable and original layers stay unmodified. Preview sampling of outpaint edge color is now bounded rather than full-resolution; precise color equality with prior output requires tests.
The legacy `Foundry` component export and any other dense paths remain separate known work, not silently claimed fixed. Device memory and functional regression tests remain required before release.

## C03 - S-Pen test laboratory
`tools/android_device_qa.sh` is a read-only physical-device collection harness. It does not synthesize successful hardware tests or install an APK. D01-D09 require interactive human pen observations. Compare real 120 Hz frames and pressure curves against acceptance criteria.

## C04 - Optional image-edit provider
Host-only `companion/remote_openai.py` implements a real multipart image-edit API call, accepts only supported model IDs, rejects mismatched alpha masks, uses fixed TLS endpoint, no implicit retries and no embedded APK secrets. `companion/server.py` remains deterministic by default. Remote mode additionally requires per-job consent, host API credentials, INFINITE_COMPANION_ENABLE_PAID_IMAGE_EDIT=yes and a host request cap (default 1). Remote calls were NOT executed in this build; billing, model output quality and in-flight cancellation remain unverified.

## C05 - Portable Canon Passport
Adds hash-checked portable reference contracts, visual locks, untrusted import handling, a document/archive field, JSON schema and Foundry ZIP metadata export. The import path never treats a claimed approval as verified by the current user. It does not register canonical artwork, scan private storage, or substitute approval for visual review. Character Studio UI is not yet a complete passport authoring/import surface.

## Required validation before shipping APK
1. Pin the clone and run the overlay preflight first; back up the clone independently.
2. Apply overlay, inspect exact diff, and rerun Gradle/Robolectric and Python companion suites on a supported Android SDK/Gradle environment.
3. Run physical Tab S9 Ultra D01-D09, with particular attention to alpha, selection seams, large-canvas memory and file recovery.
4. Verify that the generated debug APK contains no credentials and that native source/regression protections remain intact.
5. Never call the resulting DEBUG APK a release-signed, production-tested artifact.

## Environmental status during authoring
This ChatGPT sandbox had JDK 21 but NO Gradle, Android SDK, Kotlin compiler or network package access. The overlay was authored against current GitHub source inspections, not compiled against the complete native project here. Only the included offline Python image-edit transport tests and script preflights could be exercised. No APK was built during this authoring session.
