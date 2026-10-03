# Changelog

## 4.5.0
- Notify when a newer presenter release is published on GitHub (checked at most once a day; a notification plus a one-time dialog on the client selection screen; footer shows the available version). A major version bump (e.g. 4.x → 5.0.0) blocks the app with an "update required" dialog on the client selection screen (back closes the app). The footer version turns red while an update is available.
- Replace log item detail dialog with a dedicated full page, with a copy button.
- Show millisecond precision on log list item time.
- Allow HTTP log item path to wrap to two lines.
- Show app version and contributor name in StarterActivity footer; tapping it opens the GitHub releases page.
- Fix copy icon being invisible in dark theme.

## 4.4.0
- Implement session separator feature.
- Implement notification-log feature: notify per client+group with debounce, configurable per-group notification color and channel, wildcard tag support, and whitelist for allowed tags.
- Add copy-body and copy-as-cURL actions to HTTP request/response detail tabs.
- Add horizontal padding to HTTP log detail menu icon.
- Migrate to gradle version catalog.
- Highlight okhttp tag filter chip with a distinct color and "http" label.

## 4.3.1
- Change password field to textPassword.
- Fix crash on clearing logs in search.

## 4.3.0
- Redesign application, light and dark theme.
- Implement contextual menu for quick action on filter tags.
- Implement pining feature for tag and custom filter in LogActivity.
- Implement quick share feature for tag filter in LogActivity.
- Implement removing a custom filter from contextual menu in LogActivity.

## 4.2.0
- Implement client login (log password) feature.

## 4.1.0
- Improve exporting by create a dedicated dialog.
- Add default and custom export options.
- Improve client list UI UX by having refresh and changing UI.
- Fix StarterActivity crash when user back from LogActivity.

## 4.0.0
- Support ok-http special logs.
- Add search and pagination feature.
- Create a dedicated exporter dialog.
- All query on DB should be done on main thread.
- Able presenter module to run custom queries.

## 3.0.0
- Present image logs from image logger.
- Disable log counting feature.

## 2.0.0
- Support multi app client (Content provider moved to presenter).
- Add copy export in clipboard to ease sharing.

## 1.1.0
- Support filter by tag.
- Create header log date which show date in persian.
- Add count replicated logs feature.
- Colorize filters.
- Support import exporting logs.
- Separate clearing logs and filters.
- Change share text format.
- Support different log level, payload and throwable when log in presentation.
- Fix some bug.

## 1.0.1
- Initialize presenter application. 