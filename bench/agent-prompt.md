You are debugging the Android app in the current directory (Gradle project, module `:app`, package `com.khosravi.devin.bench`).

- An Android emulator is connected through `adb`, and the app is installed on it.
- The app's backend runs elsewhere. You can't access or change it; fix the problem in the app.
- Run a user flow on the device with `./repro.sh <browse|search|cart|checkout>`.
- Rebuild and reinstall with `./gradlew :app:installDebug`.

Bug report:

{{BUG_REPORT}}

Find the root cause and fix it in the app code. Don't add or change tests. When you're done, write `ANSWER.md`. Its first line must be `Root cause: <one sentence>`, followed by a short description of your fix.
