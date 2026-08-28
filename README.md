# Devin

[![Maven Central](https://img.shields.io/maven-central/v/io.github.nasserkhosravi.devin/write.svg)](https://search.maven.org/artifact/io.github.nasserkhosravi.devin/write)

Devin is an on-device logging framework for Android. Apps embed a lightweight **write** library to record logs (text, images, HTTP traffic), and a standalone **presenter app** reads and displays those logs on the same device — no server, no cable, no log-scraping. Goal: make QA reports easier to produce and give QA/devs visibility into what the app is actually doing.

## Modules

| Module | Published as | Purpose |
|---|---|---|
| `devin` | `io.github.nasserkhosravi.devin:write` | Core write library: logging API, Room-backed storage, ContentProvider bridge to the presenter app. |
| `devin-no-op` | `io.github.nasserkhosravi.devin:write-no-op` | Same API, empty implementations. Use in release builds for zero overhead. |
| `devin-write-okhttp` | `io.github.nasserkhosravi.devin:write-okhttp` | OkHttp interceptor that captures request/response traffic as HAR-formatted logs. |
| `devin-write-okhttp-no-op` | `io.github.nasserkhosravi.devin:write-okhttp-no-op` | No-op counterpart for release builds. |
| `present-app` | — (standalone app) | Viewer app: lists connected client apps, browses/filters/searches logs, shows HTTP detail (share as cURL/HAR), notifications, export to JSON/ZIP. |
| `sample-app` | — (sample) | Reference integration showing write + OkHttp usage. |

## Features

- Text logs with levels (debug/info/warning/error/verbose), tags, payload, and throwable capture
- Multiple client apps can log to a single presenter app (per-app registration)
- Log filtering, search, and export (JSON or per-tag ZIP) in the presenter app
- HTTP logging via OkHttp interceptor, HAR 1.2 format, header redaction, pluggable body decoders
- Automatic uncaught-exception logging
- Debug/release split via no-op modules — no logging code shipped in release builds
- Optional per-client password protection and per-tag notification config
- Image load logging (downloading/succeeded/failed)

## Install

Add the write library, and its no-op counterpart for release builds:

```groovy
dependencies {
    debugImplementation "io.github.nasserkhosravi.devin:write:$VERSION"
    releaseImplementation "io.github.nasserkhosravi.devin:write-no-op:$VERSION"
}
```

For HTTP logging via OkHttp, add the same debug/release split:

```groovy
dependencies {
    debugImplementation "io.github.nasserkhosravi.devin:write-okhttp:$VERSION"
    releaseImplementation "io.github.nasserkhosravi.devin:write-okhttp-no-op:$VERSION"
}
```

Then install the [present-app](present-app) on your device/emulator to view logs from any client app that has the write library.

See [SampleActivity](https://github.com/nasserkhosravi/devin-proj/blob/main/sample-app/src/main/java/ir/khosravi/sample/devin/SampleActivity.kt) for a full integration example.

## Snapshot versions

To resolve snapshot builds, add the Central Portal snapshot repository:

```kotlin
// in dependencyResolutionManagement -> repositories
maven {
    name = "Central Portal Snapshots"
    url = URI("https://central.sonatype.com/repository/maven-snapshots/")

    // Only search this repository for the specific dependency
    content {
        includeModule("io.github.nasserkhosravi.devin", "write")
    }
}
```

## Publishing (maintainers)

1. Bump `POM_VERSION_NAME` in the module's `gradle.properties` (keep the `-SNAPSHOT` suffix for a snapshot release).
2. Run, per module:
   - `$module$ -> build -> assemble`
   - `$module$ -> publishing -> publishToMavenCentral`
3. Verify/manage the deployment at [central.sonatype.com/publishing](https://central.sonatype.com/publishing).
