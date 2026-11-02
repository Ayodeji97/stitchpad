# Country and phone smoke-test observations — 2 November 2026

Scope: Africa expansion PR #374. Daniel is testing iOS with a Ghana device region.

## Observations and implementation

- Country selection needs a visible close action, search and clearer selection feedback.
  The shared sheet now has these controls, searches localized names, ISO codes and dial codes,
  and animates its selected-row background. Search and visibility belong to the owning ViewModel.
- Edit Profile's separate country chip and floating-label input do not align.
  Onboarding, profile phone/WhatsApp and customer phone now share `CountryPhoneField`:
  one outlined control, one baseline, an external label and supporting text below.
- Customer phone should show its country. The form defaults to the tailor's country,
  allows a per-customer override and saves complete national numbers with an international prefix.
  This override never changes the workshop country/currency. Explicit supported international
  prefixes are detected on input and when editing existing customers.
- Daniel requested automatic region selection. New-account onboarding retains device-region
  preselection; existing accounts retain their saved country. New customers inherit the account
  country unless manually overridden or their explicit number provides another prefix.
- A generated visual proposal was shown before implementation. It is a concept, not runtime QA.

## Owner verification on the rebuilt app

- [ ] Ghana region preselects Ghana/+233 on fresh signup.
- [ ] Country sheet closes with X without changing selection.
- [ ] Search `Ghana`, `GH` and `233`; clear search and try an unmatched query.
- [ ] Selected country is highlighted; select a different country and reopen the sheet.
- [ ] Profile and customer phone controls align at normal and enlarged text sizes.
- [ ] Profile shows +233 only once for a saved +233 number; editing/deleting digits keeps the caret usable.
- [ ] Ghana customer `24 555 1234` saves and opens WhatsApp at +233245551234.
- [ ] Pasting +254712345678 selects Kenya for that customer without changing workshop country.
- [ ] Repeat existing Nigerian-account regression checks from PR #374.

These local changes require review and a fresh smoke test before merging; they have not been pushed.
