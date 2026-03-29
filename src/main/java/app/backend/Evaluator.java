package app.backend;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Token evaluator using jtokkit — the Java port of OpenAI's tiktoken.
 *
 * Counts tokens in original and compressed text and returns a
 * EvaluationResult containing the counts, savings, and percentage.
 *
 * Uses cl100k_base encoding which is the tokenizer used by:
 *   - Claude (approximation)
 *   - GPT-4
 *   - GPT-3.5-turbo
 *   - text-embedding-ada-002
 *
 * The EncodingRegistry and Encoding are thread-safe and initialized
 * once — reuse the same Evaluator instance for every call.
 *
 * Pipeline position:
 *   Compressor.compress() -> Evaluator.evaluate() -> MainWindow (UI)
 *
 * Usage:
 *   Evaluator evaluator = new Evaluator();
 *   EvaluationResult result = evaluator.evaluate(original, compressed);
 *   System.out.println(result.percent() + "% reduction");
 */
public class Evaluator {

    private final Encoding encoding;

    // ── Constructor ───────────────────────────────────────────────────────
    /**
     * Initializes jtokkit with cl100k_base encoding.
     * Lightweight — no large model files to load unlike CoreNLP.
     */
    public Evaluator() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    // ── Public entry point ────────────────────────────────────────────────
    /**
     * Counts tokens in both strings and returns comparison metrics.
     *
     * @param original    raw user prompt before compression
     * @param compressed  prompt after compression pipeline
     * @return            EvaluationResult with counts, savings, percent
     */
    public EvaluationResult evaluate(String original, String compressed) {
        int originalTokens   = count(original);
        int compressedTokens = count(compressed);
        return new EvaluationResult(original, compressed, originalTokens, compressedTokens);
    }

    // ── Count tokens in a single string ──────────────────────────────────
    /**
     * Returns the token count for a string using cl100k_base encoding.
     * Uses countTokens() which is faster than encode() when you only
     * need the count and not the actual token list.
     *
     * @param text  any string
     * @return      token count
     */
    public int count(String text) {
        if (text == null || text.isEmpty()) return 0;
        return encoding.countTokens(text);
    }

    // ── Result container ──────────────────────────────────────────────────
    /**
     * Immutable result object returned by evaluate().
     * Passed directly to MainWindow to update the stat cards.
     */
    public static class EvaluationResult {
        public final String original;
        public final String compressed;
        public final int originalTokens;
        public final int compressedTokens;
        public double fidelity;
        public double perplexity;

        public EvaluationResult(String original, String compressed,
                                int originalTokens, int compressedTokens) {
            this.original         = original;
            this.compressed       = compressed;
            this.originalTokens   = originalTokens;
            this.compressedTokens = compressedTokens;
        }

        /** Tokens saved by compression */
        public int saved() {
            return originalTokens - compressedTokens;
        }

        /** Percentage reduction, rounded to 1 decimal place */
        public double percent() {
            if (originalTokens == 0) return 0.0;
            return Math.round(((double) saved() / originalTokens) * 1000.0) / 10.0;
        }

        @Override
        public String toString() {
            return String.format(
                "Tokens: %d -> %d | Saved: %d (%.1f%%)",
                originalTokens, compressedTokens, saved(), percent()
            );
        }
    }
}