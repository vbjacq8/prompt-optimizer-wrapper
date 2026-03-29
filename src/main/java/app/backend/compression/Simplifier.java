package app.backend.compression;

import app.backend.Parser;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.trees.tregex.tsurgeon.*;

import java.util.*;

/**
 * LLM-oriented prompt compressor using Tregex/Tsurgeon.
 *
 * Rules are derived from actual CoreNLP parse tree output.
 * Each rule is annotated with the exact tree structure it targets.
 *
 * Pipeline position:
 *   Parser.parse() -> Simplifier.simplify() -> Compressor.compress()
 */
public class Simplifier {

    /* 
    private static final String[][] RULES = {

        // ── Rule 1: "I was wondering if you could X" -> X ─────────────────
        // Actual tree:
        // ROOT < S < NP(I) < VP(was) < VP(wondering) < SBAR(if) < S < VP(could) < VP=content
        {
            "ROOT=root "
          + "< (S < (NP < (PRP < /(?i)^i$/)) "
          + "  < (VP < (VBD < /(?i)^was$/) "
          + "    < (VP < (VBG < /(?i)^wondering$/) "
          + "      < (SBAR "
          + "        < (S < (VP < MD < VP=content))))))",
            "replace root content"
        },

        // ── Rule 2: "Could you X" at root level -> X ──────────────────────
        // Actual tree:
        // ROOT < SQ < MD < NP(you) < VP=content
        {
            "ROOT=root < (SQ < MD < (NP < (PRP < /(?i)^you$/)) < VP=content)",
            "replace root content"
        },

        // ── Rule 3: "It is important to note that X" -> X ─────────────────
        // Actual tree:
        // ROOT < S < NP(It) < VP(is) < ADJP < S < VP(note) < SBAR(that) < S=inner
        {
            "ROOT=root "
          + "< (S < (NP < (PRP < /(?i)^it$/)) "
          + "  < (VP < (VBZ|VBP) "
          + "    < (ADJP "
          + "      < (S < (VP "
          + "        < (SBAR < (IN < /(?i)^that$/) < S=inner))))))",
            "replace root inner"
        },

        // ── Rule 4: "There are X" -> X ────────────────────────────────────
        // Actual tree:
        // ROOT < S < NP(EX there) < VP(is|are) < NP=content
        {
            "ROOT=root "
          + "< (S < (NP < (EX < /(?i)^there$/)) "
          + "  < (VP < (VBZ|VBP|VBD) < NP=content))",
            "replace root content"
        },

        // ── Rule 5: "I am trying/looking to X" -> X ───────────────────────
        // Actual tree:
        // ROOT < S < NP(I) < VP(am) < VP(VBG) < VP=content
        {
            "ROOT=root "
          + "< (S < (NP < (PRP < /(?i)^i$/)) "
          + "  < (VP < (VBP|VBZ < /(?i)^am|are$/) "
          + "    < (VP < VBG < VP=content)))",
            "replace root content"
        },

        // ── Rule 6: "What I want is X" -> X ──────────────────────────────
        // Actual tree:
        // ROOT < S < SBAR(WHNP(what) < S) < VP(is) < NP=content
        {
            "ROOT=root "
          + "< (S < (SBAR < (WHNP < WP) < S) "
          + "  < (VP < (VBZ|VBP < /(?i)^is|are$/) < NP=content))",
            "replace root content"
        },

        // ── Rule 7: "I would like/want/need you to X" -> X ───────────────
        // Actual tree (observed):
        // ROOT < S < ... < S < NP(I) < VP(MD would) < ADVP? < VP(like) < S < NP(you) < VP(TO) < VP=content
        // Targets the inner coordinated S containing "I would like you to"
        {
            "S=root "
          + "< (NP < (PRP < /(?i)^i$/)) "
          + "< (VP < MD "
          + "  < (VP < (VB < /(?i)^like|want|need$/) "
          + "    < (S < (NP < (PRP < /(?i)^you$/)) "
          + "      < (VP < TO < VP=content))))",
            "replace root content"
        },





        // ── Rule 8: Delete filler adverbs (RB) anywhere in tree ───────────
        // Targets: really, very, quite, basically, etc.
        // Deletes the RB leaf node directly — works inside any parent
        //{
        //    "VP|ADJP|S|ADVP|ROOT "
        //  + "< (RB=filler "
        //  + "  < /(?i)^(very|really|quite|extremely|basically|essentially|"
        //  + "actually|literally|simply|rather|somewhat|fairly|also|additionally)$/)",
        //    "delete filler"
        //},

        // ── Rule 9: Delete empty ADVP left behind after Rule 8 ────────────
        // After deleting the RB, the ADVP parent may be empty
        //{
        //    "VP|S|ROOT < (ADVP=empty !< __)",
        //    "delete empty"
        //},

        // ── Rule 10: "Because/Although X, Y" -> Y ────────────────────────
        // Actual tree:
        // S=root < SBAR(IN(because) ...) < /,/ < S=main

        {
            "S=root "
          + "< (SBAR=sub "
          + "  < (IN < /(?i)^(because|although|while|since|unless|whenever)$/) "
          + "  $+ /,/=comma) "
          + "< S=main",
            "replace root main"
        },

        // ── Rule 11: "please" as verb -> unwrap VP content ────────────────
        // CoreNLP tags "please" as VB inside VP when used as politeness marker
        // Actual tree: VP < VB(please) < VP=content
        {
            "VP=root < (VB=p < /(?i)^please$/) < VP=content",
            "replace root content"
        },


        //Gemini suggestions

        // ── Rule 8: The "Fluff" Killer (CRITICAL FOR ML EFFICIENCY) ──────────
    {
        "RB=filler < /(?i)^(very|really|quite|basically|actually|literally|simply|just|maybe)$/",
        "delete filler"
    },

    // ── Rule 12: "I want you to X" -> X ──────────────────────────────────
    {
        "S=root < (NP < (PRP < /(?i)^i$/)) < (VP < (VBP < /(?i)^want|need$/) < (S < (VP < TO < VP=content)))",
        "replace root content"
    },

    // ── Rule 13: "I think that X" -> X ──────────────────────────────────
    {
        "S=root < (NP < (PRP < /(?i)^i$/)) < (VP < (VBP < /(?i)^think|believe|feel$/) < (SBAR < (IN < that) < S=content))",
        "replace root content"
    }

    };*/

    /* 
    private static final String[][] RULES = {
    // 1. Politeness Lead-ins (High priority)
    {
        "ROOT=root < (S < (VP < (VB < /(?i)please/) < VP=content))", 
        "replace root content"
    },

    // 2. I want you to -> [Verb]
    {
        "S=root < (NP < (PRP < /(?i)^i$/)) < (VP < (VBP < /(?i)^want|need$/) < (S < (VP < TO < VP=content)))",
        "replace root content"
    },

    // 3. SAFE Infinitive Stripper (Deletes 'to' instead of replacing VP)
    {
        "VP < TO=to",
        "delete to"
    },

    // 4. Relative Clause Killer (which/that is/are)
    {
        "SBAR=root [ < (WHNP < WDT|WP) | < (IN < that) ] < (S < (VP < (VBP|VBZ|VBD|VBS < /(?i)is|are|was|were/) < VP=content))",
        "replace root content"
    },

    // 5. Fact-heavy Relative Clause (suggestions, which can be accepted -> suggestions, accepted)
    {
        "SBAR=root < (WHNP < WDT|WP) < (S < (VP < (MD|VBP|VBZ) < (VP < (VBN|VB) < VP=content)))",
        "replace root content"
    },

    // 6. Adverb/Filler Pruning (The "Fluff" Killer)
    {
        "RB=filler < /(?i)^(very|really|quite|basically|actually|simply|just|fairly|rather|automatically)$/",
        "delete filler"
    }
};
    */

    /* 
    private static final String[][] RULES = {
    // 1. Politeness/Imperative stripping (for user prompts)
    { "ROOT=root < (S < (VP < (VB < /(?i)please/) < VP=content))", "replace root content" },
    { "S=root < (NP < (PRP < /(?i)^i$/)) < (VP < (VBP < /(?i)^want|need$/) < (S < (VP < TO < VP=content)))", "replace root content" },

    // 2. Infinitives & Relatives (The "To/Which/That" Killer)
    { "VP < TO=to", "delete to" },
    { "SBAR=root [ < (WHNP < WDT|WP) | < (IN < that) ] < (S < (VP < (MD|VBP|VBZ) < (VP < (VBN|VB) < VP=content)))", "replace root content" },
    { "SBAR=root [ < (WHNP < WDT|WP) | < (IN < that) ] < (S < (VP < (VBP|VBZ|VBD|VBS < /(?i)is|are|was|were/) < VP=content))", "replace root content" },

    // 3. The "Fluff" Adverb Stripper
    { "RB=filler < /(?i)^(very|really|quite|basically|actually|simply|just|fairly|rather|automatically|stylistically)$/", "delete filler" },

    // 4. ARTICLE STRIPPER (Massive token savings)
    { "DT=article < /(?i)^(the|a|an)$/", "delete article" },

    // 5. THE CLEANUP CREW (Prevents the "ADVP" or "SBAR" text bug)
    // This MUST come after rules that delete children.
    { "ADVP|SBAR|PP|S=empty !< __", "delete empty" }
};
    */
    

    
    private static final String[][] RULES = {
    // 1. THE "POLITENESS" PRUNER (Safe: Logic is always in the Verb)
    { "ROOT=root < (S < (VP < (VB < /(?i)please/) < VP=content))", "replace root content" },

    // 2. THE "SVO" ANCHOR (Safe: Jumps straight to the Action)
    { "S=root < (NP < (PRP < /(?i)^i$/)) < (VP < (VBP < /(?i)^want|need$/) < (S < (VP < TO < VP=content)))", "replace root content" },

    // 3. SMART INFINITIVE STRIPPER (Safer Version)
    // Only strip 'to' if it's followed by a Verb (VB), never a Noun (NP).
    // "to identify" (Delete) vs "to the office" (Keep)
    { "VP < (TO=to $+ VP)", "delete to" },

    // 4. THE COPULA FLATTENER (Very Safe: Removes the 'is/are' bridge)
    { "SBAR=root [ < (WHNP < WDT|WP) | < (IN < that) ] < (S < (VP < (VBP|VBZ|VBD|VBS < /(?i)is|are|was|were/) < VP=content))", "replace root content" },

    // 5. RELATIONAL GUARD (NEW: The "Who-Does-What" Protector)
    // We explicitly KEEP prepositions (IN) that follow a Verb and precede a Noun
    // because they define the "Path" of the action. 
    // This rule doesn't transform; it acts as a "Stop" for other deletions.
    { "PP <<: (IN < /(?i)^(by|for|from|with|to)$/)", "prune" }, // 'prune' here is a placeholder for 'ignore'

    // 6. SELECTIVE ARTICLE STRIPPER
    // Strip articles from common nouns, but keep them if they are part of a specific title.
    { "NP < (DT=article < /(?i)^(the|a|an)$/) < NN|NNS", "delete article" },

    // 7. THE ARTIFACT CLEANUP (The "ADVP" Fixer)
    { "ADVP|SBAR|PP|S|VP|NP=empty !< __", "delete empty" },

    // Rule: Protect Dialogue Labels (A: , B: , User: , Assistant:)
    // This rule finds a Colon preceded by a short NP and ensures they stay together
    // or are deleted together.
    {
        "ROOT < (S=label < (NP < (NN|NNP < /^[A-Z][a-z]?$/)) $+ (PUNC < /:/))",
        "replace root label" 
}
};

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

    private Tree applyAllRules(Tree tree) {
        for (String[] rule : RULES) {
            if (tree == null || tree.yield() == null || tree.yield().isEmpty()) {
                return tree;
            }
            tree = applyRule(tree, rule[0], rule[1]);
        }
        return tree;
    }

    private Tree applyRule(Tree tree, String tregexPattern, String tsurgeonOp) {
        try {
            TregexPattern pattern     = TregexPattern.compile(tregexPattern);
            TsurgeonPattern operation = Tsurgeon.parseOperation(tsurgeonOp);
            Tree result = Tsurgeon.processPattern(pattern, operation, tree);
            if (!result.toString().equals(tree.toString())) {
                System.out.println("RULE FIRED: " + tsurgeonOp);
                System.out.println("BEFORE: " + tree);
                System.out.println("AFTER:  " + result);
            }
            return result;
        } catch (Exception e) {
            System.out.println("RULE ERROR: " + tsurgeonOp + " — " + e.getMessage());
            return tree;
        }
    }
}