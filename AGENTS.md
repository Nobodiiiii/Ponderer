# Ponderer Project Prompt

Apply these rules to the entire repository unless a deeper `AGENTS.md` overrides them.

## Repository Grounding

1. Check local facts before concluding. Inspect repository code, generated outputs, the local `.minecraft` instance, logs, and crash reports before deciding anything. Do not invent missing facts.
2. Confirm active modules in `settings.gradle` before making cross-platform assumptions. Keep shared logic in `Common/` and loader-specific integration in the appropriate platform module.
3. Treat Gradle `Test` tasks as unavailable unless the repository explicitly re-enables them. Prefer compile checks and bounded smoke tests instead of assuming unit tests exist.

## Errors And Runtime Regressions

1. When the task involves a crash, exception, launch failure, or behavior regression, use the global `$minecraft-crash-triage` skill at `C:/Users/Nobodiiiii/.codex/skills/minecraft-crash-triage/SKILL.md`.
2. Follow that skill's workflow for log lookup, evidence gathering, and missing-file reporting instead of restating the crash-triage procedure here.

## Verification

1. After making changes, run the smallest relevant verification yourself. Do not stop at analysis when a local verification step is available.
2. Prefer targeted compile checks:
   - `Common/` changes: run `:Common:compileJava` and any affected loader compile task.
   - `Forge/` changes: run `:Forge:compileJava`.
   - `Fabric/` changes: run `:Fabric:compileJava`.
   - Build logic or shared resource changes: run the smallest broader task that covers the impact.
3. Prefer existing project scripts or Gradle tasks for smoke tests when they match the scope of the change.
4. Every long-running verification must stop automatically. Any `runClient`, `test.py`, or observation run needs a clear timeout, exit path, and cleanup behavior so no orphaned process remains.

## Execution Discipline

1. When the user gives a plan, target state, or requested stopping point, keep executing until that requested point is reached.
2. Do not pause mid-plan just to ask for confirmation.
3. Pause only when a real blocker appears or when the next action has non-obvious destructive, irreversible, or high-risk consequences that genuinely need user approval.

## Communication

1. In progress updates and final replies, always state:
   - what changed
   - how it was verified
   - what is blocked or still unverified
2. If verification could not run, say why and name the exact command or file that would be checked next.
