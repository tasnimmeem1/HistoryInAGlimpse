package org.example.historyinaglimpse;
import com.fasterxml.jackson.databind.JsonNode;import javafx.animation.*;import javafx.application.Platform;import javafx.geometry.Insets;import javafx.scene.Node;import javafx.scene.control.*;import javafx.scene.layout.*;import java.util.*;
/** Shared shell: illustrations on the left, an empty peer-owned panel on the right. */
final class ModernBatchView extends BorderPane implements AutoCloseable {
 final TabPane tabs=new TabPane();private final String id;private final AmbientAudio audio;private final StackPane left=new StackPane();private final List<Runnable> disposers=new ArrayList<>(),pauses=new ArrayList<>();private boolean closed;
 ModernBatchView(String id,String title,Runnable back){this(id,title,back,null);}
 ModernBatchView(String id,String title,Runnable back,AmbientAudio supplied){
  this.id=id;setStyle("-fx-background-color:#f5f0e6;-fx-font-family:'Georgia';");ToggleButton mute=new ToggleButton("Mute ambiance");mute.setId("batch-mute");
  audio=supplied!=null?supplied:new CloudBirdAudio(message->mute.setTooltip(new Tooltip(message)),"/api/era-scenes/"+id+"/audio");mute.setOnAction(e->{if(mute.isSelected()){audio.pause();mute.setText("Play ambiance");}else{audio.play();mute.setText("Mute ambiance");}});
  Button exit=new Button("← Back to chart");exit.setOnAction(e->back.run());Label heading=new Label(title);heading.setWrapText(true);heading.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#193c49;");Slider volume=new Slider(0,1,.45);volume.setPrefWidth(95);volume.setAccessibleText("Ambiance volume");volume.valueProperty().addListener((o,a,b)->audio.setVolume(b.doubleValue()));FlowPane bar=new FlowPane(12,8,exit,heading,mute,new Label("Volume"),volume);bar.setPadding(new Insets(12));setTop(bar);
  left.setMinWidth(0);StackPane blank=new StackPane();blank.setId("reserved-history-panel");blank.setMinWidth(220);blank.setStyle("-fx-background-color:#f4ead3;");SplitPane split=new SplitPane(left,blank);split.setDividerPositions(.58);setCenter(split);
  tabs.setId("modern-entry-tabs");tabs.setMinWidth(0);tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->{int i=a.intValue();if(i>=0&&i<pauses.size())pauses.get(i).run();activate();});load();audio.setVolume(.45);audio.play();
 }
 private void load(){left.getChildren().setAll(new Label("Loading entry…"));BatchMedia.metadata(id).whenComplete((row,error)->Platform.runLater(()->{if(closed)return;if(error!=null){Button retry=new Button("Try again");retry.setOnAction(e->load());left.getChildren().setAll(new VBox(12,new Label("Entry could not be loaded. Check the backend and SQL setup."),retry));return;}build(row.path("settings"));}));}
 private Animation sceneMotion;
 private PhotoActivityPane photoActivity;private int photoIndex=-1;
 private int sceneIndex=-1;
 private void add(String title,Node view,Runnable pause){Tab tab=new Tab(title,view);tab.setClosable(false);tabs.getTabs().add(tab);pauses.add(pause);}
 private void build(JsonNode settings){
  String view=settings.path("view").asText();
  if(view.equals("web-screen")||view.equals("market-screen")){photoActivity=new PhotoActivityPane(id,settings);photoIndex=tabs.getTabs().size();add("Animation",photoActivity,photoActivity::pause);disposers.add(photoActivity::close);}
  else if(view.equals("document"))add("Document",new ZoomMediaPane(id,"background"),()->{});
  else if(view.equals("telephone")||view.equals("light")){ScientificBatchPane pane=new ScientificBatchPane(view);add(view.equals("light")?"Electric light":"How the telephone works",pane,pane::pause);disposers.add(pane::close);if(settings.has("reference_path"))add("Instrument reference",new ZoomMediaPane(id,"background"),()->{});}
  else if(view.equals("evolution")){EvolutionPane pane=new EvolutionPane(id,settings);add("Elephant relatives",pane,pane::pause);disposers.add(pane::close);}
  else{
   CloudEraScene scene=new CloudEraScene(id);sceneMotion=scene.animation();sceneIndex=tabs.getTabs().size();add("Animation",scene,sceneMotion::pause);disposers.add(sceneMotion::stop);
   if(view.equals("photography")){PhotoSequencePane sequence=new PhotoSequencePane(id);add("Later motion photography",sequence,sequence::pause);disposers.add(sequence::close);}
  }
  if(settings.has("document_path"))add(settings.path("document_label").asText("Document"),new ZoomMediaPane(id,"document"),()->{});
  if(settings.has("map_path"))add(settings.path("map_label").asText(id.equals("first-world-war")?"Maps and overview":"Era map"),new ZoomMediaPane(id,"map"),()->{});
  left.getChildren().setAll(tabs);activate();
 }
 private void activate(){if(photoActivity!=null){if(tabs.getSelectionModel().getSelectedIndex()==photoIndex)photoActivity.play();else photoActivity.pause();}if(sceneMotion!=null){if(tabs.getSelectionModel().getSelectedIndex()==sceneIndex)sceneMotion.play();else sceneMotion.pause();}}
 @Override public void close(){if(closed)return;closed=true;disposers.forEach(Runnable::run);audio.close();}
 static StackPane thumbnail(String id){StackPane box=new StackPane(new Label("Preview"));box.setManaged(false);box.setMouseTransparent(true);box.setStyle("-fx-background-color:#efdfbb;-fx-border-color:#9a8564;-fx-border-width:.7;");
  if(id.equals("web-software")||id.equals("financial-crisis")){BatchMedia.metadata(id).thenCombine(BatchMedia.image(id,"background"),(row,picture)->new Object[]{row,picture}).whenComplete((values,error)->Platform.runLater(()->{if(error==null){javafx.scene.image.ImageView image=new javafx.scene.image.ImageView(PhotoActivityPane.preview(id,((JsonNode)values[0]).path("settings"),(javafx.scene.image.Image)values[1]));image.setFitWidth(128);image.setFitHeight(90);image.setPreserveRatio(true);box.getChildren().setAll(image);}}));}
  else if(id.equals("bell-telephone")||id.equals("edison-light")){ScientificBatchPane pane=new ScientificBatchPane(id.equals("edison-light")?"light":"telephone");pane.canvas.widthProperty().unbind();pane.canvas.heightProperty().unbind();pane.canvas.setWidth(1000);pane.canvas.setHeight(650);pane.draw();javafx.scene.image.ImageView image=new javafx.scene.image.ImageView(pane.canvas.snapshot(null,null));image.setFitWidth(128);image.setFitHeight(90);image.setPreserveRatio(true);pane.close();box.getChildren().setAll(image);}
  else if(java.util.Set.of("french-revolution","stockton-darlington","early-photography","emancipation-proclamation","wright-flight","first-world-war","second-world-war","civil-rights","moon-landing","first-generation-computers","september-11","covid-19").contains(id)){CloudEraThumbnail scene=new CloudEraThumbnail(id);return scene;}
  else BatchMedia.image(id,"background").whenComplete((value,error)->Platform.runLater(()->{if(error==null){javafx.scene.image.ImageView image=new javafx.scene.image.ImageView(value);if(id.equals("darwin-evolution"))image.setViewport(new javafx.geometry.Rectangle2D(0,0,452,490));image.setPreserveRatio(true);image.setFitWidth(128);image.setFitHeight(90);box.getChildren().setAll(image);}}));
  return box;
 }
}
