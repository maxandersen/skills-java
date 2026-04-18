# jskills

**The CLI for the open agent skills ecosystem** — Java port of [vercel-labs/skills](https://github.com/vercel-labs/skills).

Run it with zero Node.js/npm required, using just Java 25+ via [JBang](https://jbang.dev).

> **🧪 Experiment:** This project is an experiment in how fast and far you can go using AI coding agents to port a utility from TypeScript to Java — and what you gain from it: managed dependencies via Maven, native binaries via GraalVM, and no Node.js runtime. The entire port was done through agent-assisted coding in a single extended session.

## Quick Start

```bash
# Install the 'skills' command (one-time)
jbang app install skills@maxandersen/jskills

# Now use 'skills' directly
skills find web design
skills add vercel-labs/agent-skills
skills list
```

> Don't have JBang? Install it with `curl -Ls https://sh.jbang.dev | bash -s - app setup`
> or see [jbang.dev/download](https://www.jbang.dev/download/) for other options.

## Usage

```bash
# Search for skills (interactive fzf-style when no query)
skills find
skills find web design

# Add skills from a GitHub repo
skills add vercel-labs/agent-skills

# Add a specific skill
skills add vercel-labs/agent-skills --skill web-design-guidelines

# Add to specific agents
skills add vercel-labs/agent-skills -a claude-code -a cursor

# Non-interactive / CI-friendly
skills add vercel-labs/agent-skills --all -y

# List installed skills
skills list
skills list --json

# Check for updates
skills check

# Update all installed skills
skills update

# Remove a skill
skills remove web-design

# Create a new skill template
skills init my-new-skill
```

## Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `add <source>` | `install`, `a`, `i` | Install skills from GitHub, GitLab, or local path |
| `find [query]` | `search`, `f`, `s` | Search skills at skills.sh |
| `list` | `ls` | List all installed skills |
| `update` | `check` | Update installed skills |
| `remove <skill>` | `rm`, `r` | Remove installed skills |
| `init [name]` | | Create a new SKILL.md template |

## Source Formats

```bash
# GitHub shorthand
skills add owner/repo

# Specific skill within a repo
skills add owner/repo@skill-name

# Full GitHub URL
skills add https://github.com/owner/repo

# Specific branch/path
skills add https://github.com/owner/repo/tree/main/skills/web-design

# GitLab
skills add https://gitlab.com/org/repo

# SSH Git URL
skills add git@github.com:owner/repo.git

# Local path
skills add ./my-local-skills
```

## Supported Agents

All 45 agents from the upstream are supported, including:
Claude Code, Cursor, OpenCode, Cline, Codex, Continue, Gemini CLI, GitHub Copilot, Goose, Kiro, OpenHands, Replit, Roo, Windsurf, and many more.

## Options

| Option | Description |
|--------|-------------|
| `-g, --global` | Install/manage globally (`~/<agent>/skills/`) |
| `-a, --agent <name>` | Target specific agent(s) |
| `--skill <name>` | Install only specific skill(s) by name |
| `--all` | Install/remove all without prompting |
| `-y, --yes` | Skip all confirmation prompts (CI mode) |
| `--copy` | Copy files instead of symlinking |
| `--dry-run` | Preview changes without making them |

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GITHUB_TOKEN` | GitHub token for private repos and higher API rate limits |
| `NO_COLOR` | Disable ANSI colors ([no-color.org](https://no-color.org/)) |
| `DO_NOT_TRACK` | Disable anonymous telemetry |
| `SKILLS_TELEMETRY=0` | Disable telemetry |

## Upstream Sync

This project tracks [vercel-labs/skills](https://github.com/vercel-labs/skills) (currently synced to v1.5.1). A weekly GitHub Action checks for upstream changes and opens an issue when updates are available.

The `upstream.lock` file records the last-synced commit SHA.

## Building from Source

```bash
git clone https://github.com/maxandersen/jskills
cd jskills
mvn package
java -jar target/jskills-*.jar --help
```

Requires Java 25+.

## License

MIT — Same as the original [vercel-labs/skills](https://github.com/vercel-labs/skills/blob/main/LICENSE).
