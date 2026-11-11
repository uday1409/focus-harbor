# What the agent-facing files mean

The repository is agent-ready for development, not an app that runs an AI agent.
There is no single universal "AI-native repository" standard and no need for an
LLM, vector database, agent swarm, or proprietary configuration to build this app.
The design uses documented formats, explicit constraints, executable checks, and
small, task-focused context instead.

| Path | Meaning to an agent |
| --- | --- |
| `AGENTS.md` | The first-read contract: project map, exact commands, safety invariants, and completion rules. It complements the human README. |
| `.agents/` | A home for reusable agent resources in this project, not background agents or executable permissions. |
| `.agents/skills/` | On-demand task runbooks. Each skill has a `SKILL.md` with `name` and `description` YAML frontmatter following the open Agent Skills format. |
| `specs/` | The desired product behavior and observable acceptance criteria. These say what must remain true even when implementation changes. |
| `docs/` | Supporting context loaded when useful: architecture, safety/recovery, build instructions, and this guide. |
| `.github/` | GitHub-specific integration: a short Copilot instruction entry point, CI, and dependency-update configuration. It does not replace the portable contract. |
| `scripts/` | Executable, repeatable checks and artifact handoff, so agents can prove work rather than provide plausible instructions. |
| `app/src/test/` and `app/src/androidTest/` | Machine-executable evidence for rules and UI acceptance, respectively. Tests are not substitutes for the safety specification. |
| `gradle/` | Pinned dependency versions and the checksum-verified Gradle wrapper, keeping the toolchain explicit. |

## Discovery is client-specific

`AGENTS.md` is an open, widely supported convention, not a guarantee that every
agent client reads every file automatically. Agent Skills standardizes the
skill-folder contents; discovery locations and permission handling depend on
the client. This project's root contract explicitly links the two skills.
If a client needs a different skill discovery path, configure it to reference
these files rather than duplicating and drifting the instructions.

The `.github/copilot-instructions.md` file provides the Copilot entry point.
There is intentionally no second root `skills/` directory, invented agent
manifest, unattended autonomous executor, persistent credential, or generated
planning-file pile.

## Suggested agent workflow

Read `AGENTS.md` -> select a relevant skill -> read the affected acceptance
criteria -> implement the smallest change -> run the prescribed checks ->
inspect the actual APK -> report evidence and limits accurately.
Do not confuse repository instructions with permission to publish, alter device
settings, install apps on a real phone, or access unrelated files.

## Format references

- [AGENTS.md open format](https://agents.md/)
- [Agent Skills specification](https://agentskills.io/specification)
- [Android Home role](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_HOME)
- [Android LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
- [Android package visibility](https://developer.android.com/training/package-visibility/declaring)
- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)

Dependency choices are pinned compatible releases, not a claim that every
dependency is the newest release. Upgrade with regression evidence, not merely
because a version number is higher.
