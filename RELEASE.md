# Release

## Manual release workflow (recommended)

Trigger the GitHub Actions release pipeline manually:

```bash
VERSION=0.2.0 make release-cli  # defaults to published
```

Optional inputs:

```bash
VERSION=0.2.0 RELEASE_PUBLISH=draft make release-cli
VERSION=0.2.0 RELEASE_PUBLISH=draft RELEASE_NOTES="Hotfix build" make release-cli
```

Note: APK files are published under GitHub Releases assets. They do not appear under the GitHub "Packages" tab unless you publish a package format (for example a container image or Maven artifact).

Workflow behavior:

- builds debug and release APK
- creates/updates tag `v<version>`
- creates GitHub release (published by default, draft optional)
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
