# HttpLogDetailActivity → Jetpack Compose migration — design

Date: 2026-10-16
Branch: `compose-migration` (one commit per screen)

## Context

`present/http/HttpLogDetailActivity.kt` (XML `activity_http_log_detail.xml`: `TabLayout` + overflow icon
`iv_ic_menu` + `ViewPager2` whose pages are FastAdapter items) shows one HTTP log, opened from
`LogActivity` via `HttpLogDetailActivity.startActivity(context, logId)` (KEY_LOG_ID extra).

- Data: `HttpDetailViewModel.fetchHttpLogDetail(context, logId)` → `detailData: StateFlow<HttpLogDetailData?>`.
- 3 tabs: **Overview** (`HttpDetailOverviewItemView`: url, method, protocol, status, response summary, SSL
  yes/no, optional TLS version + cipher suite rows, request/response time, duration, request/response/total
  size — read `item_http_detail_overview.xml` for labels/layout), **Request** and **Response**
  (`HttpDetailContentItemView`: headers (`HttpFormatUtils.formatHeaders`, a Spanned) as one item; if body
  is empty → "body empty" line and no search; else a copy-actions row (copy formatted body / copy cURL)
  then the body (`HttpFormatUtils.spanBody` → JSON-coloured Spanned) split into lines, one list item per
  line; a `SearchView` with up/down buttons and an "i / n" counter: query longer than 1 char highlights all
  occurrences (bg yellow / fg red), the current one green, scrolls to it (600 ms after typing), up/down cycle).
- Overflow popup menu (`http_detail_menu.xml`, 7 items): share HAR as JSON file, share as cURL, copy HAR,
  copy as cURL, copy response content, copy request content (`msg_response_null` toast when null),
  save HAR as JSON (SAF create-document via `registerForActivityResult`, then
  `createFlowForExportFileIntentResult`).
- Activity uses `CoroutineScope by MainScope()` (never cancelled).

## Decisions (autonomous, per the user's "migrate all screens" instruction)

1. **Stay a standalone Activity** with `setContent { DevinTheme { HttpLogDetailScreen(...) } }`;
   `startActivity(context, logId)` API, injection and manifest unchanged. The Activity keeps the action
   implementations (share/copy/save) but runs them in `lifecycleScope`; the SAF save launcher stays an
   Activity-level `registerForActivityResult` (survives recreation; result handled as today).
2. **Top area**: no app bar title (parity) — a `Row` with a Material3 `TabRow` (Overview / Request /
   Response, strings `overview`/`request`/`response`) and an overflow `IconButton(Icons.Default.MoreVert)`
   opening a `DropdownMenu` with the same 7 items (same strings, leading icons from the same drawables).
   Pages: `HorizontalPager` (foundation 1.5.2, `@OptIn(ExperimentalFoundationApi::class)`), swipeable,
   tab click animates to page, selected tab follows the pager. `rememberPagerState { 3 }` — check the
   1.5.2 API signature (`rememberPagerState(initialPage, initialPageOffsetFraction, pageCount: () -> Int)`).
3. **Spans → `AnnotatedString`**: add an internal `Spanned.toAnnotatedString()` converter supporting the
   span types actually produced (`ForegroundColorSpan` incl. `JsonSpanTextUtil.MyForegroundColorSpan`,
   `BackgroundColorSpan`, `StyleSpan` bold/italic, `UnderlineSpan`; ignore others). Keep
   `JsonSpanTextUtil`, `HttpFormatUtils`, `SearchHighlightUtil` (they have unit tests — do not change their
   behaviour; adding new functions is fine).
4. **Content tab** = `LazyColumn`: headers item, copy-actions row, body line items. **Long lines are
   chunked** (≤1000 chars per item, same rule as the log detail page — move `chunkForDisplay` from
   `LogItemDetailScreen.kt` to a shared internal util, e.g. `uikit/text/ChunkText.kt`, and use it in both)
   so a single Text can never exceed Compose 1.5.2's ~262k px Constraints limit. Each list item keeps its
   base `AnnotatedString` (JSON colours).
5. **Search** (state in a small `remember`ed state holder per content tab; survives tab swipes via
   `rememberSaveable` for the query): local `OutlinedTextField`/search field (no debounce on the text
   itself); matches computed with `remember(query, lines)` on `Dispatchers.Default` via `produceState` or
   `LaunchedEffect` when the query length > 1 (same threshold); match = (itemIndex, start); render
   highlights by overlaying `SpanStyle(background, color)` ranges on top of the base AnnotatedString for
   the visible item (yellow/red for all, green bg for current) — highlight colours as today. Up/down
   cycle with wrap-around; counter "i / n" (bold) visible only when there are matches; scroll with
   `listState.animateScrollToItem(itemIndex + headerOffset)` after the first match (a short delay like
   today is fine but not required). Empty body → show `msg_body_empty` and no search field.
6. **Overview** = scrollable `Column` of label/value rows matching `item_http_detail_overview.xml`
   (TLS/cipher rows hidden when null), values selectable (`SelectionContainer`) if the XML values were
   selectable.
7. Loading: show a centred `CircularProgressIndicator` until `detailData` is non-null.

## Deletions (grep each first; keep anything still used)

- `res/layout/activity_http_log_detail.xml`, `item_http_detail_overview.xml`,
  `item_http_detail_content.xml`, `item_http_header_row.xml`, `item_http_body_row.xml`,
  `item_http_copy_actions_row.xml`, `res/menu/http_detail_menu.xml`.
- `present/http/items/*` FastAdapter item classes (keep `SearchItemBodyLine` only if still used).
- `vh_item_http_*` ids if unused. FastAdapter itself is removed in the cleanup step, not here.
- `Widget.LoggerApp.TabLayout` style if unused.

## Verification

Build + unit-test compile (HttpFormatUtilsTest, JsonSpanTextUtilTest, SearchHighlightUtilTest, HarConvertersTest
must still compile and pass: run `:present-app:testDebugUnitTest`). On device with the sample app's OkHttp
screen: open an HTTP log; tabs swipe/click; overview values; JSON colours in body; search with multiple
matches, up/down, counter, highlight; empty body case; each of the 7 menu actions; save HAR via picker;
rotate on each tab (tab + query kept); a very large response body doesn't crash.
