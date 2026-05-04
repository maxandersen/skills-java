# Sync notes

## What I did
- Compared the upstream range `bc21a37a12...main` for `vercel-labs/skills` against this Java port.
- Verified the upstream `main` tip is only a version bump commit (`7c0a9af3f8738965b71341712710ac7371089b34`), so the real work was the diff between the old sync point and that tip.
- Ported the missing/high-value upstream changes into the Java codebase:
  - added terminal-metadata sanitization for untrusted skill names/descriptions in CLI output
  - added the 8 newer agent entries from upstream
  - added `VIBE_HOME` support for Mistral Vibe
  - added configurable clone timeout via `SKILLS_CLONE_TIMEOUT_MS`
  - fixed project update scoping so `skills update <name>` keeps the install scoped to a single skill when `skillPath` is available
- Updated tests for the new agent list and update-source behavior.
- Updated `upstream.lock` to the new upstream SHA.

## What I did not port
- Node/Javascript-specific pieces that do not map cleanly to this Java/JGit port:
  - `git-lfs` clone filter workaround from upstream `git.ts`
  - telemetry flush lifecycle changes from upstream `telemetry.ts`
- I also did not port every small cosmetic/documentation-only upstream change.

## Verified
- `mvn test` passes.
- The upstream compare range was inspected directly from GitHub.
- The Java code now includes the missing agent additions and the main safety/update fixes from that range.

## Still needs doing
- If you want perfect parity with upstream, the remaining work is a second pass over any newer upstream commits after `7c0a9af3f8`.
- Decide whether to mirror the Node telemetry lifecycle more closely in the Java CLI or leave it as-is.
- Decide whether to add any extra regression tests for the new sanitization behavior beyond the basic unit coverage.
