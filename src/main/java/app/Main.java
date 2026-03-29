package src;
 
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
 
public class Main extends Application {
 
    @Override
    public void start(Stage stage) {
        MainWindow window = new MainWindow();
        Scene scene = new Scene(window.getRoot(), 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
 
        stage.setTitle("Prompt Optimizer");
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
    }
 
    public static void main(String[] args) {
        launch(args);
    }
}