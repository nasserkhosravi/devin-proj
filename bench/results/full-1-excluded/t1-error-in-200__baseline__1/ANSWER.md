Root cause: The app hardcoded the expired coupon "SUMMER" in every checkout request, causing the server to reject all orders and return `{"status":"error","code":"COUPON_EXPIRED",...}` with HTTP 200; `CheckoutParser` treated any 200 response as success without inspecting the body, so it displayed "Order placed" with an empty orderId while no order was ever created.

## Fix

Two changes were made:

1. **`CheckoutParser.kt`** — Added a check for `status == "error"` in the response body. The server returns errors as HTTP 200 with a JSON body containing `status`, `code`, and `message` fields. The parser now returns `CheckoutResult.Failure` in that case instead of silently treating it as success.

2. **`MainActivity.kt`** — Changed `coupon = "SUMMER"` to `coupon = null`. The coupon "SUMMER" expired on 2026-09-30 and is no longer valid, so it was removed from the checkout call so orders can actually be created.
