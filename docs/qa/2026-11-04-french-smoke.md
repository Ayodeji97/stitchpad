# French smoke test

Set the device/app language to French and restart the app. Country and language are
independent: changing language must not change existing order currency or amounts.

- Sign in; check dashboard, customer/order details, measurement forms, collection list,
  Reports, settings and date pickers. Check accents, wrapping and buttons on a small phone.
- Create/edit a customer and order; confirm stored names, amounts and measurements stay intact.
- Generate a French customer message; verify names, dates and money against the order.
  Nigerian workshops retain Pidgin; other workshops offer English and French only.
- Switch to English and restart; verify English returns and currency is unchanged.
- After reviewed Functions deployment, request French verification/reset emails and a
  debug daily digest; check links, amounts, unsubscribe and language. Do not deploy all Functions.

Translations are a machine-assisted draft and need French-speaking tailoring review.
This slice covers client resources, auth emails, daily digest/nudges/push and AI drafts.
Legacy gift, subscription reminder and campaign emails still need a follow-up audit.
No release/store change or measurement inference is enabled by this branch.
