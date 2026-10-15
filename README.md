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
- Automatic cleanup of old logs, pinned filter tags, and in-app update notifications in the presenter app

## Requirements

minSdk 24.

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

Then install the presenter app on your device/emulator: download the APK from the [latest release](https://github.com/nasserkhosravi/devin-proj/releases/latest).

## Usage

```kotlin
DevinTool.init(context)

val devin = DevinTool.get()            // null when Devin is disabled (e.g. no-op build)
devin?.logger?.info("Login", "User signed in")
devin?.logger?.generalUncaughtExceptionLogging(true)

// HTTP logging (write-okhttp)
val client = OkHttpClient.Builder().apply {
    devin?.okhttpLogger?.getOrCreateInterceptor()?.let { addInterceptor(it) }
}.build()
```

See [SampleActivity](sample-app/src/main/java/com/khosravi/sample/devin/SampleActivity.kt) and [OkHttpSampleActivity](sample-app/src/main/java/com/khosravi/sample/devin/OkHttpSampleActivity.kt) for full integration examples, including presenter config (log password, notification groups).

## Snapshot versions

To resolve snapshot builds, add the Central Portal snapshot repository:

```kotlin
// in dependencyResolutionManagement -> repositories
maven {
    name = "Central Portal Snapshots"
    url = URI("https://central.sonatype.com/repository/maven-snapshots/")

    // Only search this repository for Devin's libraries
    content {
        includeModule("io.github.nasserkhosravi.devin", "write")
        includeModule("io.github.nasserkhosravi.devin", "write-okhttp")
    }
}
```

## Changelogs

[write](devin-write/CHANGELOG.md) · [write-okhttp](devin-write-okhttp/CHANGELOG.md) · [presenter](present-app/CHANGELOG.md)

Maintainers: see [RELEASING.md](RELEASING.md).

## License

```
Copyright 2023 Nasser Khosravi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
