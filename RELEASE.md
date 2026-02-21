# Release

Create and publish a GitHub Release (tag + release notes + debug APK asset):

```bash
VERSION=0.2.0 make release
```

Requirements:

- Clean git working tree
- `gh auth login` completed
- Push access to `origin`
