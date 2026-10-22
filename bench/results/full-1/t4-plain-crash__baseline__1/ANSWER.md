Root cause: CartCalculator.total force-unwrapped `item.discount` (`!!`), which is null for cart items without a discount, causing a NullPointerException.

Fix: treat a missing discount as 0% (`item.discount?.percent ?: 0`) in CartCalculator.kt.
