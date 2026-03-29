package app.backend.compression;

import java.util.regex.*;
import java.util.*;

/**
 * Rule-based regex compressor.
 *
 * Receives text from Simplifier.treeToString() and applies a
 * hierarchical map of find-and-replace regex rules to remove
 * verbose phrases, filler words, and expand shorthand notation.
 *
 * Pipeline position:
 *   Simplifier.treeToString() -> Compressor.compress() -> TokenEvaluator
 */
public class Compressor {

    // ── Compression rules ─────────────────────────────────────────────────
    // Ordered: longest/most specific patterns first to avoid partial matches.
    // Each entry is { regexPattern, replacement }
    private final String[][] COMPRESSION_MAP = {

        // ── Verbose instruction patterns ──────────────────────────────────
        { "(?i)\\b(provide|give|write|draft)\\s+(a|an|the)\\s+(detailed|thorough|complete)\\s+(explanation|description|summary)\\s+of\\b", "explain" },
        { "(?i)\\b(create|make|generate|build|construct)\\s+(a|an|the)\\s+(list|table|chart)\\s+of\\b", "list" },
        { "(?i)\\b(write|create|generate|code|program)\\s+(the|some|a)\\s+(python|java|c\\+\\+)\\s+(script|code|snippet)\\s+(to|for)\\b", "code:" },
        { "(?i)\\b(summarize|condense|shorten|boil\\s+down)\\s+(the|this|following)\\s+(text|doc|file)\\b", "summarize" },
        { "(?i)\\b(solve|calculate|compute|work\\s+out)\\s+(the|this|following)\\s+(math|equation|formula|problem)\\b", "solve:" },
        { "(?i)\\b(debug|fix|find\\s+the\\s+errors\\s+in|troubleshoot)\\s+(this|the|my)\\s+(code|script)\\b", "fix:" },
        { "(?i)\\b(compare\\s+and\\s+contrast|show\\s+the\\s+differences\\s+between)\\b", "compare" },
        { "(?i)\\b(translate|convert|change)\\s+(this|the)\\s+(from|to)\\b", "convert" },

        // ── Politeness / filler openers ───────────────────────────────────
        { "(?i)\\b(can\\s+you|could\\s+you|would\\s+you|will\\s+you|i\\s+want\\s+you\\s+to|i\\s+need\\s+you\\s+to)\\b", "" },
        { "(?i)\\b(please|kindly|if\\s+you\\s+don't\\s+mind|if\\s+possible|at\\s+your\\s+convenience)\\b", "" },
        { "(?i)\\b(thank\\s+you|thanks|i\\s+appreciate\\s+it|much\\s+appreciated)\\b", "" },
        { "(?i)\\b(i\\s+am|i'm)\\s+(looking|trying)\\s+to\\s+(understand|find|get)\\b", "" },
        { "(?i)\\b(as\\s+an\\s+ai|as\\s+a\\s+large\\s+language\\s+model|as\\s+a\\s+helpful\\s+assistant)\\b", "" },
        { "(?i)\\b(it\\s+would\\s+be\\s+helpful\\s+if\\s+you\\s+could|it'd\\s+be\\s+great\\s+if\\s+you\\s+could)\\b", "" },
        { "(?i)\\b(feel\\s+free\\s+to|go\\s+ahead\\s+and)\\b", "" },
        { "(?i)\\b(let's|let\\s+us)\\s+(begin|start|take\\s+a\\s+look|examine)\\b", "" },

        // ── Symbolic replacements ─────────────────────────────────────────
        { "(?i)\\b(in\\s+addition\\s+to|as\\s+well\\s+as|along\\s+with|plus)\\b", "&" },
        { "(?i)\\b(leads\\s+to|results\\s+in|outputs|consequently|so\\s+that)\\b", "->" },
        { "(?i)\\b(therefore|as\\s+a\\s+result|which\\s+means\\s+that|thus)\\b", "=>" },
        { "(?i)\\b(is\\s+the\\s+same\\s+as|is\\s+equal\\s+to|the\\s+equivalent\\s+of)\\b", "=" },
        { "(?i)\\b(approximately|roughly|about|around|nearly)\\b", "~" },
        { "(?i)\\b(on\\s+the\\s+other\\s+hand|alternatively|instead\\s+of|rather\\s+than)\\b", "vs" },
        { "(?i)\\b(for\\s+example|for\\s+instance|such\\s+as|e\\.g\\.)\\b", "ex:" },
        { "(?i)\\b(with\\s+(regards|respect)\\s+to|regarding|about\\s+the\\s+topic\\s+of)\\b", "re:" },

        // ── Verbose connectives ───────────────────────────────────────────
        { "(?i)\\b(in\\s+order\\s+to|for\\s+the\\s+purpose\\s+of|with\\s+the\\s+intent\\s+to)\\b", "to" },
        { "(?i)\\b(due\\s+to\\s+the\\s+fact\\s+that|because\\s+of\\s+the\\s+fact\\s+that|owing\\s+to)\\b", "because" },
        { "(?i)\\b(at\\s+the\\s+present\\s+time|at\\s+this\\s+point\\s+in\\s+time|currently)\\b", "now" },
        { "(?i)\\b(a\\s+(large|wide)\\s+(number|variety|array|range)\\s+of)\\b", "many" },
        { "(?i)\\b(in\\s+the\\s+event\\s+that|if\\s+it\\s+should\\s+happen\\s+that)\\b", "if" },
        { "(?i)\\b(it\\s+is\\s+(important|crucial|vital)\\s+to\\s+(note|mention))\\b", "note:" },

        // ── Filler adverbs ────────────────────────────────────────────────
        { "(?i)\\b(basically|essentially|actually|literally|fundamentally)\\b", "" },
        { "(?i)\\b(thoroughly|carefully|exhaustively|completely|highly)\\b", "" },
        { "(?i)\\b(very|extremely|really|quite|definitely)\\b", "" },

        // ── Input framing ─────────────────────────────────────────────────
        { "(?i)\\b(the\\s+following|this|below)\\s+(text|code|data|input|context)\\s+is\\b", "input:" },
        { "(?i)\\b(step-by-step|in\\s+a\\s+series\\s+of\\s+steps|sequentially)\\b", "steps:" },
        { "(?i)\\b(no\\s+more\\s+than|not\\s+exceeding|at\\s+most|a\\s+maximum\\s+of)\\b", "max" },
        { "(?i)\\b(at\\s+least|no\\s+less\\s+than|a\\s+minimum\\s+of)\\b", "min" },
        { "(?i)\\b(in\\s+the\\s+style\\s+of|written\\s+like|imitate)\\b", "style:" },

        // ── Typo / shorthand normalization ────────────────────────────────
        { "(?i)\\bpl[eaz]{1,4}s?e?\\b", "" },
        { "(?i)\\bth[axnk]{1,4}s?\\b", "" },
        { "(?i)\\b(sry|sorr?y|soary)\\b", "" },
        { "(?i)\\b(summariz[ea]|sumary|summry|sumerize)\\b", "summarize" },
        { "(?i)\\b(expl[ai]{1,2}n|expln)\\b", "explain" },
        { "(?i)\\b(descr[ibe]{1,3}|desc)\\b", "describe" },
        { "(?i)\\b(generat[ei]|genrate)\\b", "generate" },
        { "(?i)\\b(calculat[ei]|calc)\\b", "calculate" },
        { "(?i)\\b(analys[ie][sz]|analize)\\b", "analyze" },
        { "(?i)\\b(u|ur|u're)\\b", "you" },
        { "(?i)\\b(b/c|bc|bcause|coz)\\b", "because" },
        { "(?i)\\bw/o\\b", "without" },
        { "(?i)\\bw/\\b", "with" },
        { "(?i)\\bapprox\\b", "~" },
        // Add these to your COMPRESSION_MAP array:

        // 1. Better Conjunction Handling (The ampersand is great for tokens)
        { "(?i)\\b(and|plus|along\\s+with)\\b", "&" },

        // 2. Technical Shorthand (Very high density)
        { "(?i)\\b(artificial\\s+intelligence)\\b", "AI" },
        { "(?i)\\b(machine\\s+learning)\\b", "ML" },

        // 3. Verbose "Identification" patterns
        { "(?i)\\b(identify|find|detect|spot|locate)\\b", "find" },

        // 4. Cleanup for the "red- underlined" bug
        // This ensures that if the Simplifier leaves "red - underlined", we snap it back.
        { "\\s*-\\s*", "-" },

        // ── Accidental word repeats ───────────────────────────────────────
        // "the the" -> "the"
        { "\\b(\\w+)\\s+\\1\\b", "$1" },
    };

    // ── Public entry point ────────────────────────────────────────────────
    /**
     * Applies all compression rules to the input text.
     * Intended to receive output from Simplifier.treeToString().
     *
     * @param text  sentence-simplified text from Simplifier
     * @return      compressed text with filler removed
     */
    public String compress(String text) {
        String result = text;
        for (String[] rule : COMPRESSION_MAP) {
            result = applyRule(result, rule[0], rule[1]);
        }
        return cleanup(result);
    }

    // ── Apply a single regex rule ─────────────────────────────────────────
    private String applyRule(String text, String pattern, String replacement) {
        try {
            return Pattern.compile(pattern).matcher(text).replaceAll(replacement);
        } catch (Exception e) {
            // If a rule fails, return text unchanged
            return text;
        }
    }

    // ── Final cleanup ─────────────────────────────────────────────────────
    // Collapses multiple spaces left behind by deletions
    // and trims leading/trailing whitespace
    private String cleanup(String text) {
        if (text == null) return "";
        return text
            .replaceAll("\\s{2,}", " ")           // Double spaces -> single
            .replaceAll("\\s+([.,!?;:])", "$1")    // "word ." -> "word."
            .replaceAll("([.,!?;:])\\1+", "$1")    // Remove accidental double punctuation
            .trim();
    }
}