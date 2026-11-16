# INFINITE STUDIO // NATIVE ANDROID MAXIMUM BUILD CONTRACT

**Executor:** Google AI Studio, Build mode, **Android** selected (Antigravity Agent)  
**Product:** Infinite Studio - The Futures Table  
**Primary device:** Samsung Galaxy Tab S9 Ultra, S-Pen, landscape-first  
**Delivery:** Real native application + complete source ZIP + executable evidence + independently repairable companion architecture  
**Directive:** BUILD THE SYSTEM, NOT THE DEMONSTRATION OF A SYSTEM.

## 0. Mission and definition of done

You are the full initial engineering team for Infinite Studio: Android architect, graphics engineer, input engineer, document-format designer, UI designer, local processing engineer, test engineer, and integration engineer.

**Deliver the most complete, working, source-exportable native Android creative studio your actual tools can produce.** The objective is not a quick prototype, an MVP with twenty disabled tabs, a pretty UI, or a plan. Implement all feasible application functionality and the underlying infrastructure necessary for its future AI and media capabilities. An independent engineer should inherit a coherent codebase with bounded repairs and externally blocked integrations, **not** a hollow frontend requiring architecture to be invented afterward.

The first working drawing journey is a **gate**, not permission to stop. Continue through the full feature catalog until you hit a genuine, evidenced runtime/tool limit or all requirements are verified. Preserve a working build at each milestone; do not replace quality with sheer feature count.

Acceptance has three levels:

1. **Actually working on Android:** real UI -> handler -> engine -> document transaction -> visible result -> save/reopen -> tested proof.
2. **Implemented but environment-blocked:** complete, compilable contracts/adapters/mock or local substitute; isolated real dependency; deterministic fixtures; precise activation and verification steps. Do not label it working end-to-end.
3. **Unimplemented:** a documented gap with affected files, reproducible blocker, exact next action, and test that will prove closure. Do not hide gaps behind UI buttons.

A beautiful first launch does not satisfy this assignment. A source tree that compiles but cannot paint, reopen, and export is not satisfactory. A twenty-feature menu backed by empty stubs is not satisfactory.

## 1. Sources, authority, and preflight

Existing project: https://github.com/westkitty/Infinite_Studio  
Verified baseline commit: `242534065f7035f57a44bb400cb30bc2cd74387d`  
Drive archive: https://drive.google.com/drive/folders/19VEuVESgZwIMQXtblPSiga39v7zqSIdc

Read these if reachable: `README.md`, `OPERATIONAL_STATE.md`, `BUILD_STATUS.md`, `QA_REPORT.md`, `docs/LM_ARENA_MASTER_BUILD_PROMPT.txt`, `docs/SOURCE_PROVENANCE.md`, `index.html`, `laboratory.js`, `laboratory.css`, `laboratory.ui.html`, `original_source.html`, `server.py`, and `tests/`. Inspect relevant actual code; a README is not proof of execution. The existing browser editor is a **reference implementation and migration source**, not the destination runtime. Its Futures Table already has three visibly different **local** studies, reversible accept-as-layer, comparison, selection masks, editable `.iastudio` projects, undo/redo, and optional guarded backend. Do not relabel local processing as generated AI imagery.

**Protected original SHA-256:** `original_source.html` = `0e1497037505c011d7837874f5a277c5b65cc030161e36fc33102218f9970b76`. Preserve it byte-for-byte wherever archived or copied. Preserve original layer pixels, behavior, and source history. The repository's older browser-oriented and Arena-specific instructions describe prior delivery targets; **this newer user directive overrides only the target runtime: native Android**. It does not cancel any feature, security, or source-preservation obligation.

First inspect the selected AI Studio platform and its actual build/export permissions. Use GitHub or Drive source import only if this Android builder genuinely supports it; otherwise use attached source files/ZIP. Do **not** assume Android-to-GitHub export. If neither source can be read, build against this full specification without claiming verified legacy compatibility; record exactly what source is required to finish that check.

Do not alter `main`, push to a repository, open a PR, delete anything from Drive, publish to Play, deploy a server, introduce an analytics SDK, spend money, or run live paid image inference. Source generation and emulator compilation in the AI Studio sandbox are authorized.

## 2. Platform contract - build to actual AI Studio limits

Deliver a genuine Android app with **Kotlin, Jetpack Compose, Material 3 where appropriate, one Activity and one Android Gradle module**. Organize domain components into separate packages/classes, not a monolithic `MainActivity.kt`. Use Android SDK graphics and `MotionEvent` APIs. Consider stable AndroidX Ink for low-latency inking if dependency resolution succeeds; keep a working SDK-only path otherwise. No NDK/C++/JNI, no XML-layout-based UI, no assumption of a hosted backend, no mandatory Google services, and no WebView wrapper.

Pin a mutually compatible Gradle/Android Gradle Plugin/Kotlin/Compose toolchain from versions actually available in this builder. Commit complete Gradle wrapper metadata to the exported source if supported. No `+` or unstable dynamic version dependencies. Verify library licensing and avoid shipping font files or copyrighted stock assets without rights.

Android Build mode is client-side. A Python/Node companion cannot magically run inside its emulator. **Nevertheless, build its wire protocol, Kotlin client, fixture-based local mock, error handling, independent backend source files when the export can carry them, and precise handoff.** A server that cannot execute here is an external integration gap, not a reason to omit all its surrounding engineering.

Export the complete project as a source ZIP, including generated documents, schema definitions, fixtures and any separately packaged companion sources. An APK is desirable if this UI provides a supported build/install/export route, but do not claim an APK download exists unless the tool actually creates it. Real S-Pen behavior cannot be certified using a generic browser-streamed emulator.

## 3. Architecture - the parts the next engineer must NOT need to invent

Implement a real vertical architecture with explicit seams:

- `input/`: stylus/palm/gesture routing; provisional stroke lifecycle; historical samples, pressure, orientation, hover, cancellation.
- `render/`: viewport transform, tile renderer, dirty regions, compositor, previews, masking, color-management assumptions, caches and disposal.
- `document/`: versioned semantic scene graph, stable IDs, transactions, serializer, migrations, validation, snapshots and recovery.
- `tools/`: raster brush strategies, fill, eraser, smudge, selections, lasso, transforms, vector/text, shapes and symmetry.
- `features/`: Futures Table, Object Surgery, Spatial Stage, Preview, Recipes, Time Canvas, Material Intelligence, Forensics, Production Foundry, Typography, Vault, AI Brush, Constraints, Character Studio, Art Director, Outpainting, Branches, World Orchard and Asset Workshop.
- `services/`: interface-first optional provider router, consent/egress boundary, client transport, cancellation, offline mock and protocol fixtures.
- `storage/`: local project library, Android Storage Access Framework, atomic import/export, crash journal, indexes and metadata.
- `ui/`: professionally composed tablet workspace, tool palettes, panels, focus handling, settings, diagnostics and accessible states.
- `test/`: unit and integration tests for actual domain/serialization/invariants plus UI test paths where supported.

These are logical responsibilities; organize them sensibly inside the one Android module. Maintain a dependency rule: domain/document/geometry never depend on Composables or Activity; screen state and services depend on domain interfaces; renderer consumes model snapshots; exporters read committed state. Avoid shared mutable singletons holding live bitmap objects indefinitely.

### The canonical document

Design an explicit schema with document ID, schema version, bounds/units, tile index, ordered layers, raster tile assets, vector paths, exact text, editable strokes, semantic object references, masks, transforms, materials, depth/camera, animation tracks, workflow graph, linked assets, content hashes, branches, revision/provenance records, and optional provider metadata. Identify required vs optional fields, type validation and migrations. Preserve unknown forward-compatible fields when feasible; fail safely on unsupported mandatory versions. **Never silently flatten a legacy layered document.**

Use a documented archive-based `.iastudio` container (manifest plus individual typed binary assets) if workable; specify paths and checksums, write exact deterministic test fixtures, and import the actual legacy format after inspecting its source. Use atomic write/replacement where possible, save to SAF URIs safely, and keep a recoverable journal. Autosave must not replace user-controlled backups. Include corrupt/truncated/oversized/zip-slip inputs in validation tests. Keep document data separate from caches, thumbnails and transient previews.

### Transactions and history

An edit is a reversible command with declared input version, changed object IDs/tile bounds, undo payload or recomputable inverse, commit point, redo semantics, cancellation and provenance. Undo/redo works across **raster, masks, vector, layers, transforms, animation, materials and candidate acceptance**, not just brush strokes. Bound memory, compress snapshots when necessary, and spill safely to disk. Branch history and ordinary undo history must not alias mutable bitmap references.

### Rendering and memory

Implement sparse tiles, lazily allocated and clipped to document bounds, with viewport culling, dirty-region redraw, cache pressure handling, reusable buffers, finite bitmap allocation budgets and export streaming/tile stitching when practical. Separate low-latency provisional stroke rendering from committed compositing. Implement real alpha compositing, opacity, supported blend modes, masks and per-layer transforms. Never call a fixed-size bitmap canvas truly infinite. Profile allocation counts and memory under representative large documents; clearly report emulator vs real-device evidence.

## 4. S-Pen-first drawing is a product invariant

Build real pressure-responsive painting: pencil, technical ink, marker, airbrush, wash, eraser, smudge, fill, selection brush and user-defined brush presets. Give brushes genuinely different algorithms and settings (radius, flow, opacity, hardness, spacing, smoothing, pressure curve, angle or texture where supported). Brush labels without behavioral differences do not pass.

Support stylus vs finger intent, hover, supported tilt/orientation, historical `MotionEvent` points, one or more fingers for pan/zoom and optional rotation, active selection geometry, world/display coordinate mapping, cursor preview and low-latency provisional strokes. Correctly handle `ACTION_CANCEL` and Android 13+ `FLAG_CANCELED`, including non-primary pointer-up, rolling back provisional marks. Preserve pen input when a finger rests on the screen; add a user-controlled pen-only/palm guard. Predicted samples, if used, are temporary and replaced by real points; never save predictions as canonical strokes.

Make the entire path testable with synthetic motion-event sequences, canceled transactions and coordinate round trips. Use Android's built-in input APIs, not browser Pointer Events. An emulator may establish general gesture behavior but **cannot prove S-Pen pressure, hover, palm rejection, latency or thermal stability** on a Tab S9 Ultra. Prepare explicit device tests for those.

## 5. A real tablet studio, not feature theater

Keep the artwork central in a responsive landscape-first workspace with collapsible/dockable left brush rail, right layer/object inspector, contextual bottom controls, discreet project/navigation strip, and optional timeline/recipe/diagnostics panels. Portrait mode remains usable. Build compact but accessible targets, clear undo/save status, visible focus/content descriptions, keyboard shortcuts, reduced motion, dark/light palettes, actual empty/loading/error/permission/offline states, and intentional destructive confirmations. Use material surfaces with restraint, not oversized dashboard cards or permanent chatbot UI. Avoid unrelated visual spectacle, fake loading, needless account sign-in and unlicensed assets.

### Signature creative experience: 'One Mark, Three Futures, Zero Sacrificed Originals'

A user draws or imports an object, marks a region with the S-Pen, optionally locks its outline, opens Futures, compares three **truthfully labeled local candidates** at once or individually with a scrubber, accepts one as a new layer/branch, and immediately sees unchanged originals plus a reversible edit history. From that accepted branch the user can add vector dialogue, material shading, time keyframes, and export a reusable asset with source lineage. The system reveals possibilities without stealing authorship. **Build this as a real integrated end-to-end journey and a repeatable offline demo/test fixture**, not a splash-screen narrative.

Make the advanced system feel *earned*: the same authored object, selection, constraints and history should pass through multiple tools without duplicate import, lossy flattening, or broken state. A second encounter should reveal that the earlier version remains recoverable. If this signature route cannot run, the product is not complete however beautiful the UI is.

## 6. The entire twenty-system feature scope - implement, do not merely name

**F01 - Object Surgery.** Import flattened imagery; use actual polygon/brush/color-guided manual segmentation and refinement, optional automatic provider boundary, create transparent object layer, move/scale/rotate with correct alpha edges, original preservation, separate labeled inferred background-repair candidate, save/reopen and undo. Test the extracted object's alpha, bounds and independence from source.

**F02 - Spatial Stage.** Editable 2.5D depth planes, vanishing guides, proxy geometry, camera position/framing and light controls. Local deterministic transformed scene preview; label invented unseen surfaces speculative. Store scene/camera state and restore it.

**F03 - Living Preview.** Optional local thumbnail/alternate-render view that follows relevant strokes incrementally; debounce, backpressure, cancel stale jobs, pause during low-power/heavy work, consistent revision IDs. Mark remote model previews separately; no silent costs or source edits.

**F04 - Creative Recipes.** Serializable DAG engine with typed operation nodes, ports, editable parameters, topological execution, cancellation, dependency invalidation, content-based cache keys, resumption/checkpoints and failure propagation. Ship real local recipe operators for cleanup, line work, palette/flat color, material preview and export; test only descendants recompute after upstream changes.

**F05 - Time Canvas.** Timeline with frame durations, keyframes, onion skins, layer/transform tracks, scrubbing, playback, frame duplication and frame/sprite-sheet export. Provide real timestamps and verify output frame count/dimensions. MP4 is optional only where a functional native encoder path can be tested; preserve video export contracts otherwise.

**F06 - Material Intelligence.** Define region-bound editable base color, roughness, metalness, alpha, glow/emission and relief approximations. Render meaningful deterministic shading/lighting preview, preserve material linkage, verify a lighting adjustment survives document round-trip.

**F07 - Visual Forensics.** Real before/after pixel diff, heat map, changed-region counts, mask-outside mutation count, alpha/silhouette comparisons. Human-review checklist for anatomy/perspective/continuity is advisory; do not claim deterministic visual judgment or autonomous canon approval.

**F08 - Production Foundry.** Export source layers/transparent components, pivots, coordinate metadata, scale, atlases/sprite sheets and licensing/provenance manifest. Include exact reimport and deterministic metadata validation. Prepare rigging handoff fields without pretending native 3D auto-rigging exists.

**F09 - Vector and Typography Studio.** Real editable paths, Bezier handles, shapes, text boxes, glyph/layout metrics, wrapping, speech balloons and tails, vector selection/transform and SVG export with exact authored text. No model-generated text substituted for production lettering.

**F10 - Visual Memory Vault.** User-selected local asset import, thumbnails, metadata, tags, color features, simple source-grounded local similarity/search; attach references without copying unnecessarily. Preserve rights/source IDs and explicit approval state. No scanning all private storage or silently uploading files.

**P01 - Futures Table.** Three independent deterministic local studies with inspectable candidates, comparison slider, accept-as-new-layer, discard, branch provenance, no original pixels touched, save/reopen and undo. An unavailable AI provider must not break local Futures.

**P02 - AI Brush.** Draw editable visual guidance strokes, arrows, color hints, contextual masks and annotations; bind to real selection/object coordinates. Compose selected edits locally within exact permitted masks; optional model requests go through disabled/provider interface.

**P03 - Constraint Constellation.** Pen-authored protected silhouettes, forbidden-change regions, required traits/material notes, editable constraint ledger, references and post-candidate measurement. Enforce **zero changed committed pixels outside permitted mask**; clearly distinguish soft model instructions from hard local compositing constraints.

**P04 - Character Studio.** Multiple opt-in references, notes, palette, proportion rulers, pose/expression panels, turnaround layout, identity-comparison checkpoints and provenance. Reference approval is user-only; generated variations do not become canon automatically.

**P05 - Conversational Art Director.** In-app instruction history tied to actual document selection, layer IDs and reversible typed operations. Implement a useful local command grammar (e.g. duplicate layer, change opacity, create branch, export selected layer) and provider-ready natural-language interpretation contract. No fake chat intelligence.

**P06 - Outpainting.** Extend sparse canvas bounds, preserve old pixels, add context/masks and a reversible candidate for filled regions. Distinguish deterministic edge fill/extension from real generative outpainting; remote generation path disabled by default.

**P07 - Creative Branches.** Explicit revision DAG: fork, compare, annotate, roll back, selective merge/cherry-pick where compatible; source hashes, node IDs, linked art and independent state snapshots. Test sibling branches do not share mutable objects.

**P08 - World Orchard.** Optional spatial map of authored regions/scenes/asset links/branch provenance. Tap a node to navigate directly to a real document object or prior revision. Lazy load the view; never replace the primary drawing canvas.

**P09 - Asset Workshop.** Real templates/presets for character parts, props, backgrounds, icons and animation pieces; integrate crop, alpha, export metadata, atlas and Foundry; preserve source relationships.

**P10 - Secure Provider Router.** Define typed image/vision/segmentation/inpainting/conversation job protocols, provider capability discovery, local mock, strict egress opt-in, cancel/status/retry/idempotency, error taxonomy, and server-side-only credential expectations. Implement real client/mock tests; no live paid network use and no key embedded in APK.

All twenty must appear individually in `BUILD_STATUS.md`. A system can be VERIFIED only after visible UI, execution, persistence and appropriate test evidence; a partial or backend-blocked system remains explicitly PARTIAL or BLOCKED. No phantom success toasts.

## 7. Backend / companion - deliver the engineering, isolate the unavoidable environment gap

Google AI Studio's Android builder is **not a hosted server runtime**. Do not pretend to deploy or test an independent server from the Android emulator. Still produce the complete integration design and as much executable service code as the export mechanism permits.

Create `BACKEND_CONTRACT.md` and machine-readable OpenAPI 3.x (or equivalent exact JSON schemas) for:

- `GET /health` and `GET /capabilities`
- `POST /jobs` with typed job kind, document revision, selection/mask descriptors, privacy/egress consent, and idempotency key
- `GET /jobs/{id}` status/progress/error/cost state
- `POST /jobs/{id}/cancel`
- Authenticated bounded upload, result retrieval and deletion/retention semantics
- Provider-neutral metadata: provider, model version, source asset hashes, created time, processing mode, output provenance

Add explicit request/response fixtures, validation of image size, MIME, auth, payload bounds, consent, retries and stale document revisions; in-process mock service that supports the full job lifecycle; Android HTTP client behind an interface; end-to-end tests against the mock. Job results **must never auto-commit to artwork**.

If files external to the Android module can be included in the exported ZIP, supply `companion/` as an independent minimal Python (or another justified permissive runtime) service skeleton with implemented routes, schema validation, request limits, state machine, cancel, job expiry, tests, configuration example and README. **Default bind: loopback only; no insecure network exposure.** Credentials via host environment or secure secret storage, never in mobile build or repository. Actual multi-device remote access must await authenticated HTTPS or equivalent authenticated encrypted private transport and a separate user's approval to enable it.

If the builder cannot retain `companion/` files, include the service source in a separately downloadable sidecar or documented code file artifact, and report whether those bytes were actually exported. Do not bury pseudocode in a prose paragraph and label it a server. Do not use a second Android Gradle module.

Cloud inference, semantic segmentation, high-quality inpainting, natural-language model interpretation and remote image storage are genuinely external-dependent. Implement real **offline manual/deterministic fallbacks** and complete provider interfaces, schema/test fixtures and UI disabled states. Report exactly which algorithms require future model/service work and how to test them once connected. Never represent deterministic preview as remotely generated AI.

## 8. Offline first, permissions and privacy

Local drawing, import, layer editing, Futures local studies, vector/text, materials, recipes with local nodes, timeline, save/recovery and export must work in airplane mode. No sign-in, billing or Firebase dependency. Android Storage Access Framework (document picker) for user-selected files, URI permissions, private workspace files and robust save-as. Respect Android permissions; do not request access to all photos or storage when document pickers suffice. Never silently scan the user's Drive, GitHub or private photo library.

Implement project import size/decompression and path-traversal guards, content URI validation, safe cancellation and error handling. Keep network boundaries and explicit transfer previews. No analytics, ad SDKs, external fonts/assets with unclear rights, hardcoded API tokens, paid calls, unauthorized upload, Play Store publishing or backend deployment. Do not infer approval of visual references from filenames.

## 9. Built-in proof: tests, fixtures and inspectable receipts

### Required deterministic fixtures

Ship small synthetic test art generated by local code, not unlicensed reference imagery: transparent overlapping shapes, alpha-edge silhouette, two raster layers, a vector speech balloon with exact text, multi-frame motion, material-shaded tile, branch fork, recipe graph and a legacy `.iastudio` fixture derived from real source format when available. Include expected dimensions, IDs, image hashes or reproducible property checks. These fixtures exist for tests and diagnostics; they are not substitutes for a functioning editor.

### Essential end-to-end test journeys

**J01:** create -> pen draw -> switch brushes/colors -> layer create/reorder/hide -> pan/zoom/rotate -> undo/redo -> save -> kill/reopen -> recover exact layer/pixel state -> export PNG.

**J02:** import flattened art -> pen-refine mask -> extract object -> transform -> compare background repair as inferred -> revert -> save/reopen original unchanged.

**J03:** draw protected mask -> three local Futures -> scrub comparison -> choose candidate -> accept as separate layer -> original layer hashes unchanged -> undo/redo -> branch compare.

**J04:** vector text exact -> editable balloon tail -> material assignment -> change light -> animate two frames -> export and reimport asset bundle; text remains exact.

**J05:** execute three-stage recipe -> mutate an upstream parameter -> verify only dependent nodes recompute -> cancel and resume with consistent provenance.

**J06:** Vault import user-approved fixture -> search by tag/color -> inspect provenance -> link as reference -> export retains source/approval metadata but does not assert canon.

**J07:** offline mode -> all local tools, save/reopen/export and local candidates work -> zero remote image calls -> provider disabled state explicit.

**J08:** malformed/corrupt/oversized project input, canceled gesture, canceled job, out-of-space save, process death/restart, and unsupported document version must fail safely without losing last good project.

### Non-negotiable machine checks

- Hash pre-existing layer pixels before and after candidate acceptance: **exact equality**.
- Count changed committed pixels outside allowed mask: **zero**.
- Undo/redo and branch operations restore correct object and tile state, no aliasing.
- Save/open and export/reimport preserve supported raster, vector, text, materials, masks, timeline and references.
- Confirm different local candidate outputs really differ in pixels and are labeled local.
- Confirm exact lettering in SVG and project reload.
- Verify sprite sheet dimensions, frame count and metadata.
- Test recipe acyclicity, dependency invalidation, idempotency, cancellation and resumption.
- Detect zip-slip, untrusted MIME, oversized allocations and truncated archives.
- Disabled-paid-provider mode rejects outbound inference requests; no keys in source or artifacts.
- Verify sensible behavior on portrait/landscape, focus, accessibility, low memory and background/foreground transitions.

Compile and run app using the available Android build/emulator tools; execute tests that are actually supported. Capture the actual failure, repair once per bounded failure cluster, then rerun; do not make entire suites green by deleting assertions. If instrumentation or bitmap comparison can't execute here, include the source tests/fixtures and mark the test UNEXECUTED with exact instructions.

Report device/runtime for benchmarks. Android emulator results are not physical Samsung stylus evidence. Provide a specific `TAB_S9_ULTRA_DEVICE_QA.md` script for pressure, hover, palm rejection, stylus/finger concurrency, 60/120 Hz frame pacing when applicable, long-session memory, rotation and battery behavior. Never pretend that test already ran.

## 10. Built-in operational observability and graceful recovery

Add a hidden-by-default Developer Diagnostics screen to inspect: app/build version, active document version, layer/object counts, allocated tiles, estimated cache/history bytes, save journal state, current tool/pointer type, current revision, background tasks, last failures, active provider mode, actual service URL if configured (redact secrets) and export checks. Provide user-readable errors and recovery options, and a redactable diagnostics report that excludes art pixels and secrets.

Make rendering deterministic enough for golden fixtures; identify GPU- and hardware-specific variations. Include actual application icon and launch assets created locally or from legitimately licensed resources, but do not waste the build on branding before the core drawing journey passes. No artificial loading animations masking absent work.

## 11. Execution strategy - maximal real completion within one session

1. **Preflight (bounded):** inspect sources, record exact accessible baseline, verify Android builder/toolchain/export, choose domain modules and data schema; identify the first compilation constraint.
2. **Foundation and compiled vertical slice:** document model, tile compositor, MotionEvent router, core drawing, layers, transactions, storage; run actual app and verify J01.
3. **Protect the central promise:** selections, masking, local studies and Futures Table, compare/accept/undo, original-pixel proof; verify J03.
4. **Build structural engines:** reusable geometry, vector/text, semantic object extraction, materials, timeline, recipes, branching, export and Vault; validate J02/J04/J05/J06.
5. **Wire advanced feature UI and engines:** complete F01-F10 and P01-P10 with shared document transactions, meaningful offline behavior, persistence and tests; no disabled showcase buttons advertised as complete.
6. **Provider/companion readiness:** implement mock job lifecycle, Android client, explicit opt-in boundary, API schema, fixtures, companion code if export supports it.
7. **System QA and packaging:** run broad relevant tests after cross-cutting changes, compile/launch, inspect UI at tablet aspect ratios, verify export ZIP, produce source-map, evidence, handoff and repair queue.

Parallelize only independent source tasks when tools support it. Prefer one coherent implementation and one focused repair pass per affected subsystem; do not keep rewriting the same architecture. Preserve known-working code while extending. Use no paid API or additional service. Do not assume extra runtime privileges.

If the execution environment runs out of context/time, **save/export the strongest coherent source state**, update the status ledger and specify the exact next file/function/test. A genuine tool limitation is a valid stopping point; an arbitrary MVP cutoff is not. Never say "all implemented" unless each of the twenty has passed its own integration gate.

## 12. Required deliverables (not optional prose)

Provide the following in the exported project or explicit separately downloadable artifacts:

1. **Complete Android Studio-compatible source ZIP** with actual Kotlin/Compose project, Gradle wrapper/build configuration, manifest, resources, source, tests and licenses. One buildable Android app module.
2. **Installable/debuggable app** through a supported AI Studio emulator/install path; APK only if actually exposed. State where/how to obtain it.
3. `README.md` - exact clean setup/build/run, offline drawing workflow, project storage, feature coverage and limitations.
4. `ARCHITECTURE.md` - dependency boundaries, rendering, input, command transactions, schema, storage, service adapters and lifecycle.
5. `DOCUMENT_FORMAT.md` plus `schema/` and fixtures - project archive layout, migrations, checksums, failure/forward-version behavior and compatibility status.
6. `BACKEND_CONTRACT.md` plus OpenAPI/schema/fixtures and mock client; separate companion source when genuinely exportable.
7. `BUILD_STATUS.md` - **all 20 IDs F01-F10 and P01-P10**, statuses from VERIFIED / IMPLEMENTED_UNVERIFIED / PARTIAL / BLOCKED / NOT_STARTED, exact UI and source entry, persistence, tests and remaining gaps.
8. `REQUIREMENT_TRACEABILITY.md` - each requirement -> specific source class/function -> test/fixture -> observed result or explicit gap; no handwaving.
9. `QA_EVIDENCE.md` - commands and tests actually run, pass/fail, emulator device profile, runtime observations, unverified hardware, reproducibility, no-spend/no-deployment proof scope.
10. `TAB_S9_ULTRA_DEVICE_QA.md` - physical-device script, expected and measured fields, left untested until performed.
11. `SECURITY_PRIVACY.md` - local data, export trust boundaries, permission model, auth/TLS contract, disabled paid behavior, risks and policies.
12. `DEPENDENCIES_LICENSES.md` - pinned dependency versions, purpose and redistribution provenance.
13. `REPAIR_QUEUE.md` - ranked P0/P1/P2 defects/gaps with reproduction, suspected or known cause, paths, smallest safe repair, regression test and acceptance.
14. `HANDOFF_FOR_CHATGPT.md` - exact export/current revision, source tree, relevant hashes, build commands, current working journeys, broken/blocked areas, device questions, companion/backend activation steps, and order of independent QQQ audit.
15. `SOURCE_MANIFEST.json` - file inventory, roles, sources, generated/derived status, build toolchain and artifact hashes where supported.
16. A **repeatable offline sample project** exercising Futures, masks, layers, vector lettering, material, branches, a recipe and timeline, with no external paid service.

If GitHub/Drive writes aren't available in Android Build mode, do not try to simulate them. Deliver the ZIP and docs with stable paths suitable for a later authorized commit to `westkitty/Infinite_Studio` on a development branch. Preserve the original web app and its Git history. Do not publish, merge or deploy anything.

## 13. Final response contract

At completion, report concisely:

- Actual Android build result, emulator launch and installation/export method.
- Exact source ZIP/artifact location provided by the platform.
- Number of the twenty features VERIFIED vs PARTIAL/BLOCKED/NOT_STARTED, with the detailed mapping in `BUILD_STATUS.md`.
- Top failures that still require repair (no vague "polish remaining").
- Whether backend source, schemas and mock integration were **actually exported**; whether a real server executed (expected: **NO**).
- Test commands and actual result summary, explicitly marking skipped/unrun tests.
- Any source/Google Drive/GitHub limitations.
- Exact first action for a fresh engineer opening `HANDOFF_FOR_CHATGPT.md`.

**No unsupported claims. No decorative completion theater. No rewrite of the preserved browser original. No hidden spending.**

## 14. Begin the implementation immediately

Select the **Android** platform. Inspect available source and environment. Write and compile the native project. Finish the real pen/layer/document path. Build the complete remaining feasible systems and the infrastructure around external dependencies. Run actual tests. Correct observed failures. Export the entire source and evidence.

**The signature promise is not "AI paints for you." It is "Your mark survives every future you explore." Implement that promise in code and prove it.**
