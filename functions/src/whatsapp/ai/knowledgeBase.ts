import * as admin from 'firebase-admin';

/**
 * StitchPad support knowledge base. v1 retrieval is deliberately simple: a
 * curated set of Q&A articles (the `supportKnowledge` Firestore collection),
 * with a keyword/token-overlap prefilter to trim what goes into the prompt.
 * Embeddings/vector search are an explicit later upgrade.
 */

export interface KbArticle {
  id: string;
  category: string;
  question: string;
  answerEn: string;
  /** Optional Nigerian Pidgin answer; falls back to answerEn when absent. */
  answerPcm?: string;
  /** Curated match terms (lowercase). */
  keywords: string[];
}

/** Read seam over the knowledge base — production reads active Firestore docs. */
export interface KbIO {
  loadKnowledge(): Promise<KbArticle[]>;
}

// Common words that carry no retrieval signal — dropped before matching so
// "the app" and "my account" rank on 'app'/'account', not 'the'/'my'.
const STOPWORDS = new Set([
  'the', 'a', 'an', 'i', 'my', 'me', 'is', 'are', 'am', 'to', 'and', 'or', 'of',
  'in', 'on', 'for', 'it', 'this', 'that', 'can', 'cannot', 'cant', 'do', 'does',
  'how', 'what', 'why', 'when', 'with', 'you', 'your', 'we', 'not', 'no',
]);

function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter((t) => t.length > 1 && !STOPWORDS.has(t));
}

/**
 * Returns the articles most relevant to `question`, ranked by how many of the
 * question's tokens hit each article's keywords or question text, capped at
 * `limit`. Returns [] when nothing matches — the answerer treats "no grounding"
 * as a reason to escalate rather than guess.
 */
export function selectRelevant(question: string, articles: KbArticle[], limit = 8): KbArticle[] {
  const tokens = new Set(tokenize(question));
  if (tokens.size === 0) return [];

  const scored = articles.map((article) => {
    const haystack = new Set([
      ...article.keywords.map((k) => k.toLowerCase()),
      ...tokenize(article.question),
    ]);
    let score = 0;
    for (const token of tokens) {
      if (haystack.has(token)) score += 1;
    }
    return { article, score };
  });

  return scored
    .filter((s) => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((s) => s.article);
}

/** Firestore field shape of a `supportKnowledge` doc (snake_case on the wire). */
interface RawKbDoc {
  category?: string;
  question?: string;
  answer_en?: string;
  answer_pcm?: string;
  keywords?: string[];
  active?: boolean;
}

/** Production KbIO — reads the active curated articles each request (so edits in
 * the Firebase console take effect with no deploy). */
export function productionKbIO(db: admin.firestore.Firestore): KbIO {
  return {
    async loadKnowledge() {
      const snap = await db.collection('supportKnowledge').where('active', '==', true).get();
      return snap.docs.map((doc) => {
        const d = doc.data() as RawKbDoc;
        return {
          id: doc.id,
          category: d.category ?? '',
          question: d.question ?? '',
          answerEn: d.answer_en ?? '',
          answerPcm: d.answer_pcm,
          keywords: Array.isArray(d.keywords) ? d.keywords : [],
        };
      });
    },
  };
}
