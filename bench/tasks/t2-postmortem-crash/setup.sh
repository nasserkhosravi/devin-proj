# "Yesterday": the backend sent a product without a price and the app crashed.
start_server null-price
run_scenario browse
# "Today": the backend is fixed, the app was restarted many times and logcat has rolled over.
start_server default
adb shell am force-stop "$APP_ID"
adb logcat -c
