Root cause: The `Sort.PRICE_DESC` enum value sent the misspelled API value `price_dsc` instead of `price_desc`, so the backend ignored the sort and returned the default order.

Fix: corrected `apiValue` to `"price_desc"` in `Models.kt`. Not rebuilt or re-verified on the device (shell access was denied); the captured request showed `/search?q=mug&sort=price_dsc` with unsorted results.
