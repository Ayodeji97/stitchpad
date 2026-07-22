# Feedback Hub — Design

Date: 2026-07-21
Status: Approved, pending build

## Problem

StitchPad is live on both stores and has a WhatsApp community of roughly 85 fashion
designers. There is no standing route for those people to report a bug or suggest
something. The Day-N beta testing forms were per-day, tied to a fixed test script,
and that programme has ended.

Some community members are probably not using the app at all, and we have no way to
learn why. An in-app feedback screen structurally cannot reach them, so the first
version of this is deliberately external.

## Goal

One permanent form, one link, pinned in the WhatsApp community. Low enough friction
that a non-technical tailor will actually complete it on a phone.

## Tool: Tally (free plan)

Google Forms was ruled out. Adding a file-upload question to a Google Form forces
every respondent to sign in to a Google account, with no workaround. That is a hard
wall for a link shared to a WhatsApp group, especially for iOS users. This is the
same constraint that pushed the Day-N forms to collect screenshots via the group
instead, and it is the problem this design exists to solve.

Tally's free plan supports file uploads with no respondent login, 10MB per file
(a phone screenshot is roughly 1 to 3MB) and unlimited responses.

## Fields

Five fields, one screen.

| Field | Required | Notes |
|---|---|---|
| WhatsApp number | Yes | Label: "so we can get back to you about this" |
| Type | Yes | Bug / Idea / Question |
| Device | Yes | Android / iPhone |
| Screenshot | No | Optional. Ideas and questions do not have one |
| What happened? | Yes | Free text |

### Rejected: app version field

Considered and dropped. The app does not display its version anywhere in Settings,
so a user could only find it by digging through system settings. Asking for it would
be asking for something most respondents cannot supply.

Follow-up, separate from this work: add a "Version 1.1.0" row at the bottom of
Settings. `PlatformInfo` already exposes `appVersion` (Android `versionName`,
iOS `CFBundleShortVersionString`) and the delete-account feedback flow already
consumes it, so the row is close to free. It also unblocks the in-app version below.

### Note on the required WhatsApp number

Requiring the number means every report can be followed up, which is the point.
The tradeoff is that attributable feedback is less candid. If submissions arrive as
uniformly polite bug reports with no criticism, the required field is the likely
cause and it can be made optional without changing the link.

## Responses and triage

Responses stay in Tally, with an email notification per submission to
support@getstitchpad.com. No Google Sheets sync at this volume. Add one only if the
inbox becomes unworkable.

A feedback bucket with no reading routine is a dead bucket. One pass per week, folded
into the existing weekly train:

- Bugs get Lane A / Lane B triaged like any other report
- Ideas go to the Notion backlog
- Questions get a WhatsApp reply, and repeated ones become support-KB entries

## Privacy

The form collects WhatsApp numbers, which is personal data collected outside the app.
The Termly privacy policy should cover feedback submissions. This does not affect the
Play Data Safety declaration, which covers only data the app itself collects.

## Future: in-app feedback (V2)

Settings has a "Send feedback" entry that captures device, app version and user
identity automatically, leaving only the description and an optional image. Higher
quality reports, but it needs a full KMP feature plus a store release, and it cannot
reach lapsed users. Deferred until the Tally responses show what kinds of reports
actually arrive, which is also what should shape the prefilled fields.

## Out of scope

- Google Sheets sync
- Auto-routing to Notion or GitHub issues
- In-app feedback screen (see above)
- The Settings version row (small separate PR)
