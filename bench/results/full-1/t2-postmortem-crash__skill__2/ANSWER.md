Root cause: The backend returned a product with `"price": null`, and `ProductMapper.map` called `JSONObject.getLong("price")`, which throws an uncaught `JSONException` on the background thread and crashes the app when the product list loads.

Fix: `ProductMapper.mapList` now maps each item with a new `mapOrNull`, which skips items that are not objects or that have a null or invalid `id`, `name` or `price`. One bad product no longer crashes the whole list, and the remaining products still display.
