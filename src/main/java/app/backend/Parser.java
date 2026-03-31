package app.backend;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;

import java.util.*;

/**
 * Owns the CoreNLP pipeline and converts raw text into syntax trees.
 *
 * Pipeline position:
 *   Parser.parse() -> Simplifier.simplify() -> Compressor.compress() -> Evaluator.evaluate()
 *
 * Initialize once at app startup — CoreNLP takes 3-5 seconds to load.
 * Reuse the same instance for every compression call.
 */
public class Parser {

    private final StanfordCoreNLP pipeline;

    public Parser() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,parse");
        props.setProperty("parse.model",
            "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
        props.setProperty("annotators.verbose", "false");
        this.pipeline = new StanfordCoreNLP(props);
    }

    /**
     * Parses raw text into a list of constituency trees, one per sentence.
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
            if (tree != null) trees.add(tree);
        }
        return trees;
    }

    /**
     * Reconstructs plain text from a parse tree's leaf nodes.
     * Handles CoreNLP tokenization artifacts:
     *   - `` and '' -> "
     *   - n't, 's, 're contractions attached without space
     *   - punctuation attached without leading space
     */
    public static String treeToString(Tree tree) {
        if (tree == null) return "";
        List<Label> leaves = tree.yield();
        if (leaves == null || leaves.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        boolean insideQuote = false;
        String result;

        for (int i = 0; i < leaves.size(); i++) {
            String word = leaves.get(i).value();
            if (word == null || word.isEmpty()) continue;

        
        boolean isOpenQuote  = word.equals("``") || word.equals("`");
        boolean isCloseQuote = word.equals("''");
        boolean isContraction = word.equals("n't") || word.equals("'s")
                            || word.equals("'re") || word.equals("'ve")
                            || word.equals("'ll") || word.equals("'d")
                            || word.equals("'m");
        boolean isPunct = word.matches("[.,!?;:)\\]%-]") || isContraction;
        if (isOpenQuote) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("\"");
            insideQuote = true;
            continue;
        }

        if (isCloseQuote) {
            sb.append("\"");
            insideQuote = false;
            continue;
        }

        if (sb.length() > 0 && !isPunct && !insideQuote) {
            sb.append(" ");
        }

        sb.append(word);
    }

        result = sb.toString().trim();
        if (!result.isEmpty()) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }
        return result;
        
    }
    
}