# Releasing

The branch flow (release branches, feature branches, ordering between components) is described in [AGENTS.md](AGENTS.md#release-flow).

Releases are made by GitHub Actions workflows, run manually from the Actions tab **on `main`**. Each one creates a tag `<name>_X.Y.Z` and a GitHub Release whose notes are the `## X.Y.Z` section of the matching `CHANGELOG.md`.

| Workflow | Releases | Version from | Tag |
|---|---|---|---|
| **Release library** (pick `write`, `write-okhttp` or `lib-har`) | `devin-write` + `devin-write-no-op`, `devin-write-okhttp` + `devin-write-okhttp-no-op`, or `lib-har` to Maven Central | `POM_VERSION_NAME` in the modules' `gradle.properties` (a library's modules must match) | `write_X.Y.Z`, `write-okhttp_X.Y.Z`, `lib-har_X.Y.Z` |
| **Release presenter** | Signed `present-app` APK, attached to the GitHub Release (marked "Latest") | `versionName` in `present-app/build.gradle.kts` | `presenter_X.Y.Z` |

1. On the release branch, bump the version and add its `## X.Y.Z` section to the component's `CHANGELOG.md`.
   - Presenter: also increment `versionCode` by exactly 1 over the latest `presenter_*` tag, then run `scripts/check-presenter-release.sh` (the workflow runs it too and refuses to release if it fails).
2. Merge the release branch into `main` via a pull request, then run the workflow on `main`.
3. Merge `main` into every other open release branch.

A `-SNAPSHOT` library version is published to the snapshot repository only, with no tag or GitHub Release. Release `write` before a `write-okhttp` version that depends on it; the workflow checks this.

## Secrets

Settings → Secrets and variables → Actions:

- Libraries: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_KEY`, `SIGNING_KEY_PASSWORD`
- Presenter: `PRESENTER_KEYSTORE_BASE64`, `PRESENTER_KEYSTORE_PASSWORD`, `PRESENTER_KEY_ALIAS`, `PRESENTER_KEY_PASSWORD`

Deployments can be checked at [central.sonatype.com/publishing](https://central.sonatype.com/publishing).
