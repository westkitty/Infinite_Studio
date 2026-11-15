# Infinite Studio — Arena build handoff

Use **Arena Agent Mode** (https://arena.ai/agent), rather than ordinary image Battle mode.

Best route, if you have an actual intended GitHub repository: connect that repository in Arena Agent Mode, inspect its branch and state, and paste `LM_ARENA_MASTER_BUILD_PROMPT.txt`. No repo creation/push is authorized by these instructions.

**No connected repository:** Upload `ARENA_SOURCE_SNAPSHOT.txt` and `LM_ARENA_MASTER_BUILD_PROMPT.txt` using Arena Agent Mode's **+** file control, or paste the complete master prompt into chat while attaching the source snapshot. The source snapshot is a textual reconstruction of the original ZIP's source files; it contains no generated screenshots. Ask Arena to first reconstruct paths and verify hashes, then build in its sandbox. Do not upload the `.zip` directly unless Arena explicitly supports it: official upload documentation as of October 20, 2026 lists `.txt` but not ZIP for Agent Mode. You may also upload `studio_futures.png` and `studio_home.png` from the original bundle as visual references, but don't substitute previews for source.

Download the actual workspace at the end using Arena Agent Mode's documented `/download-workspace` URL suffix. Save the resulting ZIP/source and BUILD_STATUS.md, QA_EVIDENCE.md, and hand them back to ChatGPT for independent QQQ and visual/runtime validation. A preview link is not source delivery.

Do not enter personal OpenAI/Gemini API keys in Arena prompts or upload them in project files. Keep paid calls disabled.

The output may need multiple Arena turns: the prompt requires all 20 capability groups, with honest partial-state reporting and continuation. Never treat a completed plan or attractive preview as completed engineering.
