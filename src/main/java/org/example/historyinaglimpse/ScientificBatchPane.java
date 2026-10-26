package org.example.historyinaglimpse;
import javafx.animation.AnimationTimer;import javafx.geometry.Insets;import javafx.scene.canvas.*;import javafx.scene.control.*;import javafx.scene.layout.*;import javafx.scene.paint.*;import javafx.scene.text.*;
/** Native diagrams preserve geometry at every window size. */
final class ScientificBatchPane extends BorderPane implements AutoCloseable {
 final Canvas canvas=new Canvas(1000,650);final Button action,reset=new Button("Reset");final Slider frequency=new Slider(1,5,2),amplitude=new Slider(.2,1,.7);final String kind;boolean running,on;double time,glow;private long previous;private final AnimationTimer timer;
 ScientificBatchPane(String kind){this.kind=kind;setPadding(new Insets(12));StackPane stage=new StackPane(canvas);stage.setMinSize(0,0);canvas.widthProperty().bind(stage.widthProperty());canvas.heightProperty().bind(stage.heightProperty());canvas.widthProperty().addListener((o,a,b)->draw());canvas.heightProperty().addListener((o,a,b)->draw());setCenter(stage);
  action=new Button(kind.equals("light")?"Press":"Play");action.setId(kind.equals("light")?"edison-press":"telephone-play");action.setStyle("-fx-background-color:#245c68;-fx-text-fill:white;-fx-padding:10 25;-fx-font-weight:bold;");action.setOnAction(e->{if(kind.equals("light")){on=!on;}else{running=!running;action.setText(running?"Pause":"Play");}draw();});reset.setOnAction(e->{pause();on=false;time=0;glow=0;draw();});FlowPane controls=new FlowPane(12,8,action,reset);
  if(kind.equals("telephone")){frequency.setPrefWidth(110);amplitude.setPrefWidth(110);controls.getChildren().addAll(new Label("Pitch"),frequency,new Label("Strength"),amplitude);}
  Label note=new Label(kind.equals("light")?"Press to switch the filament lamp on or off.":"Conceptual sound → electrical signal → sound demonstration. Motion and signal travel are slowed for visibility.");note.setWrapText(true);note.setStyle("-fx-text-fill:#79664c;-fx-font-size:12px;");VBox bottom=new VBox(8,controls,note);bottom.setPadding(new Insets(10,0,0,0));setBottom(bottom);
  timer=new AnimationTimer(){public void handle(long now){double dt=previous==0?0:Math.min(.05,(now-previous)/1e9);previous=now;advance(dt);}};timer.start();draw();
 }
 void advance(double dt){if(running)time+=dt;if(kind.equals("light")){glow+=(on?1-glow:-glow)*Math.min(1,dt*8);}draw();}
 void pause(){running=false;previous=0;if(kind.equals("telephone"))action.setText("Play");}
 void draw(){double w=canvas.getWidth(),h=canvas.getHeight();if(w<=0||h<=0)return;GraphicsContext g=canvas.getGraphicsContext2D();g.setFill(Color.web(kind.equals("light")?"#211e1a":"#efe4cd"));g.fillRect(0,0,w,h);g.save();double s=Math.min(w/1000,h/650);g.translate((w-s*1000)/2,(h-s*650)/2);g.scale(s,s);if(kind.equals("light"))lamp(g);else telephone(g);g.restore();}
 private void label(GraphicsContext g,String text,double x,double y,double size){g.setFont(Font.font("Georgia",size));g.fillText(text,x,y);}
 private void lamp(GraphicsContext g){g.setFill(new LinearGradient(0,0,0,650,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#181a1b")),new Stop(1,Color.web("#5b4730"))));g.fillRect(0,0,1000,650);
  if(glow>.001){g.setFill(new RadialGradient(0,0,500,275,340,false,CycleMethod.NO_CYCLE,new Stop(0,Color.color(1,.78,.25,.65*glow)),new Stop(.5,Color.color(1,.61,.12,.2*glow)),new Stop(1,Color.TRANSPARENT)));g.fillOval(140,-70,720,720);}
  g.setFill(new LinearGradient(360,150,620,400,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#c4d4cc",.25)),new Stop(.4,Color.web("#45676b",.16)),new Stop(.7,Color.web("#effaf1",.25)),new Stop(1,Color.web("#263e43",.4))));
  g.beginPath();g.moveTo(455,455);g.bezierCurveTo(450,395,440,370,410,325);g.bezierCurveTo(320,185,385,80,500,80);g.bezierCurveTo(615,80,680,185,590,325);g.bezierCurveTo(560,370,550,395,545,455);g.closePath();g.fill();g.setStroke(Color.web("#d9eee5",.7));g.setLineWidth(3);g.stroke();
  g.setStroke(Color.web("#eee8d0",.5));g.setLineWidth(5);g.strokeArc(373,111,112,178,100,90,javafx.scene.shape.ArcType.OPEN);
  g.setStroke(Color.web("#78786b"));g.setLineWidth(4);g.strokeLine(468,450,478,300);g.strokeLine(532,450,522,300);
  g.setStroke(Color.color(.25+.75*glow,.2+.63*glow,.12+.3*glow));g.setLineWidth(4+3*glow);g.beginPath();g.moveTo(478,300);g.bezierCurveTo(425,150,575,150,522,300);g.stroke();if(glow>.1){g.setStroke(Color.color(1,.76,.15,.15*glow));g.setLineWidth(20);g.stroke();g.setStroke(Color.color(1,.95,.67,glow));g.setLineWidth(3);g.stroke();}
  g.setFill(new LinearGradient(400,450,600,500,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#3d251c")),new Stop(.5,Color.web("#b68c53")),new Stop(1,Color.web("#3a231a"))));g.fillRoundRect(435,448,130,47,20,20);g.fillPolygon(new double[]{465,535,570,430},new double[]{490,490,555,555},4);g.fillOval(350,535,300,65);g.setFill(Color.web("#b28748"));g.fillOval(370,548,260,25);
  g.setFill(Color.web("#fff0c9"));label(g,on?"LIGHT ON":"LIGHT OFF",430,630,23);
 }
 private void telephone(GraphicsContext g){g.setFill(new LinearGradient(0,0,0,650,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#faf4e5")),new Stop(1,Color.web("#d3c09f"))));g.fillRect(0,0,1000,650);g.setFill(Color.web("#244b50"));g.fillRoundRect(30,22,940,65,10,10);g.setFill(Color.web("#fff2d8"));label(g,"SOUND → SIGNAL → SOUND",265,64,27);
  double wave=running?Math.sin(time*frequency.getValue()*Math.PI*2)*amplitude.getValue():0;
  for(int side=0;side<2;side++){double x=side==0?200:800,v=side==0?wave:(running?Math.sin((time-.2)*frequency.getValue()*Math.PI*2)*amplitude.getValue():0);g.setFill(Color.web("#825532"));g.fillRoundRect(x-100,450,200,40,12,12);g.setFill(Color.web("#8f754e"));g.fillRect(x-12,310,24,140);g.setFill(Color.web("#3b4543"));g.fillOval(x-70,220,140,140);g.setStroke(Color.web("#c59c5f"));g.setLineWidth(7);g.strokeOval(x-45,245,90,90);g.setStroke(Color.web("#dce3d4"));g.setLineWidth(5);g.strokeLine(x+v*9,245,x+v*9,335);g.setFill(Color.web("#253f44"));label(g,side==0?"Transmitter":"Receiver",x-65,530,21);
   for(int i=0;i<3;i++){g.setStroke(Color.web("#327d91",running?.7:.2));g.setLineWidth(3);double radius=90+i*20+(time*25)%20;g.strokeArc(x-radius,290-radius/2,radius*2,radius,side==0?140:-40,80,javafx.scene.shape.ArcType.OPEN);}
  }
  g.setStroke(Color.web("#78684c"));g.setLineWidth(5);g.strokePolyline(new double[]{270,330,670,730},new double[]{310,310,310,310},4);g.strokePolyline(new double[]{200,200,800,800},new double[]{450,570,570,450},4);
  g.setStroke(Color.web("#b16628"));g.setLineWidth(3);g.beginPath();for(int i=0;i<=340;i++){double y=290+(running?Math.sin(time*frequency.getValue()*Math.PI*2-i*.06)*amplitude.getValue()*35:0);if(i==0)g.moveTo(330,y);else g.lineTo(330+i,y);}g.stroke();g.setFill(Color.web("#263f44"));label(g,"Changing electrical signal",365,390,19);label(g,"Diaphragm",145,195,17);label(g,"Diaphragm",745,195,17);
 }
 @Override public void close(){timer.stop();running=false;}
}
