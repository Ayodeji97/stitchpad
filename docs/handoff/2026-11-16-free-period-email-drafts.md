# Free-period-ending email — drafts (2026-11-16)

Dates decided by Daniel on 2026-11-16:

| Moment | Date | What happens |
|---|---|---|
| T-30, notice | **Mon 30 Nov 2026** | First email + in-app notice. Billing must be on (`config/app.billingEnabled = true`). |
| T-14 | Wed 16 Dec 2026 | Reminder email |
| T-7 | Wed 23 Dec 2026 | Reminder email + push |
| T-0, free access ends | **Wed 30 Dec 2026** | Grace period starts: saving still works, with a warning |
| Grace ends, read-only | **Wed 6 Jan 2027** | Without a plan: view-only, except recording payments and changing order status |

Audience (built 2026-11-16, read-only, PII kept outside the repo at
`~/StitchPad Analytics Data/local/free_period_audience_2026-11-16.csv`): **20** owners with an order or
customer created or updated in the last 30 days. All are NG/NGN, English, Atelier `launch_free`, with
verified emails, and none opted out or bounced. Rebuild the list on 29 Nov before sending.

Prices (NG): Pro ₦2,000/month or ₦20,000/year · Atelier ₦4,000/month or ₦40,000/year. No founder offer.
iOS users: don't mention Paystack. Send them to the Upgrade screen (Apple in-app purchase).

---

## English (T-30, 30 Nov)

**Subject:** Your free StitchPad access ends on 30 December. Your records are safe.

Hello {name},

Thank you for running {business} on StitchPad. Since launch, you've had every feature free.

From **30 December 2026**, StitchPad becomes a paid app. This is how we keep improving it and keep your records safe.

**What stays the same:** all your customers, measurements, orders and photos stay in your account. We never delete them.

**What changes:** after your free access ends, you'll have until **6 January 2027** to choose a plan. After that, without a plan, you can still:
- view all your customers, measurements and orders
- record payments and update the status of jobs you've already started, so you can finish them

But you won't be able to add new customers, measurements or orders.

**Plans:** Pro ₦2,000/month · Atelier ₦4,000/month (paying yearly saves you two months).

**What to do:** update StitchPad to the latest version, then open **Settings → Plan** to choose.

[Update & open StitchPad]

Any question, just reply to this email. I read every one.

Daniel, StitchPad

*Reminders (16 Dec, 23 Dec): same body, subject "{n} days left on your free StitchPad access".*

---

## French (only for `language = fr`; none in the current list. Needs native-speaker review)

**Objet :** Votre accès gratuit à StitchPad se termine le 30 décembre. Vos données sont en sécurité.

Bonjour {name},

Merci d'utiliser StitchPad pour {business}. Depuis le lancement, toutes les fonctionnalités vous étaient offertes.

À partir du **30 décembre 2026**, StitchPad devient une application payante. C'est ce qui nous permet de continuer à l'améliorer et à protéger vos données.

**Ce qui ne change pas :** vos clients, mesures, commandes et photos restent dans votre compte. Nous ne les supprimons jamais.

**Ce qui change :** une fois l'accès gratuit terminé, vous aurez jusqu'au **6 janvier 2027** pour choisir une formule. Ensuite, sans formule, vous pourrez toujours :
- consulter tous vos clients, mesures et commandes
- enregistrer des paiements et mettre à jour le statut des commandes en cours, pour les terminer

Vous ne pourrez plus ajouter de nouveaux clients, mesures ou commandes.

**Formules :** Pro {prix}/mois · Atelier {prix}/mois (deux mois offerts en paiement annuel).

**Que faire :** mettez StitchPad à jour, puis ouvrez **Réglages → Formule**.

[Mettre à jour et ouvrir StitchPad]

Une question ? Répondez simplement à cet e-mail. Je lis chaque message.

Daniel, StitchPad
