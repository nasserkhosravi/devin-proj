#!/usr/bin/env bash
# Reads what an Android app logged to Devin on a connected device. Prints one JSON object per line.
#
# Usage: devin.sh [-s <serial>] [-a <applicationId>] <command> [args]
#   apps                      apps that log to Devin
#   timeline [n]              latest session in order (default 80 entries)
#   tags                      each tag: count and last message
#   search <keyword> [n]      logs and request URLs containing <keyword>
#   http [path] [n]           HTTP calls, newest first (optionally only URLs containing <path>)
#   call <id>                 one HTTP call: request, status, response (bodies cut to 4000 chars), and notes
#                             when the stored body was cut or was binary
#   field <id> <json-path>    one field of a response body, e.g. '$.data.cards[0].visible'
#   headers <id>              header names a request sent (never values)
#   crash [n]                 latest crash with stack trace, then the n entries before it (default 30)
#   errors [n]                warnings, errors and logged exceptions, newest first
#   clear                     delete this app's Devin logs (only right before reproducing)
#
# The app comes from -a or $DEVIN_APP. Sensitive JSON values (tokens, passwords, phone numbers...) are masked.
set -euo pipefail

ADB=(adb)
APP="${DEVIN_APP:-}"
while getopts "s:a:" opt; do
  case "$opt" in
    s) ADB=(adb -s "$OPTARG") ;;
    a) APP="$OPTARG" ;;
    *) sed -n '2,19p' "$0" >&2; exit 2 ;;
  esac
done
shift $((OPTIND - 1))
cmd="${1:-}"; shift || true

URI="content://com.khosravi.devin.provider"
LATEST="_id >= (SELECT max(_id) FROM log WHERE _clientId='APP' AND _tag='SessionStart')"
ROW="'id',_id,'time',time(_date/1000,'unixepoch','localtime'),'tag',_tag,'lvl',json_extract(_meta,'\$.log_level'),'msg',substr(_value,1,160),'type',_typeId,'fields',json_extract(_meta,'\$.fields')"
HAR="json_extract(_meta,'\$.HAR.log.entries[0]"

die() { echo "devin.sh: $*" >&2; exit 1; }

# Escapes a string for use inside double quotes on the device shell.
esc() { printf '%s' "$1" | sed 's/[\\"$`]/\\&/g'; }

# Masks values of sensitive JSON keys in the output.
mask() { perl -pe 's/("(?:[\w-]*token|password|passwd|secret|authorization|cookie|phone|mobile|msisdn|email|national_?id|otp|pin)"\s*:\s*)("[^"]*"|\d+)/$1"***"/gi'; }

run() {
  local out
  out="$("${ADB[@]}" shell "$1" 2>&1)" || true
  case "$out" in
    *"no devices"*|*"device offline"*|*"device unauthorized"*) die "no device on adb (use -s <serial> when several are connected)" ;;
    "adb: device"*"not found"*) die "$out" ;;
    *"more than one device"*) die "several devices connected; pass -s <serial>" ;;
    *"Could not find provider"*|*"Unknown URL"*) die "Devin presenter app is not installed on this device" ;;
    "Error while accessing provider"*) printf '%s\n' "$out" | head -3 >&2; die "query failed" ;;
    "No result found.") return 0 ;;
  esac
  printf '%s\n' "$out" | sed -E 's/^Row: [0-9]+ (r=)?//' | mask
}

# q <projection> <where> <sort> [n]
q() {
  local uri="$URI/log?isRawQuery=true"
  [ -n "${4:-}" ] && uri="$uri&itemCount=$4"
  run "content query --uri '$uri' --projection \"$(esc "$1") AS r\" --where \"$(esc "$2")\" --sort '$3'"
}

need_app() {
  [ -n "$APP" ] && return 0
  echo "devin.sh: no app chosen; pass -a <applicationId> or set DEVIN_APP. Apps on this device:" >&2
  "$0" apps >&2; exit 1
}

app_where() { printf "_clientId='%s'" "$APP"; }

# Fails with the list of apps when $APP has no logs at all.
check_app() {
  [ -n "$(q "_id" "$(app_where)" "_id" 1)" ] && return 0
  echo "devin.sh: no Devin logs for '$APP'. Apps on this device:" >&2
  "$0" "${ADB[@]:1}" apps >&2; exit 1
}
latest() { printf '%s' "${LATEST//APP/$APP}"; }
num() { case "${1:-}" in ''|*[!0-9]*) die "expected a number, got '${1:-}'" ;; esac; }

case "$cmd" in
  apps)
    # The provider returns every column; keep only the id (the config can hold the presenter password).
    run "content query --uri '$URI/client'" | sed -E 's/^_clientId=//; s/, _presenterConfig=.*$//' ;;
  timeline)
    need_app; check_app; n="${1:-80}"; num "$n"
    q "json_object($ROW)" "$(app_where) AND $(latest)" "_id ASC" "$n" ;;
  tags)
    need_app; check_app
    q "json_object('tag',_tag,'n',(SELECT count(*) FROM log c WHERE c._clientId=log._clientId AND c._tag=log._tag),'last',substr(_value,1,120))" \
      "_id IN (SELECT max(_id) FROM log WHERE $(app_where) GROUP BY _tag)" "_id DESC" 50 ;;
  search)
    need_app; check_app; kw="${1:?usage: devin.sh search <keyword> [n]}"; n="${2:-20}"; num "$n"
    kw="${kw//\'/\'\'}"
    q "json_object($ROW)" "$(app_where) AND (_value LIKE '%$kw%' OR json_extract(_meta,'\$.url') LIKE '%$kw%')" "_id DESC" "$n" ;;
  http)
    need_app; check_app; path="${1:-}"; n="${2:-20}"; num "$n"; path="${path//\'/\'\'}"
    q "json_object('id',_id,'time',time(_date/1000,'unixepoch','localtime'),'call',_value,'url',json_extract(_meta,'\$.url'),'ms',$HAR.time'))" \
      "$(app_where) AND _typeId='dvn_okhttp' AND json_extract(_meta,'\$.url') LIKE '%$path%'" "_id DESC" "$n" ;;
  call)
    id="${1:?usage: devin.sh call <id>}"; num "$id"
    q "json_object('url',json_extract(_meta,'\$.url'),'request',substr($HAR.request.postData.text'),1,4000),'requestNote',$HAR.request.postData.comment'),'status',$HAR.response.status'),'response',substr($HAR.response.content.text'),1,4000),'responseNote',$HAR.response.content.comment'),'error',json_extract(_meta,'\$.error_summery'))" \
      "_id=$id" "_id" ;;
  field)
    id="${1:?usage: devin.sh field <id> <json-path>}"; path="${2:?usage: devin.sh field <id> <json-path>}"; num "$id"
    path="${path//\'/\'\'}"
    q "json_extract($HAR.response.content.text'),'$path')" "_id=$id" "_id" ;;
  headers)
    id="${1:?usage: devin.sh headers <id>}"; num "$id"
    q "json_object('request',(SELECT group_concat(json_extract(h.value,'\$.name'),',') FROM json_each($HAR.request.headers')) h),'response',(SELECT group_concat(json_extract(h.value,'\$.name'),',') FROM json_each($HAR.response.headers')) h))" \
      "_id=$id" "_id" ;;
  crash)
    need_app; check_app; n="${1:-30}"; num "$n"
    crash="$(q "json_object('id',_id,'time',datetime(_date/1000,'unixepoch','localtime'),'trace',substr(json_extract(_meta,'\$.throwable'),1,3000))" "$(app_where) AND _typeId='exception'" "_id DESC" 1)"
    [ -z "$crash" ] && { echo "no crash recorded for $APP"; exit 0; }
    echo "$crash"
    id="$(printf '%s' "$crash" | sed -E 's/^\{"id":([0-9]+).*/\1/')"
    echo "--- the $n entries before it, newest first"
    q "json_object($ROW)" "$(app_where) AND _id < $id" "_id DESC" "$n" ;;
  errors)
    need_app; check_app; n="${1:-30}"; num "$n"
    q "json_object($ROW,'throwable',substr(json_extract(_meta,'\$.throwable'),1,600))" \
      "$(app_where) AND (json_extract(_meta,'\$.log_level') >= 5 OR json_extract(_meta,'\$.throwable') IS NOT NULL)" "_id DESC" "$n" ;;
  clear)
    need_app
    run "content delete --uri '$URI/log?_clientId=$APP'" > /dev/null
    echo "cleared Devin logs for $APP" ;;
  ''|-h|--help|help)
    sed -n '2,19p' "$0" ;;
  *)
    die "unknown command '$cmd' (see devin.sh help)" ;;
esac
