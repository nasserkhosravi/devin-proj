Root cause: The backend reports checkout errors (e.g. COUPON_EXPIRED) as HTTP 200 with an error JSON body, and CheckoutParser treated every 2xx as success, so "Order placed" was shown with an empty orderId even though no order was created.

Fix: CheckoutParser now inspects the body: `status: "error"`/`code` yields CheckoutResult.Failure with the server's code and message, and a missing orderId or unparseable body also yields Failure instead of a false Success.
