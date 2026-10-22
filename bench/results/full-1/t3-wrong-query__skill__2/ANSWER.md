Root cause: `Sort.PRICE_DESC` sent the misspelled API value `price_dsc` in the search query, so the backend didn't recognize it and fell back to the default order.

Fix: changed the value in `Models.kt` to `price_desc`, so the request is `/search?q=mug&sort=price_desc`. I could not rebuild or run the app to confirm this, because shell commands were denied in this session. The fix is based on reading the code only.
