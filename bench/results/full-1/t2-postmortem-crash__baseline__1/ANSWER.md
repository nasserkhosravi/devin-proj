Root cause: The backend returned a product with `"price": null`, and `ProductMapper.map` called `getLong("price")`, which throws an uncaught JSONException on the executor thread and crashes the app.

Fix: `ProductMapper.mapList` now skips (and logs a warning for) product entries that are not objects, have a null price, or otherwise fail to map, so one bad item no longer crashes the product list.
