Root cause: CartCalculator.total force-unwrapped the nullable CartItem.discount (`!!`), so any cart item without a discount threw a NullPointerException.

Fix: treat a missing discount as 0% (`item.discount?.percent ?: 0`) in CartCalculator.kt.
