Root cause: CartCalculator.total force-unwrapped (`!!`) the optional `CartItem.discount`, which is null for items without a discount, causing a NullPointerException when opening the cart.

Fix: treat a missing discount as 0% (`item.discount?.percent ?: 0`) in CartCalculator.kt.
