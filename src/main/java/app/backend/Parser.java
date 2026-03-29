package app.backend;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
//import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;

import java.util.*;

/**
 * Owns the CoreNLP pipeline and converts raw text into syntax trees.
 *
 * Separates parsing concerns from transformation concerns:
 *   Parser    — text -> List<Tree>  (CoreNLP)
 *   Simplifier — List<Tree> -> String (Tregex/Tsurgeon)
 *   Compressor — String -> String   (regex map)
 *
 * Initialize once at app startup — CoreNLP takes 3-5 seconds to load.
 * Reuse the same Parser instance for every compression call.
 *
 * Usage:
 *   Parser parser = new Parser();
 *   List<Tree> trees = parser.parse("Could you explain neural networks?");
 *   String simplified = simplifier.simplify(trees);
 *   String compressed = Compressor.compress(simplified);
 */
public class Parser {

    private final StanfordCoreNLP pipeline;

    // ── Constructor ───────────────────────────────────────────────────────
    /**
     * Initializes the CoreNLP pipeline with only the annotators needed
     * for constituency parsing. Deliberately excludes NER, coref, and
     * other expensive annotators that are not needed for Tregex/Tsurgeon.
     *
     * Annotator chain:
     *   tokenize  — splits text into tokens (words, punctuation)
     *   ssplit    — splits tokens into sentences
     *   pos       — assigns part-of-speech tags (NN, VB, JJ, etc.)
     *   parse     — builds constituency parse tree from POS-tagged tokens
     */
    public Parser() {
        System.out.println("Parser CONSTRUCTED!!!");

        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,parse");
        props.setProperty("parse.model",
            "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
        // Suppress CoreNLP's verbose logging
        props.setProperty("annotators.verbose", "false");
        this.pipeline = new StanfordCoreNLP(props);
    }

    // ── Public entry point ────────────────────────────────────────────────
    /**
     * Parses raw text into a list of constituency trees, one per sentence.
     *
     * @param text  raw user prompt
     * @return      list of parse trees, one per sentence
     */
    public List<Tree> parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Annotation doc = new Annotation(text.trim());
        pipeline.annotate(doc);

        List<Tree> trees = new ArrayList<>();
        for (CoreMap sentence : doc.get(SentencesAnnotation.class)) {
            Tree tree = sentence.get(TreeAnnotation.class);
            if (tree != null) {
                trees.add(tree);
            }
        }
        System.out.println("TREES: " + trees.size());
        for (Tree t : trees) System.out.println(t);
        return trees;
    }

    // ── Utility: convert a single tree back to plain text ─────────────────
    /**
     * Reconstructs a plain text string from a parse tree's leaf nodes.
     * Used by Simplifier after Tregex/Tsurgeon transforms the tree.
     *
     * @param tree  transformed parse tree
     * @return      plain text string
     */
    public static String treeToString(Tree tree) {
    if (tree == null) return "";

    List<Label> leaves = tree.yield();
    if (leaves == null || leaves.isEmpty()) return "";

    // Add this check
    if (leaves.size() == 0) return "";

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < leaves.size(); i++) {
        String word = leaves.get(i).value();
        if (word == null || word.isEmpty()) continue;
        if (i > 0 && !word.matches("[.,!?;:)'\"\\]%-]")) {
            sb.append(" ");
        }
        sb.append(word);
    }

    String result = sb.toString().trim();
    if (!result.isEmpty()) {
        result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }
    return result;
}
}