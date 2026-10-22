# Changelog
**Write module:**

## 4.5.0
- Fix ANRs when logging from the main thread. Logs are now queued and sent to the presenter in batches off the caller's thread, so a log call no longer blocks on IPC or on starting the presenter process.
- If the queue fills up (2,000 waiting logs), new logs are dropped and a `devin_dropped_logs` warning records how many.
- Before writing an uncaught-exception log, wait up to 500 ms for queued logs, so the logs leading up to a crash are kept.
- Built with Kotlin 2.1.20 (was 1.9.0). Apps using Devin need Kotlin 2.0 or newer.
- Adds `kotlinx-coroutines-android` 1.7.3 as a dependency.

## 4.4.1
- Fix `generalUncaughtExceptionLogging(false)` clearing the app's own uncaught-exception handler when Devin's handler was never enabled.
- Fix `generalUncaughtExceptionLogging(true)` called twice logging every crash twice.
- Fix provider meta search: it failed without a selection, `StartWith`/`EqualTo` behaved as `Contain`, and search values are now bound as SQL arguments.
- Fix deleting logs without a client id crashing the provider.
- Fix log content files being written under log id `0` (and failing when the client directory didn't exist).

## 4.4.0
- Add `DevinTool.isProviderInstalled()` to check whether a Devin presenter app was found during `init` (`null` when Devin is disabled; always `null` in no-op).

## 4.3.1
- Bump compileSdk/targetSdk to 35, lower minSdk to 24.

## 4.3.0
- Add PresenterConfigBuilder to ease creating standard devin presenter supported config.

## 4.2.0
- Change sessionStart to customize message and payload.
- Add verbose log function.
- Add send log function.

## 4.1.0
- Update gradle to 8.11.1, and agp to 8.10.0.
- Add presenterConfig feature through write module to presenter.
- Add special sessionStart function to logger.

## 4.0.0
- DevinTool now accept a boolean to config being enable.
- Add metaIndex table to presenter be able to query something in meta.
- Break-change, DevinTool getOrCreate function changed to init() and get().
- Add type id column to separate tags and types.
- Add connectPlugin function to able extend functionality of DevinTool, see write-okhttp module

## 3.1.1
- Fix devin content provider visibility access that may cause some problem in android 30 and above.   

## 3.1.0
- Make DevinTool as singleton instance.
- Disable devin-api module.

## 3.0.0
- Implement dedicate presenter and image logger. 
- Implement general exception logging.
- Create devin-api module to share codes between operation and no-operation module.

## 2.0.0
- Support multi app client (Content provider moved to presenter)

## 1.1.0
- Support different log level, payload and throwable when log.
- Add no-op version.
- Move devin content provider declaring to write module.
- Remove log function by proguard in release.
- Add snapshot repository.

## 1.0.1 
- Initialize devin write module project.