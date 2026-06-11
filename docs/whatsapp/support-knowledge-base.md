# StitchPad WhatsApp Support — Knowledge Base

The WhatsApp support bot answers **only** from the `supportKnowledge` Firestore
collection. It reads the collection live on every message, so edits in the
Firebase console take effect immediately — no deploy.

## How the bot uses this
- It keyword-matches the user's question against each article's `keywords` +
  `question`, sends the most relevant articles to Gemini, and the model answers
  **using only those articles**.
- If nothing matches, or the model isn't confident, the bot does **not** guess —
  it hands the chat to a human (email to support@ + relay to your phone).
- So: the broader and better-keyworded this collection is, the more the bot can
  handle on its own. Add an article for every recurring question.

## Document schema (`supportKnowledge/{autoId}`)
| Field | Type | Notes |
|---|---|---|
| `category` | string | grouping only (e.g. `account`, `orders`, `billing`) |
| `question` | string | the canonical question (also used for matching) |
| `answer_en` | string | English answer — keep it short & WhatsApp-friendly |
| `answer_pcm` | string | Nigerian Pidgin answer (optional; falls back to `answer_en`) |
| `keywords` | array<string> | lowercase match terms — add the words people actually type |
| `active` | boolean | `false` hides the article from the bot without deleting it |

## Editing in the Firebase console
1. Firestore → **Start collection** → ID `supportKnowledge` (first time only).
2. **Add document** → Auto-ID → add the fields above.
3. To bulk-load the starter set below, use the JSON at the end with your admin
   seed script (same pattern as the Testing/Admin batch scripts), or add them by
   hand.

> ⚠️ **Verify before go-live:** the answers below are a solid first draft, but
> confirm the specifics against the current app — exact screen/button labels,
> the live **prices**, and the **Free-plan limits** — and tweak the copy in your
> brand voice. Treat this as a starting point, not gospel.

---

## Starter articles

### account

**Q: I can't log in / I forgot my password**
- EN: Tap **Forgot password?** on the login screen and we'll email you a reset link. Check your spam folder if you don't see it. Make sure you're using the email you signed up with.
- PCM: For the login screen, press **Forgot password?** — we go send reset link to your email. Check your spam folder if e no show. Make sure say na the email wey you take register you dey use.
- keywords: `login`, `log`, `password`, `reset`, `cant access`, `signin`, `sign in`, `forgot`

**Q: Sign up says "Something went wrong"**
- EN: Please check your internet and try again. If it keeps happening, close and reopen the app, or try a different network. Still stuck? Reply "talk to a human".
- PCM: Abeg check your internet make you try again. If e continue, close the app open am again, or try another network. E still dey worry you? Reply "talk to a human".
- keywords: `something went wrong`, `signup`, `sign up`, `register`, `cant create`, `error`

**Q: I didn't get my verification email**
- EN: Open the app and tap **Resend** on the verify-email screen (wait ~1 minute between tries). Check spam, and confirm the email address is correct in your profile.
- PCM: Open the app, press **Resend** for the verify-email screen (wait like 1 minute before you try again). Check spam, and confirm say the email correct for your profile.
- keywords: `verification`, `verify`, `email`, `confirm`, `didnt receive`, `resend`

**Q: How do I change my business name or phone number?**
- EN: Go to **Settings → Workshop** (or your profile) to update your business name and contact details.
- PCM: Go to **Settings → Workshop** (or your profile) make you change your business name and contact details.
- keywords: `change`, `business name`, `workshop`, `phone`, `number`, `profile`, `edit`

### app

**Q: The app is not working / keeps crashing**
- EN: Try these in order: 1) close and reopen the app, 2) check your internet, 3) update StitchPad in the Play Store / App Store, 4) restart your phone. If it still crashes, tell us what screen it happens on and reply "talk to a human".
- PCM: Try am like this: 1) close the app open am again, 2) check internet, 3) update StitchPad for Play Store / App Store, 4) restart your phone. If e still dey crash, tell us which screen e dey happen and reply "talk to a human".
- keywords: `not working`, `crash`, `crashing`, `freeze`, `slow`, `app`, `broken`, `bug`

**Q: How do I update the app?**
- EN: Open the **Play Store** (Android) or **App Store** (iPhone), search "StitchPad", and tap **Update** if it's available.
- PCM: Open **Play Store** (Android) or **App Store** (iPhone), search "StitchPad", press **Update** if e dey.
- keywords: `update`, `new version`, `upgrade app`, `latest`

**Q: Does StitchPad work offline?**
- EN: You can view your saved customers and orders offline, but you need internet to sync new changes and to send WhatsApp reminders.
- PCM: You fit see your customers and orders wey you don save even without internet, but you need internet to sync new changes and to send WhatsApp reminders.
- keywords: `offline`, `no internet`, `network`, `sync`, `data connection`

### customers

**Q: How do I add a customer?**
- EN: From the Customers tab, tap the **+** button, enter their name and details, and save. You can add their measurements at the same time or later.
- PCM: For the Customers tab, press the **+** button, enter their name and details, save am. You fit add their measurements that same time or later.
- keywords: `add customer`, `new customer`, `create customer`, `client`

**Q: How do I add or edit measurements?**
- EN: Open a customer and tap **Measurements** to add or edit their sizes. You can add your own custom measurement fields if the defaults don't cover a style.
- PCM: Open one customer, press **Measurements** make you add or change their sizes. You fit add your own custom measurement fields if the default ones no cover one style.
- keywords: `measurement`, `measurements`, `sizes`, `custom field`, `chest`, `waist`

### orders

**Q: How do I create an order?**
- EN: From the Orders tab (or a customer's page), tap **+ New order**, pick the customer, add the garment, price, deposit and deadline, then save.
- PCM: For the Orders tab (or customer page), press **+ New order**, choose the customer, add the garment, price, deposit and deadline, then save.
- keywords: `create order`, `new order`, `add order`, `job`

**Q: How do I record a payment or deposit?**
- EN: Open the order and tap **Add payment** to record a deposit or balance payment. The outstanding balance updates automatically.
- PCM: Open the order, press **Add payment** make you record deposit or balance. The remaining balance go update by itself.
- keywords: `payment`, `deposit`, `balance`, `record payment`, `paid`, `money`

**Q: How do I mark an order as ready or delivered?**
- EN: Open the order and change its status to **Ready** or **Delivered**. Customers you owe nothing show as collected.
- PCM: Open the order, change the status to **Ready** or **Delivered**. The ones wey balance no dey go show as collected.
- keywords: `ready`, `delivered`, `status`, `complete`, `collect`, `pickup`

### messaging

**Q: How do I send a WhatsApp reminder to a customer?**
- EN: Open a customer or order and use **Draft message** — StitchPad writes a friendly WhatsApp message (balance reminder, ready-for-pickup, follow-up) that you can edit and send.
- PCM: Open one customer or order, use **Draft message** — StitchPad go write better WhatsApp message (balance reminder, ready for pickup, follow-up) wey you fit edit and send.
- keywords: `whatsapp`, `message`, `reminder`, `draft`, `send message`, `follow up`, `text customer`

### billing

**Q: What plans does StitchPad have and how much?**
- EN: There's a Free plan, **Tailor Pro** and **Tailor Atelier**. You can see the current prices and what each unlocks on the **Plans/Upgrade** screen in the app.
- PCM: We get Free plan, **Tailor Pro** and **Tailor Atelier**. You fit see the current prices and wetin each one dey unlock for the **Plans/Upgrade** screen inside the app.
- keywords: `plan`, `plans`, `price`, `pricing`, `pro`, `atelier`, `cost`, `how much`, `subscription`

**Q: How do I upgrade to Pro / Atelier?**
- EN: Go to **Settings → Plans** (or the upgrade banner), pick a plan and pay securely with Paystack (card or transfer). Your plan unlocks as soon as payment is confirmed.
- PCM: Go to **Settings → Plans** (or the upgrade banner), choose plan and pay with Paystack (card or transfer). Your plan go open once we confirm the payment.
- keywords: `upgrade`, `subscribe`, `pay`, `paystack`, `buy pro`, `unlock`, `payment plan`

**Q: What are the limits on the Free plan?**
- EN: The Free plan lets you manage a limited number of customers; you can see your current usage and limit on the Plans screen, and upgrade any time for more.
- PCM: Free plan dey let you manage small number of customers; you fit see your current usage and limit for the Plans screen, and upgrade any time make you get more.
- keywords: `free plan`, `limit`, `how many customers`, `cap`, `locked`, `maximum`

**Q: I paid but my plan hasn't unlocked**
- EN: It usually unlocks within a minute of a confirmed payment. Close and reopen the app once. If it's still locked after a few minutes, reply "talk to a human" and we'll check it for you.
- PCM: E dey usually open within one minute after we confirm payment. Close the app open am once. If e still lock after some minutes, reply "talk to a human" make we check am for you.
- keywords: `paid`, `not unlocked`, `payment not working`, `still locked`, `upgraded but`

### data & account

**Q: Is my data safe / is it backed up?**
- EN: Your data is securely stored in the cloud and synced across your devices, so it's safe even if you change phones. Just sign in with the same account.
- PCM: Your data dey safe for cloud and e dey sync across your devices, so e safe even if you change phone. Just sign in with the same account.
- keywords: `data`, `safe`, `backup`, `secure`, `lose data`, `change phone`, `privacy`

**Q: How do I delete my account?**
- EN: Account deletion is permanent and removes your data. Reply "delete my account" and a member of our team will help you confirm and process it.
- PCM: To delete account na permanent thing and e go remove your data. Reply "delete my account" and our team go help you confirm and do am.
- keywords: `delete account`, `close account`, `remove account`, `deactivate`

### general

**Q: How do I contact a human / customer support?**
- EN: Reply "talk to a human" any time and we'll connect you with our team. You can also email support@getstitchpad.com.
- PCM: Reply "talk to a human" any time make we connect you with our team. You fit also email support@getstitchpad.com.
- keywords: `human`, `support`, `agent`, `contact`, `customer care`, `help`, `talk to someone`

---

## JSON seed (for an admin script / bulk import)

```json
[
  { "category": "account", "active": true, "question": "I can't log in / I forgot my password", "answer_en": "Tap Forgot password? on the login screen and we'll email you a reset link. Check your spam folder if you don't see it. Make sure you're using the email you signed up with.", "answer_pcm": "For the login screen, press Forgot password? — we go send reset link to your email. Check your spam folder if e no show. Make sure say na the email wey you take register you dey use.", "keywords": ["login","log","password","reset","cant access","signin","sign in","forgot"] },
  { "category": "account", "active": true, "question": "Sign up says Something went wrong", "answer_en": "Please check your internet and try again. If it keeps happening, close and reopen the app, or try a different network. Still stuck? Reply talk to a human.", "answer_pcm": "Abeg check your internet make you try again. If e continue, close the app open am again, or try another network. E still dey worry you? Reply talk to a human.", "keywords": ["something went wrong","signup","sign up","register","cant create","error"] },
  { "category": "account", "active": true, "question": "I didn't get my verification email", "answer_en": "Open the app and tap Resend on the verify-email screen (wait about a minute between tries). Check spam, and confirm the email address is correct in your profile.", "answer_pcm": "Open the app, press Resend for the verify-email screen (wait like 1 minute before you try again). Check spam, and confirm say the email correct for your profile.", "keywords": ["verification","verify","email","confirm","didnt receive","resend"] },
  { "category": "account", "active": true, "question": "How do I change my business name or phone number?", "answer_en": "Go to Settings then Workshop (or your profile) to update your business name and contact details.", "answer_pcm": "Go to Settings then Workshop (or your profile) make you change your business name and contact details.", "keywords": ["change","business name","workshop","phone","number","profile","edit"] },
  { "category": "app", "active": true, "question": "The app is not working / keeps crashing", "answer_en": "Try these in order: 1) close and reopen the app, 2) check your internet, 3) update StitchPad in the Play Store / App Store, 4) restart your phone. If it still crashes, tell us what screen it happens on and reply talk to a human.", "answer_pcm": "Try am like this: 1) close the app open am again, 2) check internet, 3) update StitchPad for Play Store / App Store, 4) restart your phone. If e still dey crash, tell us which screen e dey happen and reply talk to a human.", "keywords": ["not working","crash","crashing","freeze","slow","app","broken","bug"] },
  { "category": "app", "active": true, "question": "How do I update the app?", "answer_en": "Open the Play Store (Android) or App Store (iPhone), search StitchPad, and tap Update if it's available.", "answer_pcm": "Open Play Store (Android) or App Store (iPhone), search StitchPad, press Update if e dey.", "keywords": ["update","new version","upgrade app","latest"] },
  { "category": "app", "active": true, "question": "Does StitchPad work offline?", "answer_en": "You can view your saved customers and orders offline, but you need internet to sync new changes and to send WhatsApp reminders.", "answer_pcm": "You fit see your customers and orders wey you don save even without internet, but you need internet to sync new changes and to send WhatsApp reminders.", "keywords": ["offline","no internet","network","sync","data connection"] },
  { "category": "customers", "active": true, "question": "How do I add a customer?", "answer_en": "From the Customers tab, tap the + button, enter their name and details, and save. You can add their measurements at the same time or later.", "answer_pcm": "For the Customers tab, press the + button, enter their name and details, save am. You fit add their measurements that same time or later.", "keywords": ["add customer","new customer","create customer","client"] },
  { "category": "customers", "active": true, "question": "How do I add or edit measurements?", "answer_en": "Open a customer and tap Measurements to add or edit their sizes. You can add your own custom measurement fields if the defaults don't cover a style.", "answer_pcm": "Open one customer, press Measurements make you add or change their sizes. You fit add your own custom measurement fields if the default ones no cover one style.", "keywords": ["measurement","measurements","sizes","custom field","chest","waist"] },
  { "category": "orders", "active": true, "question": "How do I create an order?", "answer_en": "From the Orders tab (or a customer's page), tap + New order, pick the customer, add the garment, price, deposit and deadline, then save.", "answer_pcm": "For the Orders tab (or customer page), press + New order, choose the customer, add the garment, price, deposit and deadline, then save.", "keywords": ["create order","new order","add order","job"] },
  { "category": "orders", "active": true, "question": "How do I record a payment or deposit?", "answer_en": "Open the order and tap Add payment to record a deposit or balance payment. The outstanding balance updates automatically.", "answer_pcm": "Open the order, press Add payment make you record deposit or balance. The remaining balance go update by itself.", "keywords": ["payment","deposit","balance","record payment","paid","money"] },
  { "category": "orders", "active": true, "question": "How do I mark an order as ready or delivered?", "answer_en": "Open the order and change its status to Ready or Delivered. Orders where nothing is owed show as collected.", "answer_pcm": "Open the order, change the status to Ready or Delivered. The ones wey balance no dey go show as collected.", "keywords": ["ready","delivered","status","complete","collect","pickup"] },
  { "category": "messaging", "active": true, "question": "How do I send a WhatsApp reminder to a customer?", "answer_en": "Open a customer or order and use Draft message — StitchPad writes a friendly WhatsApp message (balance reminder, ready-for-pickup, follow-up) that you can edit and send.", "answer_pcm": "Open one customer or order, use Draft message — StitchPad go write better WhatsApp message (balance reminder, ready for pickup, follow-up) wey you fit edit and send.", "keywords": ["whatsapp","message","reminder","draft","send message","follow up","text customer"] },
  { "category": "billing", "active": true, "question": "What plans does StitchPad have and how much?", "answer_en": "There's a Free plan, Tailor Pro and Tailor Atelier. You can see the current prices and what each unlocks on the Plans/Upgrade screen in the app.", "answer_pcm": "We get Free plan, Tailor Pro and Tailor Atelier. You fit see the current prices and wetin each one dey unlock for the Plans/Upgrade screen inside the app.", "keywords": ["plan","plans","price","pricing","pro","atelier","cost","how much","subscription"] },
  { "category": "billing", "active": true, "question": "How do I upgrade to Pro or Atelier?", "answer_en": "Go to Settings then Plans (or the upgrade banner), pick a plan and pay securely with Paystack (card or transfer). Your plan unlocks as soon as payment is confirmed.", "answer_pcm": "Go to Settings then Plans (or the upgrade banner), choose plan and pay with Paystack (card or transfer). Your plan go open once we confirm the payment.", "keywords": ["upgrade","subscribe","pay","paystack","buy pro","unlock","payment plan"] },
  { "category": "billing", "active": true, "question": "What are the limits on the Free plan?", "answer_en": "The Free plan lets you manage a limited number of customers; you can see your current usage and limit on the Plans screen, and upgrade any time for more.", "answer_pcm": "Free plan dey let you manage small number of customers; you fit see your current usage and limit for the Plans screen, and upgrade any time make you get more.", "keywords": ["free plan","limit","how many customers","cap","locked","maximum"] },
  { "category": "billing", "active": true, "question": "I paid but my plan hasn't unlocked", "answer_en": "It usually unlocks within a minute of a confirmed payment. Close and reopen the app once. If it's still locked after a few minutes, reply talk to a human and we'll check it for you.", "answer_pcm": "E dey usually open within one minute after we confirm payment. Close the app open am once. If e still lock after some minutes, reply talk to a human make we check am for you.", "keywords": ["paid","not unlocked","payment not working","still locked","upgraded but"] },
  { "category": "data", "active": true, "question": "Is my data safe / is it backed up?", "answer_en": "Your data is securely stored in the cloud and synced across your devices, so it's safe even if you change phones. Just sign in with the same account.", "answer_pcm": "Your data dey safe for cloud and e dey sync across your devices, so e safe even if you change phone. Just sign in with the same account.", "keywords": ["data","safe","backup","secure","lose data","change phone","privacy"] },
  { "category": "data", "active": true, "question": "How do I delete my account?", "answer_en": "Account deletion is permanent and removes your data. Reply delete my account and a member of our team will help you confirm and process it.", "answer_pcm": "To delete account na permanent thing and e go remove your data. Reply delete my account and our team go help you confirm and do am.", "keywords": ["delete account","close account","remove account","deactivate"] },
  { "category": "general", "active": true, "question": "How do I contact a human / customer support?", "answer_en": "Reply talk to a human any time and we'll connect you with our team. You can also email support@getstitchpad.com.", "answer_pcm": "Reply talk to a human any time make we connect you with our team. You fit also email support@getstitchpad.com.", "keywords": ["human","support","agent","contact","customer care","help","talk to someone"] }
]
```
