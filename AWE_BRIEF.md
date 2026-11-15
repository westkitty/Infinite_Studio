# The Futures Table — Transformation Brief

**Source state:** `original_source.html` (observed), original S-Pen/touch canvas features observed in source; actual tablet pressure and performance unverified. The source was already a credible drawing tool with brushes, symmetry, layers, import/export, and rectangular/lasso AI entry points. It lacked a safe real image-edit integration, had no editable project file, and its AI request would have bypassed a real selection mask.

**Purpose:** An artist begins with a mark and remains the author of what it becomes. Keep painting and selection immediate on a tablet. Make generated images subordinate to art direction, never a destructive shortcut.

**Experience hypothesis:** The ordinary expectation is that clicking “Render” commits a single opaque answer. Replacing that mechanism with a *table of reversible futures* changes AI from a one-shot verdict to a medium for artistic comparison. This is a design hypothesis, not a claim that awe was measured.

## Three genuinely different challengers

### A — The Futures Table (selected for the working experiment)
**Mechanism:** branching alternatives and a conservation boundary. **Action:** draw → select → inspect three studies → peel original versus candidate → accept exactly one on a separate layer. **Change in interpretation:** an AI suggestion is one possible future; the original mark is never discarded. **Risk:** candidates can become a decorative gimmick if they cannot be compared and committed reliably. **Counterargument:** an ordinary preview/undo might already be enough for basic edits. **Test:** verify that underlying layers remain byte-identical after acceptance, a candidate creates a new layer, undo/redo traverses the change, and save/reopen preserves the result. **Proof level:** executed in headless Chromium for local studies and local compositing; live model generation unverified.

### B — The Constraint Constellation (concept-only)
**Mechanism:** make visually meaningful annotations into structured constraints. **Action:** pin a silhouette, paint a material region, label a protected feature and a forbidden alteration, and ask an image model to revise only a chosen region. **Change in interpretation:** a drawing becomes a negotiable contract rather than a picture plus a vague prompt. **Risk:** the system may infer wrong semantics; structure can become an illusion of model precision. **Safeguard:** show every proposed constraint, separate user-authored locks from model guesses, and validate actual pixels before acceptance. **Test:** one reference-locked character edit with a predeclared facial/proportion invariant; independent review must detect real drift. **Proof level:** described only.

### C — The World Orchard (concept-only)
**Mechanism:** turn branches into navigable spatial history and reusable scene components. **Action:** zoom through a drawing to open variations of its objects, shots and environments; revisit where each decision came from and reuse a prior choice as a new reference. **Change in interpretation:** the canvas becomes an authored creative world rather than an endless sheet. **Risk:** immense complexity, memory pressure, and semantic drift; this requires sparse tiling and strong provenance. **Test:** representative scene with 40+ authored regions and 100+ history nodes under a measured tablet memory budget. **Proof level:** described only.

## Divergence check

A changes **decision timing**: choose among reversible outcomes. B changes **authorial input**: constraints are drawable and inspectable. C changes **navigation and memory**: the space becomes a structured history of worlds. They are not cosmetic variations on one idea.

## Gates and verdict

| Gate | A | B | C |
| --- | --- | --- | --- |
| Core purpose | PASS in proposed mechanism | PASS | PASS with scope control |
| Protect original user art | PASS in tested local commit | Requires invariant system | Requires provenance/history system |
| Source and canon truth | PASS for user-supplied source; no invented character art | UNVERIFIED until reference handling exists | UNVERIFIED |
| Accessibility and reduced motion | Responsive and keyboard behavior partly tested | Design required | Design required |
| Feasibility | Local demonstration VERIFIED | EXPERIMENT | LATER |
| External cost/action rights | No calls by default; explicit confirmation path only | Must be gated | Must be gated |
| Demonstrability | Real browser proof | Awaiting small proof | Awaiting architecture proof |

**Recommended:** A, because it creates a real changed interaction while keeping the original tool recognizable and testable. **Next:** B as the highest-leverage genuinely new tool once live API integration is proven. **Defer:** C until the raster memory model is replaced with tiled/sparse storage. **No-change finding:** keep the S-Pen-first brush and gesture model instead of replacing it with a generic image-prompt chatbot.

## Signature moment — observed demonstration

1. The visitor draws a line. The original mark exists as a real editable layer.
2. A rectangular selection is drawn around that mark. The system creates an actual alpha mask and contextual crop.
3. Three **labeled local studies** appear side by side. The visitor chooses one and scrubs a before/after divider through the image.
4. “Accept” creates a new layer clipped to the selection, preserving all pixels on the pre-existing layers.
5. Undo removes the accepted layer; redo restores it. Saving and reopening restores the editable document.

The memorable causal idea is **a possibility may be examined before it becomes part of the artwork**. Returning to the editor should increase trust because accepted changes remain reversible. The human validation question is: *Did you feel you had more control over the outcome than with a single Render button?* No audience evaluation has been conducted.

## Decision-changing external precedents

InvokeAI's distinct canvas layers/selection model and Krita AI Diffusion's context-aware regional edits indicate that masks, context and non-destructive operations should be first-class interfaces, not afterthoughts. OpenAI's current Image API supports image-edit masks with alpha; its own documentation cautions that mask adherence is approximate. Therefore the canvas must impose the final clipping boundary itself.

OpenAI image documentation: https://developers.openai.com/api/docs/guides/image-generation
