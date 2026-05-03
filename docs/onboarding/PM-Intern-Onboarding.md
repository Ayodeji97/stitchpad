# Welcome to StitchPad

A short guide to get you started. Read it once end-to-end, then come back to it as you go.

---

## 1. What is StitchPad?

StitchPad is the simple app Nigerian fashion designers will use to never lose a customer's measurements again.

It's a phone app — no laptops, no spreadsheets — that holds a tailor's customer book, body measurements, orders, and payment status, all in one place.

We're early. Less than 12 weeks from a real V1 launch. Everything you do here will show up in something a real person uses.

---

## 2. The problem we're solving

We surveyed Nigerian tailors and the picture was clear:

- **85%** said they'd lost customer records before
- **92%** still keep everything in paper notebooks
- **96%** said an app for this would be "extremely" or "very" useful
- **81%** said they'd pay for it

A tailor losing a notebook isn't a small thing. It costs them re-measurements, missed orders, embarrassed apologies, sometimes the whole customer. We're building the thing that quietly makes that fear go away.

---

## 3. Who are our users?

Picture one specific person:

- A solo fashion designer in **Abuja or Lagos**
- Runs the whole shop alone — sometimes with one apprentice
- Works on her **phone**. No laptop. (62% iPhone, 31% Android.)
- Identifies hard with her **brand name** — "House of Ayo", "Stitched by Tomi"
- Gets paid in **deposits** and chases the balance later
- Is not a tech person. If something needs explaining, we've already lost her.

Whenever we make a decision in this app, we ask: *would she understand this in 3 seconds, while standing in a busy market?*

If no, it's not done.

---

## 4. What we're building (V1)

Five things, no more, before launch:

1. **A customer book** — names, photos, contact info
2. **Measurements** — saved per customer, in cm or inches
3. **Order tracking** — with a clear status: In Progress / Ready / Delivered / Overdue
4. **Payment tracking** — deposit paid, balance owed, who still owes me
5. **Subscriptions** — Free (15 customers), ₦500/mo, or ₦1,000/mo paid through Paystack

Everything else — invoices, AI suggestions, fabric inventory — is later. We're disciplined about V1.

---

## 5. Where we are right now

We're heads-down on the **Dashboard** — the screen tailors land on every time they open the app. It's the most important screen we'll build, because if it doesn't help her *make money today*, she won't open it tomorrow.

Most of the foundation (sign up, login, basic navigation) already works. Customer management is next, then orders, then payments.

We **do not have a tester pool yet.** That's where you come in.

---

## 6. What you'll be doing

Your role here is real. You're not shadowing — you're doing the work.

### Your headline first project: find us users

Recruit **5 to 10 Nigerian fashion designers** we can test with. Anywhere you can find them is fair game:

- Instagram outreach (search hashtags like #NigerianTailor, #LagosFashionDesigner)
- Aso Ebi Bella & similar communities
- Friends-of-friends (everyone in Nigeria knows a tailor)
- Visiting markets in person (Wuse, Balogun, Aba Road)
- Tailor WhatsApp groups
- Fashion school students/alumni

Then build a small interview script and run sessions with them. We'll write the script together in your first week.

### And ongoing:

- **Be the user's voice.** If a screen confuses you, it'll confuse her. Say so.
- **Review every flow** before we ship — copy, button labels, error messages
- **Keep the backlog tidy in Notion** — groom it, prioritise, kill old ideas
- **Test every release** on your own phone before it goes anywhere
- **Watch competitors** (Tailorz, Stitchee, any Nigerian alternative) and bring back what's working
- **Write our test scripts and surveys** as you grow into the role

---

## 7. The tools you'll live in

| Tool | What it's for |
|------|---------------|
| **Notion** | Your home base. PRD, product backlog, design system, survey data, weekly log. Read everything in the Project Hub. |
| **Figma** | Designs and the component library. You don't need to use it to design — just to *see* what's coming. |
| **WhatsApp** | Daily back-and-forth with Daniel. And eventually, the tester group you'll help set up. |
| **The app itself** | Installed on your phone (next section). |

You'll get invited to Notion and Figma in your first day.

---

## 8. How we work

Three rules, and we don't break them:

**1. Mock before we build.**
Every new screen starts as a clickable mockup we can react to before any code is written. You'll review these and push back on what doesn't make sense.

**2. Brainstorm before we ship.**
Every meaningful feature gets a short written brief — what we're doing, why, who it's for, what could go wrong. Yours to question.

**3. Every release gets a smoke test.**
Before anything goes to a real tester, you run through it on your own phone. Daniel writes the steps; you do the run.

We move slowly *into* a decision and quickly *out of one*. The conversation upfront saves us a refactor later.

---

## 9. How to install and test the app

### Android

Daniel will send you a `.apk` file over WhatsApp.

1. Tap the file in WhatsApp
2. The first time, your phone will ask permission to "install unknown apps" — allow it
3. Tap install
4. Open the app, sign up, you're in

### iPhone

We don't have TestFlight (Apple's official testing system) set up yet. So for now, two options:

**Option A** — bring your iPhone to Daniel and he'll install the latest version directly via cable. This works for about 7 days at a time, then needs reinstalling.

**Option B** — use the Android version on a backup phone, and review the iOS look through Figma + screenshots.

We'll set up TestFlight properly once you've recruited testers and we're ready to ship to them.

### What to test (the "golden path")

Every time you open a new build, walk through this. If any step is broken or confusing, that's a bug.

1. Sign up with email + password
2. Set up your workshop (brand name, phone)
3. Add a customer
4. Add a measurement to that customer
5. Create an order for that customer
6. Mark the order as Ready
7. Mark the order as Delivered

If Daniel says "test edge case X for me", you do that too.

### How to file feedback

- **For UI bugs:** screen recording into the WhatsApp chat with Daniel + one line: "I expected X, got Y."
- **For ideas, copy fixes, longer thoughts:** add to the Notion backlog (we'll show you the template).
- **Don't sit on things.** A "small" issue you noticed is the same issue a real tailor will hit.

---

## 10. Your first week — checklist

- [ ] Read this doc end to end
- [ ] Get added to Notion + Figma (Daniel will invite you)
- [ ] Install the Android APK and run through the golden path above
- [ ] Read the **PRD** in Notion — one sitting, even if it's long
- [ ] Read the **survey data** in Notion — these are the people we're building for
- [ ] Try **3 competitor apps** (Android — search "tailor app" / "fashion designer app") and write a one-page comparison: what's good, what's bad, what we should steal
- [ ] Draft a one-page plan for *"how do I find 5 Nigerian tailors to test with?"* — bring it to our first sync
- [ ] First **30-min weekly sync** with Daniel (we'll book it)

---

## 11. A note on tone & culture

A few things we believe:

- **Warm beats clever.** The user feels respected, not impressed.
- **Obvious beats smart.** If a screen needs explaining, it's wrong.
- **Ship small beats ship perfect.** A rough thing in a tester's hands beats a polished thing in our heads.
- **Ask twice if unsure.** It's never annoying. Guessing wrong wastes more time.

We're casual but we care. Take the work seriously, not yourself.

---

## 12. Glossary

A handful of words you'll hear:

- **PRD** — Product Requirements Doc. The "what we're building and why" master doc in Notion.
- **Backlog** — the running list of features, fixes, and ideas. Lives in Notion.
- **Sprint** — a short focused chunk of work, usually 1–2 weeks.
- **Smoke test** — a quick end-to-end walk-through of a release to make sure nothing's on fire.
- **Golden path** — the main "happy" flow a user takes through the app, with no errors.
- **V1** — the first real version we'll release publicly.
- **Freemium** — free with limits + paid tiers. Our model: free up to 15 customers, ₦500/mo or ₦1,000/mo for more.
- **Churn** — when a user stops using (or paying for) the app. The thing we hate.
- **NBA** — Next Best Action. A nudge on the dashboard telling the tailor what to do right now to make money today.
- **Tester group** — a group of real users we ship early builds to before public launch. *You're going to build this.*

---

## Welcome again

Anything that's unclear, ask. There are no dumb questions in week one.

Have fun with it.
