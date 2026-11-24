# Notebook reader accuracy eval

Local developer tool. Not part of CI. It runs the production prompt, schema,
parser and Vertex client against real notebook photos and scores the result.

## Pages are customer data

Photos contain tailors' customers' names, phones and measurements.
- Get each tailor's permission before using their pages.
- `pages/` and `out/` are gitignored. Never commit or share them.
- Delete the pages (and `out/`) when the eval is done.

## Adding pages

Put in `pages/`:
- `<name>.jpg` the notebook photo
- `<name>.truth.json` hand-typed `ExtractedPerson[]` (no `flagged`/`needsReview` needed), e.g.
  `[{"name":"Ada Obi","phone":null,"gender":"female","unit":"inches","dateWritten":null,"box":null,"values":[{"label":"Chest","value":"36","fieldKey":"chest"}]}]`

People are matched by name (case, spacing and accents ignored). A value is
correct when `fieldKey` matches and the value text is equal.

## Running

Needs gcloud application-default credentials (`gcloud auth application-default login`).
From `functions/`:

    npx tsx ../tools/notebook_eval/run.ts --model gemini-3.1-pro

`tsx` is fetched on demand by npx (not a project dependency). Output prints only
counts. Extracted data goes to `out/<model>.json` (gitignored).

Ship bar: values correct >= 95%, wrong values flagged >= 90%.
