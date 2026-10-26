package org.example.historyinaglimpse;
import javafx.animation.AnimationTimer;import javafx.application.Platform;import javafx.geometry.*;import javafx.scene.control.*;import javafx.scene.image.*;import javafx.scene.layout.*;
/** Plays the user's20-frame photographic plate in reading order. */
final class PhotoSequencePane extends BorderPane implements AutoCloseable {
 final ImageView image=new ImageView();final Button play=new Button("Play"),reset=new Button("Reset");final Slider fps=new Slider(2,16,8);boolean running;double elapsed;int frame;private long previous;private final AnimationTimer timer;
 PhotoSequencePane(String id){StackPane stage=new StackPane(image);stage.setMinSize(0,0);image.setPreserveRatio(true);image.fitWidthProperty().bind(stage.widthProperty().subtract(24));image.fitHeightProperty().bind(stage.heightProperty().subtract(24));setCenter(stage);play.setOnAction(e->{running=!running;play.setText(running?"Pause":"Play");});reset.setOnAction(e->{pause();elapsed=0;frame=0;show();});fps.setPrefWidth(130);fps.setAccessibleText("Photographic frames per second");Label rate=new Label();rate.textProperty().bind(javafx.beans.binding.Bindings.format("%.0f frames/sec",fps.valueProperty()));Label note=new Label("Later nineteenth-century motion study · supplied20-frame plate · images play left-to-right, top-to-bottom");note.setWrapText(true);VBox tools=new VBox(8,new FlowPane(12,8,play,reset,rate,fps),note);tools.setPadding(new Insets(12));setBottom(tools);
 BatchMedia.image(id,"foreground").whenComplete((value,error)->Platform.runLater(()->{if(error==null){image.setImage(value);show();}else stage.getChildren().add(new Label("Motion-study image unavailable. Reopen to retry."));}));
 timer=new AnimationTimer(){public void handle(long now){double dt=previous==0?0:Math.min(.1,(now-previous)/1e9);previous=now;advance(dt);}};timer.start();}
 void advance(double dt){if(running){elapsed+=dt;frame=((int)(elapsed*fps.getValue()))%20;show();}}
 private void show(){if(image.getImage()==null)return;double w=image.getImage().getWidth()/5,h=image.getImage().getHeight()/4;image.setViewport(new Rectangle2D((frame%5)*w,(frame/5)*h,w,h));}
 void pause(){running=false;previous=0;play.setText("Play");}
 @Override public void close(){pause();timer.stop();}
}
