# Contributing

Thanks for wanting to contribute changes back to Draconis Fleet! This covers the git/PR mechanics only.

## Branch target

Open PRs against **`beta`**, not `main`. `main` only ever receives tagged releases merged in from `beta` - a PR opened against `main` will be asked to retarget.

## Commits

- Keep commits small and focused - one logical change per commit (e.g. one balance tweak, one bug fix, one feature).
- Write a commit message that says *why*, not just what changed.
- Don't bundle unrelated changes into one commit.

## What to avoid touching

- `mod_info.json`, `draconis.version`, `changelog.txt` - these are updated as part of the release process, not per-contribution. If your change needs a changelog entry, mention it in the PR description and it'll be added at release time.
- Anything clearly unrelated to the change you're making.

## Submitting

1. Fork the repo, branch off `beta`.
2. Make your change(s) as small, focused commits.
3. Open a PR back into `beta`, with a short description of what the change does and why.
4. Everything is hand-reviewed and merged manually - expect edits, follow-up questions, or changes before/instead of a merge.
