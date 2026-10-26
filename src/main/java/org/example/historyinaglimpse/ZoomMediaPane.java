package org.example.historyinaglimpse;
import javafx.application.Platform;import javafx.beans.binding.Bindings;import javafx.geometry.Insets;import javafx.scene.control.*;import javafx.scene.image.*;import javafx.scene.layout.*;
/** A proportional still image with fit, zoom and drag-to-pan; no animation. */
final class ZoomMediaPane extends BorderPane {
 final ImageView image=new ImageView();final Slider zoom=new Slider(1,6,1);final ScrollPane scroll=new ScrollPane();final Button reset=new Button("Fit image");private final StackPane paper=new StackPane();
 ZoomMediaPane(String id,String route){
  image.setPreserveRatio(true);image.setSmooth(true);paper.setStyle("-fx-background-color:#e6d7b8;");scroll.setContent(paper);scroll.setPannable(true);setCenter(scroll);
  paper.minWidthProperty().bind(Bindings.createDoubleBinding(()->Math.max(1,scroll.getViewportBounds().getWidth()),scroll.viewportBoundsProperty()));paper.minHeightProperty().bind(Bindings.createDoubleBinding(()->Math.max(1,scroll.getViewportBounds().getHeight()),scroll.viewportBoundsProperty()));
  image.fitWidthProperty().bind(Bindings.createDoubleBinding(()->Math.max(1,scroll.getViewportBounds().getWidth()-24)*zoom.getValue(),scroll.viewportBoundsProperty(),zoom.valueProperty()));
  image.fitHeightProperty().bind(Bindings.createDoubleBinding(()->Math.max(1,scroll.getViewportBounds().getHeight()-24)*zoom.getValue(),scroll.viewportBoundsProperty(),zoom.valueProperty()));
  zoom.setPrefWidth(130);zoom.setAccessibleText("Image zoom");reset.setOnAction(e->{zoom.setValue(1);scroll.setHvalue(.5);scroll.setVvalue(0);});Label percent=new Label();percent.textProperty().bind(Bindings.format("%.0f%%",zoom.valueProperty().multiply(100)));FlowPane tools=new FlowPane(10,8,new Label("Zoom"),zoom,percent,reset);tools.setPadding(new Insets(10));setBottom(tools);load(id,route);
 }
 private void load(String id,String route){paper.getChildren().setAll(new Label("Loading image…"));BatchMedia.image(id,route).whenComplete((value,error)->Platform.runLater(()->{if(error!=null){Button retry=new Button("Try again");retry.setOnAction(e->load(id,route));paper.getChildren().setAll(new VBox(12,new Label("Image could not be loaded."),retry));}else{image.setImage(value);paper.getChildren().setAll(image);}}));}
}
