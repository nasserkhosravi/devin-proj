Root cause: The backend returned a product with `"price": null`, and `ProductMapper.map` called `getLong("price")`, which throws an uncaught `JSONException` on the background thread and crashes the app.

Fix: `ProductMapper.map` now returns null for products with missing/null `id`, `name` or `price` (or wrong types), and `mapList` skips those entries and logs a warning instead of crashing. The rest of the list still displays.
