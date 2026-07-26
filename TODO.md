# TODO

## FINDROID-7: Dependency currency (Renovate/Dependabot)

- [ ] Review upstream Findroid's dependency updates since this fork diverged and
      selectively pull in the ones that still make sense (don't blindly merge -
      this fork has diverged substantially from upstream in places)
- [x] Enable Renovate or Dependabot on this repo so dependency versions stay
      current going forward without manual tracking. First tried Dependabot
      (2026-07-18) since it needs no GitHub App install, but it produced zero
      PRs in 6 days despite `.github/dependabot.yml` being live and a scheduled
      Monday run passing — switched to Renovate instead (`renovate.json`
      restored to its pre-2026-07-18 content: `config:recommended` +
      `schedule:weekly` + `:semanticCommits`, kotlin/ksp grouped, `dependencies`
      label; validated with `renovate-config-validator`). Removed
      `.github/dependabot.yml` to avoid duplicate/conflicting automation.
      **Manual follow-up required**: install the Renovate GitHub App
      (https://github.com/apps/renovate) on `pschmitt/findroidplus` specifically
      — this is the one step that can't be done from a commit, and is exactly
      why the old inherited `renovate.json` was never actually active despite
      looking configured. Done (2026-07-24, set to "All repositories"). Even
      with the app installed, Renovate still produced nothing at first - this
      repo is a real GitHub fork (`fork: true`, parent
      `jarnedemeulemeester/findroid`), and Renovate disables itself on forks by
      default to avoid spamming them with irrelevant PRs. Added
      `"forkProcessing": "enabled"` to `renovate.json` to override that.

Status: in progress (2026-07-18) - automation enabled; the manual "review and
selectively pull in upstream dependency updates" item is still open and requires
human judgment.
