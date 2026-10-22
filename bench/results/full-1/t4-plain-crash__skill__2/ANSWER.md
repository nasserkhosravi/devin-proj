Root cause: CartCalculator.total force-unwrapped `item.discount!!`, but discount is nullable and cart items without a discount made it throw a NullPointerException.

Fix: treat a missing discount as 0% (`item.discount?.percent ?: 0`) so undiscounted items are priced at full amount.
