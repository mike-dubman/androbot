# Release

## Manual release workflow (recommended)

Trigger the GitHub Actions release pipeline manually:

```bash
VERSION=0.2.0 make release-cli
```

Optional inputs:

```bash
VERSION=0.2.0 RELEASE_PUBLISH=published make release-cli
VERSION=0.2.0 RELEASE_PUBLISH=draft RELEASE_NOTES="Hotfix build" make release-cli
```

Workflow behavior:

- builds debug and release APK
- creates/updates tag `v<version>`
- creates GitHub release (draft or published)
- attaches APK assets
- writes release body with changelog and direct download links

## Legacy local release script

Create and publish a GitHub Release (tag + generated notes + debug APK asset):

```bash
VERSION=0.2.0 make release
```

Requirements:

- Clean git working tree
- `gh auth login` completed
- Push access to `origin`
