package app;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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

public class MainWindow {

    // ── Root layout ───────────────────────────────────────────────────────
    private final BorderPane root = new BorderPane();

    // ── Editor panes ──────────────────────────────────────────────────────
    private final TextArea inputArea  = new TextArea();
    private final TextArea outputArea = new TextArea();

    // ── Stat labels ───────────────────────────────────────────────────────
    private final Label statOriginal   = new Label("—");
    private final Label statCompressed = new Label("—");
    private final Label statSaved      = new Label("—");
    private final Label statPercent    = new Label("—");

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

        // Divider line beneath header
        return header;
    }

    // ── Stat cards row ────────────────────────────────────────────────────
    private HBox buildStatCards() {
        HBox row = new HBox(10,
            statCard("ORIGINAL TOKENS",    statOriginal),
            statCard("COMPRESSED TOKENS",  statCompressed),
            statCard("TOKENS SAVED",       statSaved),
            statCard("REDUCTION",          statPercent)
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
        card.setPrefHeight(68);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    // ── Center (editors) ──────────────────────────────────────────────────
    private VBox buildCenter() {
        HBox editors = buildEditors();
        HBox stats   = buildStatCards();
        VBox center  = new VBox(0, stats, editors);
        VBox.setVgrow(editors, Priority.ALWAYS);
        return center;
    }

    private HBox buildEditors() {
        // Input
        Label inputLabel = new Label("INPUT PROMPT");
        inputLabel.getStyleClass().add("section-label");
        inputArea.setPromptText("Paste your prompt here...");
        inputArea.getStyleClass().add("editor");
        inputArea.setWrapText(true);
        VBox leftPane = new VBox(6, inputLabel, inputArea);
        VBox.setVgrow(inputArea, Priority.ALWAYS);
        leftPane.setPadding(new Insets(12, 6, 0, 0));

        // Output
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
        VBox.setVgrow(editors, Priority.ALWAYS);
        editors.setPadding(new Insets(0, 0, 0, 0));
        return editors;
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

        // ── Background task so UI stays responsive ────────────────────────
        Task<CompressionResult> task = new Task<>() {
            @Override
            protected CompressionResult call() {
                // ── WIRE YOUR BACKEND HERE ────────────────────────────────
                // Replace mockCompress() with:
                //   String compressed = RegexCompressor.compress(text);
                //   compressed = Simplifier.simplify(compressed);
                //   int[] tokens = TokenEvaluator.evaluate(text, compressed);
                //   return new CompressionResult(text, compressed, tokens[0], tokens[1]);
                return mockCompress(text);
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

            btnOptimize.setDisable(false);
            btnOptimize.setText("⚡  OPTIMIZE");
            btnCopyCompressed.setDisable(false);
            btnCopyOriginal.setDisable(false);

            setStatus("DONE  ·  " + r.percent() + "% REDUCTION");
        });

        task.setOnFailed(e -> {
            setStatus("ERROR: " + task.getException().getMessage());
            btnOptimize.setDisable(false);
            btnOptimize.setText("⚡  OPTIMIZE");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void copyToClipboard(String text, String confirmMsg) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        setStatus(confirmMsg);
    }

    private void clearAll() {
        inputArea.clear();
        outputArea.clear();
        lastOriginal = lastCompressed = "";
        statOriginal.setText("—");
        statCompressed.setText("—");
        statSaved.setText("—");
        statPercent.setText("—");
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

    // ── Mock backend (replace with real calls) ────────────────────────────
    private CompressionResult mockCompress(String text) {
        try { Thread.sleep(600); } catch (InterruptedException ignored) {}
        String compressed = text.replaceAll("(?i)\\b(please|can you|could you|I want you to|I need you to)\\b\\s*", "");
        compressed = compressed.replaceAll("\\s+", " ").trim();
        int origTokens = text.split("\\s+").length;
        int compTokens = compressed.split("\\s+").length;
        return new CompressionResult(text, compressed, origTokens, compTokens);
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