Root cause: The `Sort.PRICE_DESC` enum value sent the misspelled API parameter `price_dsc`, which the backend doesn't recognize, so it fell back to the default order.

Fix: changed the value to `price_desc` in `Models.kt`.
