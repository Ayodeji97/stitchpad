import { GoogleGenAI } from '@google/genai';

const PROJECT_ID = 'stitchpad-30607';
// Agent Platform (rebranded Vertex AI Generative) routes via the `global`
// multi-region endpoint as of 2026. The new `@google/genai` SDK is the
// only path the platform supports — the legacy `@google-cloud/vertexai`
// SDK was retired in mid-2026 and returns HTML 404s for `global`.
const LOCATION = 'global';
const MODEL_ID = 'gemini-3.1-flash-lite';

/**
 * Thin interface over the Gen AI SDK so the draftMessage handler can be
 * tested with a fake client (no real LLM calls in CI — cost + flake).
 */
export interface VertexClient {
  generateText(args: { systemPrompt: string; userPrompt: string; maxOutputTokens?: number }): Promise<string>;
}

let cachedClient: VertexClient | null = null;

export function getVertexClient(): VertexClient {
  if (cachedClient !== null) return cachedClient;

  // Vertex mode — uses Application Default Credentials (the Cloud Functions
  // service account). No API key required.
  const ai = new GoogleGenAI({ vertexai: true, project: PROJECT_ID, location: LOCATION });

  cachedClient = {
    async generateText({ systemPrompt, userPrompt, maxOutputTokens = 200 }) {
      const response = await ai.models.generateContent({
        model: MODEL_ID,
        contents: [{ role: 'user', parts: [{ text: userPrompt }] }],
        config: {
          systemInstruction: systemPrompt,
          temperature: 0.7,
          maxOutputTokens,
        },
      });
      const text = response.text;
      if (!text || text.trim().length === 0) {
        throw new Error('genai_empty_text');
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
