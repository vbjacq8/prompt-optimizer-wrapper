package app.backend.compression;

import app.backend.Parser;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.trees.tregex.tsurgeon.*;

import java.util.*;

/**
 * LLM-oriented prompt compressor using Tregex/Tsurgeon.
 *
 * Receives parsed Trees from Parser.java and applies structural
 * transformation rules to remove verbose grammatical patterns.
 *
 * Pipeline position:
 *   Parser.parse() -> Simplifier.simplify() -> Compressor.compress()
 *
 * Rules:
 *   1. Indirect request extraction     "I was wondering if you could X" -> "X"
 *   2. Politeness modal stripping      "Could you X"                    -> "X"
 *   3. Expletive it removal            "It is important to note that X" -> "X"
 *   4. There-construction collapse     "There are X that Y"             -> "X Y"
 *   5. Redundant gerund unwrapping     "I am looking for info about X"  -> "X"
 *   6. Pseudo-cleft flattening         "What I want is X"               -> "X"
 *   7. Desire/need verb unwrapping     "I want you to X"                -> "X"
 *   8. Filler adverb pruning           "very", "really", "quite", etc.  -> ""
 *   9. In-order-to removal             "in order to X"                  -> "X"
 *  10. Fronted subordinate removal     "Because X, Y"                   -> "Y"
 */
public class Simplifier {

    // ── Rule table ────────────────────────────────────────────────────────
    private static final String[][] RULES = {

        // Rule 1: Indirect request extraction
        {
            "S=root < (NP < (PRP < /(?i)i/)) "
          + "< (VP < (VB|VBD|VBP|VBZ < /(?i)wonder|ask|curious|inquir/) "
          + "< (SBAR=content < S=inner))",
            "replace root inner"
        },

        // Rule 2: Politeness modal stripping
        {
            "S=root < (NP < (PRP < /(?i)you/)) "
          + "< (VP=vp < MD < VP=content)",
            "replace root content"
        },

        // Rule 3: Expletive it removal
        {
            "S=root < (NP < (PRP < /(?i)it/)) "
          + "< (VP < (VBZ|VBP|MD) "
          + "< (SBAR=content < (IN < /(?i)that/) < S=inner))",
            "replace root inner"
        },

        // Rule 4: There-construction collapse
        {
            "S=root < (NP < (EX < /(?i)there/)) "
          + "< (VP < (VBZ|VBP|VBD) < NP=content)",
            "replace root content"
        },

        // Rule 5: Redundant gerund unwrapping
        {
            "S=root < (NP < (PRP < /(?i)i/)) "
          + "< (VP < (VBP|VBZ < /(?i)am|is/) "
          + "< (VP < (VBG < /(?i)look|try|attempt|seek|hope|wish/) "
          + "< (PP=content|VP=content)))",
            "replace root content"
        },

        // Rule 6: Pseudo-cleft flattening
        {
            "S=root < (SBAR < (WHNP < WP) < S) "
          + "< (VP < (VBZ|VBP < /(?i)is|are/) "
          + "< (NP=content|S=content|VP=content))",
            "replace root content"
        },

        // Rule 7: Desire/need verb unwrapping
        {
            "S=root < (NP < (PRP < /(?i)i/)) "
          + "< (VP < (VB|VBP|VBD < /(?i)want|need|like|require|ask/) "
          + "< (NP < (PRP < /(?i)you/)) "
          + "< (S=content < VP=inner))",
            "replace root inner"
        },

        // Rule 8: Filler adverb pruning
        {
            "ADJP|ADVP|VP < "
          + "(RB=filler < /(?i)^(very|really|quite|extremely|basically|"
          + "essentially|actually|literally|simply|just|rather|somewhat|fairly)$/)",
            "delete filler"
        },

        // Rule 9: "In order to" removal
        {
            "PP=root < (IN < /(?i)in/) "
          + "< (NP < (NN < /(?i)order/) < (S=content < (VP < TO)))",
            "replace root content"
        },

        // Rule 10: Fronted subordinate clause removal
        {
            "S=root "
          + "< (SBAR=sub < (IN < /(?i)^(because|although|while|since|unless|whenever)$/) "
          + "$+ /,/=comma) "
          + "< S=main",
            "replace root main"
        },
    };

    // ── Public entry point ────────────────────────────────────────────────
    /**
     * Applies Tregex/Tsurgeon compression rules to a list of parse trees
     * produced by Parser.parse().
     *
     * @param trees  list of constituency trees from Parser
     * @return       compressed text with verbose structures removed
     */
    public String simplify(List<Tree> trees) {
        List<String> results = new ArrayList<>();
        for (Tree tree : trees) {
            Tree compressed = applyAllRules(tree);
            String output = Parser.treeToString(compressed);
            if (!output.isEmpty()) {
                results.add(output);
            }
        }
        return String.join(" ", results).trim();
    }

    // ── Apply all rules in sequence ───────────────────────────────────────
    private Tree applyAllRules(Tree tree) {
        for (String[] rule : RULES) {
            tree = applyRule(tree, rule[0], rule[1]);
        }
        return tree;
    }

    // ── Single rule application ───────────────────────────────────────────
    private Tree applyRule(Tree tree, String tregexPattern, String tsurgeonOp) {
        try {
            TregexPattern pattern     = TregexPattern.compile(tregexPattern);
            TsurgeonPattern operation = Tsurgeon.parseOperation(tsurgeonOp);
            return Tsurgeon.processPattern(pattern, operation, tree);
        } catch (Exception e) {
            // If rule fails, return tree unchanged — never crash the pipeline
            return tree;
        }
    }
}