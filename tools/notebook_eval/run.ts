/**
 * Offline accuracy eval for the notebook page reader. LOCAL ONLY, not CI.
 * Run from functions/:  npx tsx ../tools/notebook_eval/run.ts --model gemini-3.1-pro
 * Uses your gcloud application-default credentials. Prints aggregates only.
 */
import * as fs from 'fs';
import * as path from 'path';
import { getVertexClient } from '../../functions/src/smart/vertexClient';
import { buildSystemPrompt, RESPONSE_SCHEMA } from '../../functions/src/notebook/extractionPrompt';
import { parseExtraction } from '../../functions/src/notebook/parseExtraction';
import { NOTEBOOK_MODEL_ID } from '../../functions/src/notebook/notebookModel';
import type { ExtractedPerson } from '../../functions/src/notebook/types';
import { score, errorsTotal, type EvalScore } from './score';

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const mi = args.indexOf('--model');
  const model = mi >= 0 && args[mi + 1] ? args[mi + 1] : NOTEBOOK_MODEL_ID;
  const safeModel = model.replace(/[^A-Za-z0-9._-]/g, '_');
  const pagesDir = path.join(__dirname, 'pages');
  const outDir = path.join(__dirname, 'out');
  fs.mkdirSync(outDir, { recursive: true });

  const jpgs = fs.existsSync(pagesDir)
    ? fs.readdirSync(pagesDir).filter((f) => /\.jpe?g$/i.test(f)).sort()
    : [];
  if (jpgs.length === 0) {
    console.error(`No pages found in ${pagesDir}. See README.md.`);
    process.exit(1);
  }

  const client = getVertexClient();
  const total: EvalScore = {
    namesCorrect: 0, namesTotal: 0, valuesCorrect: 0, valuesTotal: 0, wrongValues: 0, wrongFlagged: 0,
    missingValues: 0, unmatchedPeople: 0, unmatchedPeopleValues: 0,
  };
  const outputs: Record<string, ExtractedPerson[] | { error: string }> = {};

  for (const file of jpgs) {
    const base = file.replace(/\.jpe?g$/i, '');
    const truthPath = path.join(pagesDir, `${base}.truth.json`);
    if (!fs.existsSync(truthPath)) {
      console.log(`${file}: skipped (no truth file)`);
      continue;
    }
    const truth = JSON.parse(fs.readFileSync(truthPath, 'utf8')) as ExtractedPerson[];
    try {
      const raw = await client.generateJsonFromImage({
        model,
        systemPrompt: buildSystemPrompt(),
        imageBase64: fs.readFileSync(path.join(pagesDir, file)).toString('base64'),
        mimeType: 'image/jpeg',
        responseSchema: RESPONSE_SCHEMA,
      });
      const got = parseExtraction(raw);
      outputs[file] = got;
      const s = score(truth, got);
      for (const k of Object.keys(total) as (keyof EvalScore)[]) total[k] += s[k];
      console.log(`${file}: names ${s.namesCorrect}/${s.namesTotal}, values ${s.valuesCorrect}/${s.valuesTotal}, wrong ${s.wrongValues} (flagged ${s.wrongFlagged}), missing ${s.missingValues}, unmatched people ${s.unmatchedPeople}`);
    } catch (e) {
      outputs[file] = { error: e instanceof Error ? e.name : 'unknown' };
      console.log(`${file}: FAILED (${e instanceof Error ? e.name : 'unknown'})`);
    }
  }

  fs.writeFileSync(path.join(outDir, `${safeModel}.json`), JSON.stringify(outputs, null, 2));

  const valuePct = total.valuesTotal ? (100 * total.valuesCorrect) / total.valuesTotal : 0;
  const errors = errorsTotal(total);
  const flagPct = errors ? (100 * total.wrongFlagged) / errors : null;
  const valuesPass = total.valuesTotal > 0 && valuePct >= 95;
  const flagPass = flagPct === null ? true : flagPct >= 90;
  console.log(`\nModel: ${model}`);
  console.log(`Names:  ${total.namesCorrect}/${total.namesTotal} (unmatched people ${total.unmatchedPeople})`);
  console.log(`Values: ${total.valuesCorrect}/${total.valuesTotal} (${valuePct.toFixed(1)}%)  bar >= 95%: ${valuesPass ? 'PASS' : 'FAIL'}`);
  console.log(`Errors: ${errors} (wrong ${total.wrongValues}, missing ${total.missingValues}, in unmatched people ${total.unmatchedPeopleValues})`);
  console.log(
    flagPct === null
      ? 'Errors flagged: n/a (no errors)'
      : `Errors flagged: ${total.wrongFlagged}/${errors} (${flagPct.toFixed(1)}%)  bar >= 90%: ${flagPass ? 'PASS' : 'FAIL'}`,
  );
  process.exit(valuesPass && flagPass ? 0 : 1);
}

main().catch((e) => {
  console.error(e instanceof Error ? e.name : 'error');
  process.exit(1);
});
