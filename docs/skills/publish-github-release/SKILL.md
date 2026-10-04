---
name: publish-github-release
description: Legacy PinShot release entry point; use the project-level pinshot-release skill for packaging, tagging, and publishing this app.
---

# PinShot release entry point

Read and follow the canonical [project-level pinshot-release skill](../../../.agents/skills/pinshot-release/SKILL.md) before packaging, tagging, or publishing. It defines commit approval for pending changes, commit/push, clean builds, and GitHub Release uploads.

Tags now match `versionName` exactly (for example `0.9.0`), without a `v` prefix. Never replace historical tags or releases.
