# Rebrand Illustration Audit — Saffron → Adire

Captured during PR-A smoke testing on 2026-05-14/15. The new indigo + warm-paper brand renders cleanly for the *code* surfaces (auth screens, buttons, pills, type, theme), but the existing illustrations under `composeApp/src/commonMain/composeResources/drawable/` are saffron-era — red drapery, saffron-yellow scissors, yellow measuring tape, orange shirts. Visible on the dashboard right now in the "1 order needs attention" hero card.

This doc lists every illustration asset, classifies it, and provides regeneration prompts for the ones that need new versions in the Adire Atelier palette.

## Common style guide (paste at the top of every prompt)

```
Style: hand-painted digital illustration with soft gouache / watercolor texture.
Subject framed against a transparent or near-white background with a subtle
soft drop shadow at the base. Centered composition with negative space around
the subject — no edge bleed.

PALETTE (Adire Atelier):
- Primary: deep adire indigo #2C3E7C (Yoruba textile dye reference)
- Secondary: burnt sienna #B85A30 (workshop bench / leather)
- Heritage accent: saffron #E8A800 — used SPARINGLY only as a small detail
  (a single thread, a button, a stitched line). NEVER as the dominant color.
- Neutrals: warm cream / off-white #FAF6EC for paper-tone elements, warm
  dark brown #3A2E1F for wood / leather / bench
- Status colors are reserved for UI semantics — DO NOT use red, green, or
  bright orange in the illustration palette.

NEGATIVE PROMPTS (avoid):
- No bright yellow (#FFD700 / saffron yellow scissors and tape — that's
  what we are replacing)
- No red drapery or red thread spools
- No bright orange clothing
- No scissors as a primary subject (the competitor StyleOS uses scissors
  in their logo; we deliberately avoid the scissors trope)
- No generic tech / fintech aesthetic
- No emojis or text in the illustration
- No people's faces unless explicitly requested

DIMENSIONS:
- Square aspect ratio (1:1) — these get rendered at 384px and 512px webp
- Subject occupies ~70% of canvas; ~15% negative space on each side
```

---

## Tier 1 — Must regenerate (strongly saffron, visibly inconsistent)

These are the ones that visibly clash with the new brand. Highest priority.

### `dashboard_hero_busy.png`
**Current:** mannequin with red drapery + saffron-yellow scissors + brown thread spool with brown thread.
**Used on:** dashboard hero when a tailor has overdue / many active orders. *Visible right now on Daniel's screenshot.*
**Prompt:**
> Adire Atelier illustration of a tailor's dress form (mannequin) actively in use, with deep indigo adire-patterned cloth draped over the shoulder. A measuring tape in warm sienna wraps the bust. A spool of indigo thread sits at the base. Subtle saffron stitching detail on the cloth edge as a rare heritage accent. Hand-painted gouache style. Composition reads as "busy workshop in motion." No scissors. No yellow tape.

### `dashboard_hero_welcome.png`
**Current:** mannequin + red folded fabric + red thread spools + yellow tape + yellow stars + yellow scissors.
**Used on:** dashboard welcome state (first launch).
**Prompt:**
> Adire Atelier illustration of a tailor's dress form welcoming new orders. Deep indigo background fabric folded neatly below. Sienna measuring tape draped softly across the mannequin's shoulder. Two indigo thread spools stacked beside. Tiny saffron stitched stars around (1-3 small ones, very subtle) — celebration accent. Soft warm-paper base. Hand-painted gouache. No scissors. No red.

### `dashboard_hero_first_order.png`
**Current:** mannequin + yellow measuring tape + green notebook with brown pen.
**Used on:** dashboard hero for "your first order is in!" state.
**Prompt:**
> Adire Atelier illustration of a dress form with a sienna measuring tape spiraling around the body. A small indigo notebook with a paper-stitched binding sits at the base, a wooden pencil resting on top. Composition reads as "first order — measure twice." Saffron thread detail on the notebook cover (single thin line). Hand-painted gouache. No yellow tape.

### `dashboard_hero_quiet.png`
**Current:** mannequin + saffron-yellow draped fabric + yellow measuring tape.
**Used on:** dashboard hero on quiet days / no urgent orders.
**Prompt:**
> Adire Atelier illustration of a dress form draped with calm indigo adire-patterned fabric, folded peacefully. A roll of unworked indigo cloth at the base. No urgency, very still composition. Sienna measuring tape coiled neatly to the side. Hand-painted gouache, soft and meditative. No yellow.

### `dashboard_empty_pipeline.png`
**Current:** mannequin + wooden clothing rack with empty hangers + yellow measuring tape + small green plant.
**Used on:** empty work pipeline state.
**Prompt:**
> Adire Atelier illustration of a tailor's atelier with an empty wooden clothing rack (3-4 wooden hangers, no garments) beside a dress form. Sienna measuring tape draped on the mannequin's shoulder. Small potted plant in a warm-ceramic pot at the base. Composition reads as "ready for work — pipeline empty." Hand-painted gouache. No yellow.

### `dashboard_empty_customers.png`
**Current:** two stylized figures shaking hands, one in orange shirt, one in green shirt, red thread spool between them.
**Used on:** empty customer list state.
**Prompt:**
> Adire Atelier illustration of two stylized figures (faceless, simple shapes) greeting / meeting in a tailor's workspace. One wears an indigo shirt, the other wears a sienna shirt. Between them sits a small indigo thread spool. Warm-paper background. Hand-painted gouache. Composition reads as "first customer welcome." No red, no orange.

---

## Tier 2 — Worth regenerating for cohesion (less urgent)

These are not visibly broken but use a non-indigo palette. Regenerate for full brand consistency.

### `dashboard_hero_money.png`
**Current:** brown leather wallet with green Naira bills sticking out, gold Naira coins.
**Used on:** dashboard hero for revenue moments / collect payment.
**Prompt:**
> Adire Atelier illustration of a warm sienna leather wallet on a workshop bench. Indigo-banded Naira notes peeking out, a small stack of warm-bronze Naira coins beside it. Soft warm-paper / wood base. Hand-painted gouache. Composition reads as "money to collect." Keep the Naira ₦ glyph visible.

### `dashboard_hero_pickup.png`
**Current:** paper bag with green folded fabric inside, green hang tag, all in green palette.
**Used on:** dashboard hero for pickup ready state.
**Prompt:**
> Adire Atelier illustration of a brown paper bag with neatly folded indigo adire-patterned fabric inside, ready for customer pickup. A small sienna hang tag tied with cream thread. Warm-paper base. Hand-painted gouache. Composition reads as "order ready for pickup." No green dominant.

### `dashboard_hero_steady.png`
**Current:** mannequin + green draped fabric + green thread + green measuring tape.
**Used on:** dashboard hero for steady-state work-in-progress.
**Prompt:**
> Adire Atelier illustration of a dress form mid-fitting, sienna measuring tape pinned vertically along the bust line. Indigo fabric folded over a small wooden bench beside. A wooden thread spool with indigo thread. Hand-painted gouache. Composition reads as "steady work in progress." No green.

### `dashboard_empty_nba.png`
**Current:** open notebook with cream pages + brown pen + olive branch + red bookmark ribbon.
**Used on:** empty "Next Best Actions" state.
**Prompt:**
> Adire Atelier illustration of an open paper notebook with cream pages, an indigo fountain pen resting on it. A small sprig of dried flora (sage or olive — muted green is fine) at the corner. An indigo bookmark ribbon tucked into the spine. Hand-painted gouache. Composition reads as "fresh page — nothing to do yet." Replace red bookmark with indigo.

---

## Tier 3 — Onboarding photos (different sourcing)

These three are real photographs of Nigerian tailors, not illustrations. They use heavy saffron-yellow accents in fabric / wall art / Ankara patterns. Cohesion-wise they don't match the new brand, but they're authentic photography — replacing them requires re-shooting or sourcing new images.

### `onboarding_notebook.jpg`
**Current:** Nigerian woman in saffron-pattern Ankara dress using a phone, with a saffron textile artwork on the wall behind her and saffron Ankara dress on a mannequin in the background.
**Recommendation:** find or shoot a photo of a Nigerian tailor working with **indigo Ankara / indigo adire textile**, in a softly lit atelier setting. Subject can hold a notebook or phone. Background should NOT feature bright saffron.

### `onboarding_measurements.jpg`
**Current:** two Nigerian women in cream linen; one measures the other with yellow measuring tape; saffron abstract artwork on the wall behind; saffron-yellow fabric on the worktable in the foreground.
**Recommendation:** find or shoot a photo of two Nigerian tailors taking measurements, with a **sienna or warm-bronze measuring tape** (not yellow). The background art and foreground fabric should be indigo or neutral — not saffron.

### `onboarding_orders.jpg`
**Current:** Nigerian man cutting saffron-yellow Ankara fabric on a workbench, with more saffron Ankara garments hanging in the background; a saffron-yellow fabric folded on a side table.
**Recommendation:** find or shoot a photo of a Nigerian tailor working with **indigo or adire-patterned cloth** (the actual Yoruba indigo-dye textile our brand references). Background garments should be deeper colors — indigo, sienna, dark earth tones.

---

## Workflow once images are ready

1. Generate / source replacement images using the prompts above.
2. Drop the new files at `~/Desktop/stitchpad_images/` with the same base name as the current asset (e.g. `dashboard_hero_busy.png`).
3. Tell me which ones are ready and I'll:
   - Convert each to webp via `cwebp -q 80`
   - Replace the corresponding file under `composeApp/src/commonMain/composeResources/drawable/`
   - Run build + iOS compile + detekt
   - Commit in one or more batches

Or — defer all of this to PR-C (the originally planned illustration refresh PR) and ship PR-A with the existing saffron-era illustrations remaining. They're functionally fine; just brand-inconsistent.
