Root cause: The backend returns HTTP 200 with a body like {"status":"error","code":"COUPON_EXPIRED",...} for a failed checkout, and CheckoutParser only checked the HTTP code, so it reported Success with an empty orderId ("Order placed") although no order was created.

Fix: CheckoutParser now treats a body with status "error" or without an orderId as CheckoutResult.Failure, using the body's code and message, so the user sees the real failure.
