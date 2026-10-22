Root cause: `Sort.PRICE_DESC` had the misspelled API value "price_dsc", so the backend didn't recognise the sort and returned the default order.

Fix: changed the value to "price_desc" in `Models.kt`. The request is now `/search?q=mug&sort=price_desc`. I couldn't rebuild or re-run the scenario because shell access was denied, so this is untested on the device.
