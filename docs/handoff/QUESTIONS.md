# Africa expansion currency decisions

## Approved — 2 November 2026

Daniel selected **separate totals per currency**, with no currency filter required and no FX
conversion. Partition by the currency stamped on each order before adding any monetary values.
Single-currency workshops retain the simple existing report/dashboard layout.

## Clarification requested — VIP spend threshold outside Nigeria

The existing customer VIP rule is five lifetime orders **or ₦500,000** lifetime spend. No equivalent
GHS/KES/CFA spend thresholds are specified. Proposed interim behavior: retain global repeat/VIP
order-count qualification; apply the existing monetary threshold only to NGN spend, without FX.
Root has been informed; explicit foreign-currency monetary thresholds remain unconfigured.

## Pending — weekly goal after a country change

Safety behavior implemented: existing weekly goals keep their stamped currency (legacy goals are
NGN), and progress only counts orders in that same currency. New goals use the hydrated account
country. The existing editor has no currency reset/change control, so moving countries does not
silently turn an NGN target into GHS. Product direction is needed for an explicit option to replace
an existing goal in the new account currency; no automatic conversion or target reset is planned.
