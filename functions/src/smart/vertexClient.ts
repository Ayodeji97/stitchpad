import { VertexAI } from '@google-cloud/vertexai';

const PROJECT_ID = 'stitchpad-30607';
const LOCATION = 'europe-west1';
// gemini-2.0-flash-001 is not yet available in europe-west1 — the project
// hits a 404 when trying to invoke it there. gemini-1.5-flash-002 has full
// europe-west1 GA coverage and produces equivalent-quality short drafts.
const MODEL_ID = 'gemini-1.5-flash-002';

/**
 * Thin interface over the Vertex AI SDK so the draftMessage handler can be
 * tested with a fake client (no real LLM calls in CI — cost + flake).
 */
export interface VertexClient {
  generateText(args: { systemPrompt: string; userPrompt: string }): Promise<string>;
}

/**
 * Production client using the Vertex AI SDK. Initialized lazily on first
 * use so module load doesn't pay the SDK setup cost.
 */
let cachedClient: VertexClient | null = null;

export function getVertexClient(): VertexClient {
  if (cachedClient !== null) return cachedClient;

  const vertex = new VertexAI({ project: PROJECT_ID, location: LOCATION });
  const model = vertex.getGenerativeModel({
    model: MODEL_ID,
    generationConfig: { temperature: 0.7, maxOutputTokens: 200 },
  });

  cachedClient = {
    async generateText({ systemPrompt, userPrompt }) {
      const response = (
        await model.generateContent({
          contents: [
            { role: 'user', parts: [{ text: userPrompt }] },
          ],
          systemInstruction: { role: 'system', parts: [{ text: systemPrompt }] },
        })
      ).response;

      const candidates = response.candidates ?? [];
      if (candidates.length === 0) {
        throw new Error('vertex_no_candidates');
      }
      const text = candidates[0]?.content?.parts?.[0]?.text;
      if (!text || text.trim().length === 0) {
        throw new Error('vertex_empty_text');
      }
      return text.trim();
    },
  };

  return cachedClient;
}

/**
 * Reset the cached client. Used by tests; not exported via the package
 * boundary in production code.
 */
export function __resetVertexClientForTests(): void {
  cachedClient = null;
}
