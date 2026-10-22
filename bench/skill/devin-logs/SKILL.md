---
name: devin-logs
description: Read an Android app's runtime logs, crashes, and full HTTP requests/responses from Devin on a connected device or emulator. Use when debugging an Android app that uses Devin (io.github.nasserkhosravi.devin) — e.g. a bug report, a crash you can't reproduce, wrong data from the backend — before guessing from code.
---

# Devin logs

Apps that embed Devin write their logs into the Devin presenter app on the device. Devin keeps:
- every app log (tag, message, level), across app restarts and crashes, after logcat has rolled over;
- uncaught crashes with stack traces;
- every OkHttp call as HAR, **including request and response bodies** (logcat usually only has the request line).

Read it with `adb shell content query` against `content://com.khosravi.devin.provider`. No app changes are needed.

## Rules

- Quote the whole command as **one string** passed to `adb shell`. Otherwise the device shell treats `&` in the URI as "run in background" and drops parameters.
- Always add `isRawQuery=true`, a `--where` on `_clientId`, a sort, and `itemCount=N` to keep output small.
- Use the `json_object(...) AS r` projections below. They return one JSON value per row.
- Always end the projection with an alias (`AS r`). Without it, any expression containing `.` (like `'$.url'`) prints as `NULL`.
- Never put `:` in `--projection`; `content` splits on it.

Columns: `_id`, `_clientId` (app package), `_tag`, `_value` (message), `_date` (epoch ms), `_typeId` (`NULL` for app logs, `dvn_okhttp` for HTTP, `exception` for crashes), `_meta` (JSON: `log_level` 2=V 3=D 4=I 5=W 6=E, plus `throwable`, `payload`, `HAR`).

## Recipes

Set `APP=<applicationId>` first.

**Which apps log to Devin:**
```bash
adb shell "content query --uri 'content://com.khosravi.devin.provider/client'"
```

**Latest 50 logs, newest first** (app logs, HTTP summaries and crashes together):
```bash
adb shell "content query --uri 'content://com.khosravi.devin.provider/log?isRawQuery=true&itemCount=50' --projection \"json_object('id',_id,'date',_date,'time',time(_date/1000,'unixepoch','localtime'),'tag',_tag,'lvl',json_extract(_meta,'\$.log_level'),'msg',substr(_value,1,200),'type',_typeId) AS r\" --where \"_clientId='$APP'\" --sort '_date DESC'"
```
Each session starts with a `SessionStart` log, so you can see where one app run ends and the next begins.

**Narrow down:** extend `--where`, e.g.
- errors and warnings only: `... AND json_extract(_meta,'\$.log_level') >= 5`
- one tag: `... AND _tag='Checkout'`
- text search: `... AND _value LIKE '%order%'`
- the last 10 minutes: `... AND _date > (strftime('%s','now') - 600) * 1000`

**Crashes, with stack traces:**
```bash
adb shell "content query --uri 'content://com.khosravi.devin.provider/log?isRawQuery=true&itemCount=5' --projection \"json_object('id',_id,'date',_date,'trace',substr(json_extract(_meta,'\$.throwable'),1,3000)) AS r\" --where \"_clientId='$APP' AND _typeId='exception'\" --sort '_date DESC'"
```
Then read the logs just before the crash: `--where "_clientId='$APP' AND _date <= <date>"` with `--sort '_date DESC'`.

**HTTP calls (summary):** `_value` is like `200 POST /checkout`. A failed call starts with `!!!!!!` and has `error_summery` in its meta.
```bash
adb shell "content query --uri 'content://com.khosravi.devin.provider/log?isRawQuery=true&itemCount=20' --projection \"json_object('id',_id,'call',_value,'url',json_extract(_meta,'\$.url'),'ms',json_extract(_meta,'\$.HAR.log.entries[0].time')) AS r\" --where \"_clientId='$APP' AND _typeId='dvn_okhttp'\" --sort '_date DESC'"
```

**One HTTP call in full** (request and response, bodies cut to 4000 chars):
```bash
adb shell "content query --uri 'content://com.khosravi.devin.provider/log?isRawQuery=true' --projection \"json_object('request',json_extract(_meta,'\$.HAR.log.entries[0].request.postData.text'),'status',json_extract(_meta,'\$.HAR.log.entries[0].response.status'),'response',substr(json_extract(_meta,'\$.HAR.log.entries[0].response.content.text'),1,4000),'error',json_extract(_meta,'\$.error_summery')) AS r\" --where \"_id=<id>\""
```
Request and response headers are at `$.HAR.log.entries[0].request.headers` and `$.HAR.log.entries[0].response.headers`.

**Start a clean reproduction:** delete this app's Devin logs, run the flow again, then query.
```bash
adb shell "content delete --uri 'content://com.khosravi.devin.provider/log?_clientId=$APP'"
```
