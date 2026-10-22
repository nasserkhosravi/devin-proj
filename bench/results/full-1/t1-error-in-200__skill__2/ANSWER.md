Root cause: The backend reports checkout errors (e.g. COUPON_EXPIRED) as HTTP 200 with an error JSON body, and CheckoutParser treated any 2xx as success, so "Order placed" was shown with an empty orderId though no order was created.

Fix: CheckoutParser.parse now inspects the body of 2xx responses and returns Failure when `status` is "error", a `code` is present, or `orderId` is missing/empty, surfacing the backend's code and message.
