Root cause: The backend returned a product with `"price": null` in the product list, and `ProductMapper.map` called `getLong("price")`, which threw an uncaught JSONException on the worker thread and crashed the app.

Fix: `ProductMapper.map` now returns null when `price` is null, and `mapList` uses `mapNotNull` so products without a price are skipped and the rest of the list still loads.
