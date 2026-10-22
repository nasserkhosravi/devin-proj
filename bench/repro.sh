#!/usr/bin/env bash
# Runs one user flow of the app on the connected device: ./repro.sh <browse|search|cart|checkout>
set -euo pipefail
scenario="${1:?usage: ./repro.sh <browse|search|cart|checkout>}"
adb shell am force-stop com.khosravi.devin.bench
adb shell am start -W -n com.khosravi.devin.bench/.MainActivity --es scenario "$scenario" > /dev/null
sleep 5
echo "Ran scenario '$scenario'."
