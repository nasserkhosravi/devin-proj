Root cause: The backend reports checkout failures (e.g. COUPON_EXPIRED) as HTTP 200 with an error JSON body, and CheckoutParser treated every 2xx as success, so "Order placed" was shown with an empty orderId even though no order was created.

Fix: CheckoutParser.parse now inspects the body of 2xx responses; if it has an error `status`/`code`, or no `orderId`, it returns CheckoutResult.Failure with the server's code and message instead of Success.
