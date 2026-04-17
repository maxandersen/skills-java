# Upstream Sync Plan: vercel-labs/skills → jskills

**Date:** 2026-04-17  
**Upstream range:** v1.4.5 (c9fb03ec, 2026-03-13) → v1.5.1 (bc21a37a, 2026-04-17)  
**Commits to port:** ~20 meaningful changes across 4 minor versions

---

## Priority 1: Bug Fixes (Low effort, high value)

### Task 1: Sort search results by install count (#546)
- **File:** `FindCommand.java` (maps to `src/find.ts`)
- **Change:** After fetching search results, sort by `installs` descending
- **Effort:** Trivial — add `.sort()` after mapping results

### Task 2: Stop excluding underscore-prefixed files (#548)
- **File:** `providers/SkillDiscovery.java` or installer equivalent
- **Change:** Remove `name.startsWith("_")` exclusion, add `.startsWith(".")` instead. Add `__pycache__` and `__pypackages__` to excluded dirs
- **Effort:** Small

### Task 3: Skip broken symlinks during installation (#583)
- **File:** Installer logic (wherever `copyDirectory` equivalent lives)
- **Change:** Wrap file copy in try/catch, log warning on broken symlink, continue
- **Effort:** Small

### Task 4: Preserve SSH source URLs in lock files (#588)
- **File:** `commands/AddCommand.java`
- **Change:** When source URL starts with `git@`, store the SSH URL in lock instead of normalizing to `owner/repo`
- **Effort:** Small

### Task 5: Avoid hardcoded `~/.agents` for skill lock file (#517)
- **File:** `lock/SkillLock.java`
- **Change:** Check `XDG_STATE_HOME` env var first; use `dirname()` instead of hardcoded path when creating parent dir
- **Effort:** Small

### Task 6: List skills for undetected agents (#656)
- **File:** Installer/list logic
- **Change:** Also scan skill directories for agents not in `agentsToCheck` (agents that were used for install but are no longer detected)
- **Effort:** Medium

---

## Priority 2: New Features (Medium effort)

### Task 7: Support branch refs in skill sources (#814) ⭐
- **Files:** `SourceParser.java` (new parsing logic), `AddCommand.java`, `lock/SkillLock.java`, `lock/LocalLock.java`, `commands/UpdateCommand.java`
- **Changes:**
  - Parse `#ref` fragment from source URLs (new `extractFragmentRef()` logic)
  - Distinguish `#ref` (branch/tag) from `#skill-filter` based on whether source looks like a git URL
  - Add `ref` field to `SkillLockEntry` and `LocalSkillLockEntry`
  - Pass `ref` to `fetchSkillFolderHash()`
  - New `UpdateSource.java` utility class with `formatSourceInput()` and `buildUpdateInstallSource()`
- **Effort:** Large — touches many files, needs new URL fragment parsing logic
- **Tests:** Update `source-parser` fixtures, add `update-source` tests

### Task 8: Support `/.well-known/agent-skills/` path (#support-agent-skills-path)
- **File:** `providers/WellKnownProvider.java`
- **Change:** Try `/.well-known/agent-skills/index.json` first, fall back to `/.well-known/skills/index.json`
- **Effort:** Small

### Task 9: Skip symlink/copy prompt when all agents share one directory (#582)
- **File:** `commands/AddCommand.java` (well-known skill handling)
- **Change:** Compute unique target dirs; skip install mode prompt if only 1 unique dir
- **Effort:** Small

### Task 10: Indicate which skills failed to update (#387)
- **File:** `commands/CheckCommand.java` / `commands/UpdateCommand.java`
- **Change:** Track and display failed skill names in update output
- **Effort:** Medium

---

## Priority 3: Major Features (High effort)

### Task 11: Direct download from snapshot / blob install (#853) ⭐⭐
- **New file:** `blob/BlobDownloader.java` (maps to `src/blob.ts` — 433 lines)
- **New file:** `installer/BlobInstaller.java` (maps to new `installBlobSkillForAgent`)
- **Changes to:** `commands/AddCommand.java` — try blob download before git clone
- **Flow:**
  1. GitHub Trees API → discover SKILL.md locations
  2. `raw.githubusercontent.com` → fetch frontmatter for skill names
  3. `skills.sh/api/download` → fetch full file contents from cached snapshot
- **Effort:** Very large — 433+ lines of new code, HTTP client work, tree parsing
- **Benefit:** Massive performance improvement (no full git clone)

### Task 12: Revamped update command — project/global/single skill (#913) ⭐⭐
- **File:** `commands/UpdateCommand.java` (maps to major `cli.ts` rewrite, +386/-159)
- **Changes:**
  - Remove separate `check` command (merge into `update`)
  - Support `update [skill-name...]` for single skill updates
  - Add `--global` / `--project` / `--yes` flags for scope selection
  - Project-level updates via local lock file
  - New `buildLocalUpdateSource()` in `UpdateSource`
- **Effort:** Very large — significant command restructuring

### Task 13: Warn on openclaw sources (#865)
- **File:** `commands/AddCommand.java`
- **Change:** Block `openclaw/*` sources unless `--dangerously-accept-openclaw-risks` flag is passed. Show warning about duplicate/malicious skills
- **Effort:** Medium

---

## Priority 4: Small Updates (Low effort)

### Task 14: Skip LFS smudge during git clone (#952)
- **File:** `util/GitUtils.java`
- **Change:** Set `GIT_LFS_SKIP_SMUDGE=1` env var when spawning git clone
- **Effort:** Trivial

### Task 15: New agents — Warp, Deep Agents, Firebender, IBM Bob (#516, #478, #372, #335)
- **File:** `agents/AgentRegistry.java`, `model/AgentConfig.java`
- **Change:** Add 4 new agent configs with their paths and display names
- **Effort:** Small — data-only changes

### Task 16: Update Antigravity installation path (#667)
- **File:** `agents/AgentRegistry.java`
- **Change:** Update Antigravity agent's skills directory path
- **Effort:** Trivial

### Task 17: Remove gray-matter, use simpler frontmatter parsing (#839)
- **File:** Already done — Java port uses `FrontmatterParser.java` with simple regex
- **Effort:** None (already aligned)

---

## Suggested Execution Order

**Phase A — Quick wins (Tasks 1-5, 8, 14-17):** ✅ DONE
Sorted search results, XDG lock path, agent-skills well-known path, LFS skip note, new agents.

**Phase B — Medium features (Tasks 6, 9, 10, 13):** ✅ DONE  
Undetected agent listing, failed update reporting, openclaw warning. Task 9 N/A (no interactive prompt in Java port yet).

**Phase C — Branch refs support (Task 7):** ✅ DONE  
New SourceParser, ParsedSource, UpdateSource classes. Ref support in lock entries, providers, add/update/check commands. 23 new tests (114 total).

**Phase D — Blob download (Task 11):** ✅ DONE  
New BlobDownloader with GitHub Trees API, raw content fetch, skills.sh download API. Integrated into AddCommand with fallback to git clone. 15 new tests (129 total).

**Phase E — Update command rework (Task 12):** ✅ DONE  
Revamped UpdateCommand with project/global/both scopes, single skill updates, interactive scope prompt. CheckCommand kept as backward-compat alias. 129 total tests.

---

## Sync Complete ✅

All 5 phases done. 129 tests passing.

Update `upstream.lock`:
```json
{
  "lastSyncedSha": "bc21a37a12...",
  "lastChecked": "2026-04-17T..."
}
```
