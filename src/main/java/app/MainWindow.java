package app;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import app.backend.compression.Compressor;
import app.backend.compression.Simplifier;
import app.backend.Evaluator;
import app.backend.Parser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


public class MainWindow {

    // ── YOUR API KEY ──────────────────────────────────────────────────────
    private static final String GROK_API_KEY_LITERAL = null;

    private static String getApiKey() {
        if (GROK_API_KEY_LITERAL != null && !GROK_API_KEY_LITERAL.isBlank()) return GROK_API_KEY_LITERAL;
        String env = System.getenv("OPENAI_API_KEY");
        return (env != null && !env.isBlank()) ? env : null;
    }

    // ── Utility Objects ───────────────────────────────────────────────────
    private final Parser parser         = new Parser();
    private final Simplifier simplifier = new Simplifier();
    private final Compressor compressor = new Compressor();
    private final Evaluator evaluator   = new Evaluator();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ── Root layout ───────────────────────────────────────────────────────
    private final BorderPane root = new BorderPane();

    // ── Editor panes ──────────────────────────────────────────────────────
    private final TextArea inputArea         = new TextArea();
    private final TextArea outputArea        = new TextArea();
    private final TextArea gptOriginalArea   = new TextArea();
    private final TextArea gptCompressedArea = new TextArea();

    // ── Stat labels ───────────────────────────────────────────────────────
    private final Label statOriginal   = new Label("—");
    private final Label statCompressed = new Label("—");
    private final Label statSaved      = new Label("—");
    private final Label statPercent    = new Label("—");

    // ── GPT Stat labels ───────────────────────────────────────────────────
    private final Label statGptOriginal   = new Label("—");
    private final Label statGptCompressed = new Label("—");
    private final Label statGptSaved      = new Label("—");
    private final Label statGptPercent    = new Label("—");

    // ── Buttons ───────────────────────────────────────────────────────────
    private final Button btnOptimize       = new Button("⚡  OPTIMIZE");
    private final Button btnCopyCompressed = new Button("COPY COMPRESSED");
    private final Button btnCopyOriginal   = new Button("COPY ORIGINAL");
    private final Button btnClear          = new Button("CLEAR");

    // ── Status bar ────────────────────────────────────────────────────────
    private final Label statusLabel = new Label("READY");

    // ── State ─────────────────────────────────────────────────────────────
    private String lastOriginal   = "";
    private String lastCompressed = "";
    private int    gptDoneCount   = 0;

    // ─────────────────────────────────────────────────────────────────────
    public MainWindow() {
        buildUI();
        wireEvents();
    }

    public Parent getRoot() { return root; }

    // ── Build ─────────────────────────────────────────────────────────────
    private void buildUI() {
        root.getStyleClass().add("root-pane");
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildFooter());
        BorderPane.setMargin(root.getTop(),    new Insets(20, 24, 0,  24));
        BorderPane.setMargin(root.getCenter(), new Insets(14, 24, 0,  24));
        BorderPane.setMargin(root.getBottom(), new Insets(12, 24, 20, 24));
    }

    // ── Header ────────────────────────────────────────────────────────────
    private HBox buildHeader() {
        Label title    = new Label("PROMPT OPTIMIZER");
        Label subtitle = new Label("TOKEN COMPRESSION ENGINE");
        title.getStyleClass().add("title");
        subtitle.getStyleClass().add("subtitle");

        VBox titleCol = new VBox(2, title, subtitle);
        statusLabel.getStyleClass().add("status");
        HBox.setHgrow(titleCol, Priority.ALWAYS);

        HBox header = new HBox(titleCol, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    // ── Stat cards row ────────────────────────────────────────────────────
    private HBox buildStatCards() {
    HBox row = new HBox(10,
        statCard("ORIGINAL PROMPT TOKENS",    statOriginal),
        statCard("COMPRESSED PROMPT TOKENS",  statCompressed),
        statCard("PROMPT TOKENS SAVED",       statSaved),
        statCard("PROMPT REDUCTION %",        statPercent)
    );
    row.setPadding(new Insets(12, 0, 0, 0));
    return row;
}

    private HBox buildGptStatCards() {
    HBox row = new HBox(10,
        statCard("ORIGINAL RESPONSE TOKENS",    statGptOriginal),
        statCard("COMPRESSED RESPONSE TOKENS",  statGptCompressed),
        statCard("RESPONSE TOKENS SAVED",       statGptSaved),
        statCard("RESPONSE REDUCTION %",        statGptPercent)
    );
    row.setPadding(new Insets(12, 0, 0, 0));
    return row;
}

    private VBox statCard(String labelText, Label valueLabel) {
    Label lbl = new Label(labelText);
    lbl.getStyleClass().add("card-label");
    valueLabel.getStyleClass().add("card-value");

    VBox card = new VBox(2, lbl, valueLabel);
    card.getStyleClass().add("stat-card");
    
    // CHANGE THIS: Set MinHeight so they can't be squished
    card.setMinHeight(70); 
    card.setPrefHeight(70);
    
    card.setAlignment(Pos.CENTER_LEFT); // Ensure text stays centered
    card.setPadding(new Insets(10));
    
    HBox.setHgrow(card, Priority.ALWAYS);
    card.setMaxWidth(Double.MAX_VALUE);
    return card;
}

    // ── Center ────────────────────────────────────────────────────────────
    private VBox buildCenter() {
    HBox stats    = buildStatCards();
    HBox editors  = buildEditors();
    HBox gptStats = buildGptStatCards(); // Move these above the response row for better UX
    HBox gptRow   = buildGptRow();
    
    // Add spacing (15) between the rows
    VBox center = new VBox(15, stats, editors, gptStats, gptRow);
    
    // Ensure the editors share the vertical space
    VBox.setVgrow(editors, Priority.ALWAYS);
    VBox.setVgrow(gptRow,  Priority.ALWAYS);
    
    return center;
}

    private HBox buildEditors() {
        Label inputLabel = new Label("INPUT PROMPT");
        inputLabel.getStyleClass().add("section-label");
        inputArea.setPromptText("Paste your prompt here...");
        inputArea.getStyleClass().add("editor");
        inputArea.setWrapText(true);
        VBox leftPane = new VBox(6, inputLabel, inputArea);
        VBox.setVgrow(inputArea, Priority.ALWAYS);
        leftPane.setPadding(new Insets(12, 6, 0, 0));

        Label outputLabel = new Label("COMPRESSED PROMPT");
        outputLabel.getStyleClass().add("section-label");
        outputArea.setPromptText("Compressed output will appear here...");
        outputArea.getStyleClass().add("editor");
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        VBox rightPane = new VBox(6, outputLabel, outputArea);
        VBox.setVgrow(outputArea, Priority.ALWAYS);
        rightPane.setPadding(new Insets(12, 0, 0, 6));

        HBox editors = new HBox(leftPane, buildVerticalDivider(), rightPane);
        HBox.setHgrow(leftPane,  Priority.ALWAYS);
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        VBox.setVgrow(editors,   Priority.ALWAYS);
        return editors;
    }

    // ── Groq response row ─────────────────────────────────────────────────
    private HBox buildGptRow() {
        Label gptOrigLabel = new Label("GROQ → ORIGINAL PROMPT");
        gptOrigLabel.getStyleClass().add("section-label");
        gptOriginalArea.setPromptText("Groq's response to your original prompt will appear here...");
        gptOriginalArea.getStyleClass().add("editor");
        gptOriginalArea.setEditable(false);
        gptOriginalArea.setWrapText(true);
        VBox gptLeftPane = new VBox(6, gptOrigLabel, gptOriginalArea);
        VBox.setVgrow(gptOriginalArea, Priority.ALWAYS);
        gptLeftPane.setPadding(new Insets(12, 6, 0, 0));

        Label gptCompLabel = new Label("GROQ → COMPRESSED PROMPT");
        gptCompLabel.getStyleClass().add("section-label");
        gptCompressedArea.setPromptText("Groq's response to your compressed prompt will appear here...");
        gptCompressedArea.getStyleClass().add("editor");
        gptCompressedArea.setEditable(false);
        gptCompressedArea.setWrapText(true);
        VBox gptRightPane = new VBox(6, gptCompLabel, gptCompressedArea);
        VBox.setVgrow(gptCompressedArea, Priority.ALWAYS);
        gptRightPane.setPadding(new Insets(12, 0, 0, 6));

        HBox gptRow = new HBox(gptLeftPane, buildVerticalDivider(), gptRightPane);
        HBox.setHgrow(gptLeftPane,  Priority.ALWAYS);
        HBox.setHgrow(gptRightPane, Priority.ALWAYS);
        VBox.setVgrow(gptRow,       Priority.ALWAYS);
        return gptRow;
    }

    private Region buildVerticalDivider() {
        Region div = new Region();
        div.setPrefWidth(1);
        div.setMinWidth(1);
        div.setMaxWidth(1);
        div.getStyleClass().add("divider-v");
        return div;
    }

    // ── Footer (buttons) ──────────────────────────────────────────────────
    private HBox buildFooter() {
        btnOptimize.getStyleClass().addAll("btn", "btn-primary");
        btnOptimize.setPrefHeight(40);
        btnOptimize.setMaxWidth(Double.MAX_VALUE);

        btnCopyCompressed.getStyleClass().addAll("btn", "btn-secondary");
        btnCopyCompressed.setPrefHeight(40);
        btnCopyCompressed.setDisable(true);

        btnCopyOriginal.getStyleClass().addAll("btn", "btn-secondary");
        btnCopyOriginal.setPrefHeight(40);
        btnCopyOriginal.setDisable(true);

        btnClear.getStyleClass().addAll("btn", "btn-secondary");
        btnClear.setPrefHeight(40);

        HBox.setHgrow(btnOptimize, Priority.ALWAYS);

        HBox footer = new HBox(10,
            btnOptimize, btnCopyCompressed, btnCopyOriginal, btnClear
        );
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }

    // ── Events ────────────────────────────────────────────────────────────
    private void wireEvents() {
        btnOptimize.setOnAction(e -> runOptimization());
        btnCopyCompressed.setOnAction(e -> copyToClipboard(lastCompressed, "COPIED COMPRESSED ✓"));
        btnCopyOriginal.setOnAction(e -> copyToClipboard(lastOriginal,   "COPIED ORIGINAL ✓"));
        btnClear.setOnAction(e -> clearAll());
    }

    private void runOptimization() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        setStatus("PROCESSING...");
        btnOptimize.setDisable(true);
        btnOptimize.setText("OPTIMIZING...");
        outputArea.clear();
        gptOriginalArea.clear();
        gptCompressedArea.clear();

        Task<CompressionResult> task = new Task<>() {
            @Override
            protected CompressionResult call() {
                System.out.println("INPUT: " + text);
                String simplified = simplifier.simplify(parser.parse(text));
                System.out.println("SIMPLIFIED: " + simplified);
                String compressed = compressor.compress(simplified);
                System.out.println("COMPRESSED: " + compressed);
                Evaluator.EvaluationResult evalResult = evaluator.evaluate(text, compressed);
                
                return new CompressionResult(
                    text, compressed,
                    evalResult.originalTokens,
                    evalResult.compressedTokens
                );
            }
        };

        task.setOnSucceeded(e -> {
            CompressionResult r = task.getValue();
            lastOriginal   = r.original;
            lastCompressed = r.compressed;

            outputArea.setText(r.compressed);
            statOriginal.setText(String.format("%,d", r.originalTokens));
            statCompressed.setText(String.format("%,d", r.compressedTokens));
            statSaved.setText(String.format("%,d", r.saved()));
            statPercent.setText(r.percent() + "%");

            btnCopyCompressed.setDisable(false);
            btnCopyOriginal.setDisable(false);

            setStatus("COMPRESSION DONE · QUERYING GROQ...");
            callChatGptParallel(r.original, r.compressed);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            ex.printStackTrace();
            setStatus("ERROR: " + ex.getMessage());
            btnOptimize.setDisable(false);
            btnOptimize.setText("⚡  OPTIMIZE");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ── Groq API ──────────────────────────────────────────────────────────
    private void callChatGptParallel(String original, String compressed) {
        String apiKey = getApiKey();
        if (apiKey == null) {
            String msg = "⚠ No API key found.\n\nSet OPENAI_API_KEY_LITERAL in MainWindow.java.";
            Platform.runLater(() -> {
                gptOriginalArea.setText(msg);
                gptCompressedArea.setText(msg);
                btnOptimize.setDisable(false);
                btnOptimize.setText("⚡  OPTIMIZE");
                setStatus("DONE (no API key)");
            });
            return;
        }

        gptDoneCount = 0;
        Task<String> origTask = buildGptTask(original, apiKey);
        Task<String> compTask = buildGptTask(compressed, apiKey);

        origTask.setOnSucceeded(e -> Platform.runLater(() -> { gptOriginalArea.setText(origTask.getValue());   onOneGptDone(); }));
        origTask.setOnFailed(e ->   Platform.runLater(() -> { gptOriginalArea.setText("Error: " + origTask.getException().getMessage()); onOneGptDone(); }));
        compTask.setOnSucceeded(e -> Platform.runLater(() -> { gptCompressedArea.setText(compTask.getValue()); onOneGptDone(); }));
        compTask.setOnFailed(e ->   Platform.runLater(() -> { gptCompressedArea.setText("Error: " + compTask.getException().getMessage()); onOneGptDone(); }));

        new Thread(origTask) {{ setDaemon(true); }}.start();
        new Thread(compTask) {{ setDaemon(true); }}.start();
    }

    private void onOneGptDone() {
        if (++gptDoneCount >= 2) {
            gptDoneCount = 0;
            btnOptimize.setDisable(false);
            btnOptimize.setText("⚡  OPTIMIZE");
            setStatus("DONE · GROQ RESPONSES LOADED");

            String origResponse = gptOriginalArea.getText();
            String compResponse = gptCompressedArea.getText();
            int origTokens = origResponse.isBlank() ? 0 : origResponse.split("\\s+").length;
            int compTokens = compResponse.isBlank() ? 0 : compResponse.split("\\s+").length;
            int saved = origTokens - compTokens;
            double percent = origTokens == 0 ? 0 : Math.round(((double) saved / origTokens) * 1000.0) / 10.0;

            statGptOriginal.setText(String.format("%,d", origTokens));
            statGptCompressed.setText(String.format("%,d", compTokens));
            statGptSaved.setText(String.format("%,d", saved));
            statGptPercent.setText(percent + "%");
        }
    }

    private Task<String> buildGptTask(String prompt, String apiKey) {
        return new Task<>() {
            @Override
            protected String call() throws IOException, InterruptedException {
                String escaped = prompt.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
                String body = "{\"model\":\"llama-3.3-70b-versatile\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}],\"max_tokens\":1024}";
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                return parseGptResponse(resp.body());
            }
        };
    }

    private String parseGptResponse(String json) {
        if (json.contains("\"error\"")) {
            int m = json.indexOf("\"message\":"); if (m != -1) { int s = json.indexOf("\"", m+10)+1; int e = json.indexOf("\"", s); if (s>0&&e>s) return "API Error: "+json.substring(s,e); } return "API Error: "+json;
        }
        String marker = "\"content\":";
        int idx = json.indexOf(marker);
        if (idx == -1) return "Unexpected response:\n" + json;
        int start = json.indexOf("\"", idx + marker.length()) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c=='\\' && i+1<json.length()) { char n=json.charAt(++i); switch(n){case '"'->sb.append('"');case '\\'->sb.append('\\');case 'n'->sb.append('\n');case 'r'->sb.append('\r');case 't'->sb.append('\t');default->sb.append(c);} }
            else if (c=='"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void copyToClipboard(String text, String confirmMsg) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(confirmMsg);
    }

    private void clearAll() {
        inputArea.clear();
        outputArea.clear();
        gptOriginalArea.clear();
        gptCompressedArea.clear();
        gptDoneCount = 0;
        lastOriginal = lastCompressed = "";
        statOriginal.setText("—");
        statCompressed.setText("—");
        statSaved.setText("—");
        statPercent.setText("—");
        statGptOriginal.setText("—");
        statGptCompressed.setText("—");
        statGptSaved.setText("—");
        statGptPercent.setText("—");
        btnCopyCompressed.setDisable(true);
        btnCopyOriginal.setDisable(true);
        setStatus("READY");
    }

    private void setStatus(String msg) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            FadeTransition ft = new FadeTransition(Duration.millis(200), statusLabel);
            ft.setFromValue(0.4);
            ft.setToValue(1.0);
            ft.play();
        });
    }

    // ── Result record ─────────────────────────────────────────────────────
    public static class CompressionResult {
        public final String original;
        public final String compressed;
        public final int originalTokens;
        public final int compressedTokens;

        public CompressionResult(String original, String compressed, int originalTokens, int compressedTokens) {
            this.original         = original;
            this.compressed       = compressed;
            this.originalTokens   = originalTokens;
            this.compressedTokens = compressedTokens;
        }

        public int saved()    { return originalTokens - compressedTokens; }
        public double percent() {
            if (originalTokens == 0) return 0;
            return Math.round(((double) saved() / originalTokens) * 1000.0) / 10.0;
        }
    }
}