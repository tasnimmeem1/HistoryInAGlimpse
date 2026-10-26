package org.example.historyinaglimpse;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.ArrayList;
import java.util.List;

/** Three interactive native JavaFX illustrations sharing one ambient player. */
final class NewtonLawsView extends BorderPane implements AutoCloseable {
    private final List<Experiment> experiments = new ArrayList<>();
    private final AmbientAudio ambiance;
    private final TabPane tabs = new TabPane();
    private final AnimationTimer timer;
    private boolean closed;
    private long previous;
    private final Label audioStatus = new Label("Loading shared ambiance…");

    NewtonLawsView(Runnable back) {
        this(back, null);
    }
    NewtonLawsView(Runnable back, AmbientAudio audio) {
        ambiance=audio != null ? audio : new CloudBirdAudio(audioStatus::setText, "/api/era-scenes/newtons-laws/audio");
        setStyle("-fx-background-color:#f5f0e6;-fx-font-family:'Georgia';");
        Button returnButton=new Button("← Back to chart"); returnButton.setOnAction(e->back.run());
        returnButton.setId("newton-back");
        Label title=new Label("Newton’s laws of motion");title.setStyle("-fx-font-size:23px;-fx-font-weight:bold;-fx-text-fill:#193c49;");
        Label year=new Label("Principia · 1687");year.setStyle("-fx-text-fill:#75634a;");
        VBox heading=new VBox(3,title,year);
        ToggleButton mute=new ToggleButton("Mute ambiance");mute.setId("newton-mute");
        mute.setOnAction(e->{if(mute.isSelected()){ambiance.pause();mute.setText("Play ambiance");}else{ambiance.play();mute.setText("Mute ambiance");}});
        Slider volume=new Slider(0,1,.45);volume.setPrefWidth(100);volume.setId("newton-volume");
        volume.setAccessibleText("Shared ambiance volume");volume.valueProperty().addListener((o,a,b)->ambiance.setVolume(b.doubleValue()));
        FlowPane narrow=new FlowPane(12,8,returnButton,heading,mute,new Label("Volume"),volume);narrow.setPadding(new Insets(12));
        setTop(narrow);
        String[] names={"1 · Inertia","2 · Force and acceleration","3 · Action and reaction"};
        for(int i=1;i<=3;i++){Experiment ex=new Experiment(i);experiments.add(ex);Tab tab=new Tab(names[i-1],ex);tab.setClosable(false);tabs.getTabs().add(tab);}
        tabs.setId("newton-law-tabs");
        // Reserved for the teammate responsible for historical content.
        StackPane reading=new StackPane();reading.setId("reserved-history-panel");
        reading.setStyle("-fx-background-color:#f4ead3;");reading.setMinWidth(220);
        tabs.setMinWidth(0);SplitPane split=new SplitPane(tabs,reading);split.setId("newton-story-split");split.setDividerPositions(.58);setCenter(split);
        tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->{experiments.get(a.intValue()).pause();previous=0;});
        ambiance.setVolume(.45);ambiance.play();audioStatus.setText("One shared ambiance track · continuous across tabs");
        timer=new AnimationTimer(){@Override public void handle(long now){double dt=previous==0?0:Math.min(.05,(now-previous)/1e9);previous=now;Experiment ex=experiments.get(tabs.getSelectionModel().getSelectedIndex());ex.advance(dt);}};timer.start();
    }
    @Override public void close(){if(closed)return;closed=true;timer.stop();experiments.forEach(Experiment::pause);ambiance.close();}
    List<Experiment> experiments(){return List.copyOf(experiments);}
    TabPane tabs(){return tabs;}

    static final class Experiment extends BorderPane {
        final NewtonPhysics physics;
        final Canvas canvas=new Canvas(1000,470);
        final Button run=new Button(),reset=new Button("Reset"),brake=new Button("Apply brake");
        final Slider mass=new Slider(1,8,2),force=new Slider(0,20,6),rightMass=new Slider(1,8,2);
        final Label readout=new Label(),message=new Label();
        final CheckBox frictionToggle=new CheckBox("Friction (0.8 N)");
        private boolean running;
        private final String[] statement={"","An object keeps its velocity unless a net force changes it.","For constant mass, acceleration equals net force divided by mass.","Two interacting objects exert equal and opposite forces on each other."};
        Experiment(int law) {
            physics=new NewtonPhysics(law);setPadding(new Insets(14));
            Label intro=new Label(statement[law]);intro.setWrapText(true);intro.setStyle("-fx-font-size:17px;-fx-text-fill:#264653;");
            Label detail=new Label(law==1?"Give the ball a brief push. After your hand leaves, compare a smooth track with friction.":law==2?"Choose a net force and cart mass. Run the experiment, then reset and compare another choice.":"Release a spring between the carts. Change either mass: forces stay equal, but accelerations can differ.");detail.setWrapText(true);detail.setStyle("-fx-text-fill:#6d6556;");
            VBox introBox=new VBox(5,intro,detail);introBox.setPadding(new Insets(0,0,10,0));setTop(introBox);
            StackPane stage=new StackPane(canvas);stage.setMinSize(0,0);stage.setStyle("-fx-background-color:#f0e7d5;-fx-background-radius:12;");
            canvas.widthProperty().bind(stage.widthProperty());canvas.heightProperty().bind(stage.heightProperty());canvas.widthProperty().addListener((o,a,b)->draw());canvas.heightProperty().addListener((o,a,b)->draw());setCenter(stage);
            run.setText(law==1?"Push ball":law==2?"Run experiment":"Release carts");run.setId("law"+law+"-run");reset.setId("law"+law+"-reset");brake.setId("law1-brake");
            run.setStyle("-fx-background-color:#245c68;-fx-text-fill:white;-fx-padding:9 18;-fx-font-weight:bold;");
            run.setOnAction(e->{if(law==1){physics.push();running=true;message.setText("A brief 4 N push acts for 0.25 s.");}else if(running){pause();}else{if(law==3&&physics.time==0)physics.release();running=true;run.setText("Pause");message.setText("Experiment running");}});
            reset.setOnAction(e->reset());brake.setOnAction(e->{physics.friction=true;frictionToggle.setSelected(true);running=true;message.setText("A braking force opposes the ball’s motion.");});
            FlowPane controls=new FlowPane(12,9,run,reset);
            if(law==1){frictionToggle.setId("law1-friction");frictionToggle.setOnAction(e->{physics.friction=frictionToggle.isSelected();running=true;});controls.getChildren().addAll(brake,frictionToggle);}
            if(law==2){controls.getChildren().addAll(sliderGroup("Cart mass",mass,"kg"),sliderGroup("Net force",force,"N"));}
            if(law==3){controls.getChildren().addAll(sliderGroup("Left mass",mass,"kg"),sliderGroup("Right mass",rightMass,"kg"));}
            for(Slider slider:List.of(mass,force,rightMass))slider.valueProperty().addListener((o,a,b)->{physics.mass=mass.getValue();physics.force=force.getValue();physics.rightMass=rightMass.getValue();reset();message.setText("Settings changed. Run again to compare.");});
            message.setWrapText(true);message.setStyle("-fx-text-fill:#766345;");readout.setWrapText(true);readout.setStyle("-fx-font-family:'Monospaced';-fx-font-size:13px;-fx-text-fill:#163f4d;");
            VBox bottom=new VBox(8,controls,readout,message);bottom.setPadding(new Insets(12,0,0,0));setBottom(bottom);reset();
        }
        private VBox sliderGroup(String name,Slider slider,String unit){Label label=new Label();label.textProperty().bind(javafx.beans.binding.Bindings.format(name+": %.1f "+unit,slider.valueProperty()));slider.setPrefWidth(150);slider.setAccessibleText(name);return new VBox(3,label,slider);}
        void pause(){running=false;if(physics.law!=1)run.setText(physics.time==0?(physics.law==2?"Run experiment":"Release carts"):"Resume");}
        void reset(){pause();physics.reset();run.setText(physics.law==1?"Push ball":physics.law==2?"Run experiment":"Release carts");message.setText("Ready. Horizontal support balances gravity; air resistance is ignored.");draw();}
        void advance(double dt){if(running&&dt>0)physics.step(dt);draw();}
        boolean running(){return running;}
        private void draw(){
            double cw=canvas.getWidth(),ch=canvas.getHeight();if(cw<1||ch<1)return;GraphicsContext g=canvas.getGraphicsContext2D();g.setFill(Color.web("#f0e7d5"));g.fillRect(0,0,cw,ch);g.save();double scale=Math.min(cw/1000,ch/470);g.translate((cw-1000*scale)/2,(ch-470*scale)/2);g.scale(scale,scale);
            g.setFill(new LinearGradient(0,0,0,470,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#f8f3e7")),new Stop(1,Color.web("#dbcbb0"))));g.fillRect(0,0,1000,470);
            // A laboratory-style illustration; analytical motion is drawn in SI units.
            g.setFill(Color.web("#224a52"));g.fillRoundRect(45,30,905,120,12,12);g.setFill(Color.web("#f8f2df"));g.setFont(Font.font("Georgia",FontWeight.BOLD,25));
            g.fillText(physics.law==1?"LAW I  ·  Keep moving":physics.law==2?"LAW II  ·  F = ma":"LAW III  ·  Equal forces, opposite directions",70,70);
            g.setFont(Font.font("Monospaced",18));g.fillText(physics.law==1?(physics.pulse>1e-9?"Brief push → acceleration":physics.friction?"Friction → changing velocity":"Net force = 0 → constant velocity"):physics.law==2?String.format(java.util.Locale.US,"a = %.1f N / %.1f kg = %.2f m/s²",physics.force,physics.mass,physics.force/physics.mass):"F on left = −F on right  (different objects)",70,108);
            g.setFill(Color.web("#795b3d"));g.fillRoundRect(30,345,940,24,6,6);g.setFill(Color.web("#a48459"));g.fillRect(48,369,28,101);g.fillRect(924,369,28,101);
            g.setFill(Color.web("#d8d5c5"));g.fillRect(35,328,930,17);g.setStroke(Color.web("#647b7a"));g.setLineWidth(2);g.strokeLine(35,329,965,329);
            if(physics.law==1)drawBall(g);else if(physics.law==2)drawSingleCart(g);else drawPair(g);
            g.setFill(Color.web("#5d584b"));g.setFont(Font.font("Georgia",14));g.fillText("Ideal horizontal track · illustrative scale",45,425);
            g.restore();updateReadout();
        }
        private void drawBall(GraphicsContext g){double camera=Math.max(0,physics.x-6);double x=120+(physics.x-camera)*100;scale(g,camera);g.setFill(new RadialGradient(0,0,.28,.22,.8,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#a2d8df")),new Stop(.5,Color.web("#35818e")),new Stop(1,Color.web("#153d49"))));g.fillOval(x-25,279,50,50);g.setFill(Color.web("#163b46"));g.setFont(Font.font(14));g.fillText("1 kg",x-15,271);if(physics.velocity>1e-6)arrow(g,x+35,290,Math.min(120,35+physics.velocity*40),"velocity →",Color.web("#287087"));if(physics.netForce()!=0)arrow(g,x,235,physics.netForce()*22,"net force",Color.web("#ae7130"));}
        private void drawSingleCart(GraphicsContext g){double camera=Math.max(0,physics.x-5.5);double x=120+(physics.x-camera)*100;scale(g,camera);cart(g,x,physics.mass,Color.web("#2f7184"),"cart");if(physics.force>0)arrow(g,x+70,248,Math.min(150,physics.force*6+25),String.format(java.util.Locale.US,"%.1f N",physics.force),Color.web("#a96e27"));}
        private void drawPair(GraphicsContext g){double extent=Math.max(Math.abs(physics.x),Math.abs(physics.rightX));double scale=100/Math.max(1,extent/2.8);double left=415+physics.x*scale,right=585+physics.rightX*scale;cart(g,left,physics.mass,Color.web("#2f7184"),"left");cart(g,right,physics.rightMass,Color.web("#ab773b"),"right");if(physics.time==0 || physics.contact>1e-9){g.setStroke(Color.web("#6c7070"));g.setLineWidth(3);g.beginPath();g.moveTo(left+55,285);for(int i=1;i<12;i++)g.lineTo(left+55+(right-left-110)*i/12,285+(i%2==0?-9:9));g.lineTo(right-55,285);g.stroke();if(physics.contact>1e-9){arrow(g,left,230,-95,"6 N",Color.web("#2f7184"));arrow(g,right,230,95,"6 N",Color.web("#ab773b"));}}else{g.setFill(Color.web("#6c6250"));g.setFont(Font.font(15));g.fillText("After the spring stops pushing: both carts coast.",300,205);}if(extent>2.8){g.setFont(Font.font(13));g.fillText("View zooms out as the carts separate",370,393);}}
        private void scale(GraphicsContext g,double camera){g.setStroke(Color.web("#9b967f"));g.setFill(Color.web("#6a6656"));g.setFont(Font.font(12));for(int i=(int)Math.floor(camera);i<camera+10;i++){double x=120+(i-camera)*100;if(x>35&&x<965){g.strokeLine(x,347,x,356);g.fillText(i+" m",x-8,387);}}if(camera>0)g.fillText("Camera follows the motion",700,425);}
        private void cart(GraphicsContext g,double x,double mass,Color color,String name){g.setFill(Color.web("#22363a"));g.fillOval(x-40,310,22,22);g.fillOval(x+18,310,22,22);g.setFill(color);g.fillRoundRect(x-55,267,110,48,10,10);g.setFill(Color.web("#f5ead0"));g.fillRoundRect(x-35,245,70,30,5,5);g.setFill(Color.web("#193d46"));g.setFont(Font.font("Georgia",FontWeight.BOLD,16));g.fillText(String.format(java.util.Locale.US,"%.1f kg",mass),x-27,266);g.setFill(Color.WHITE);g.setFont(Font.font(14));g.fillText(name,x-17,296);}
        private void arrow(GraphicsContext g,double x,double y,double length,String text,Color color){g.setStroke(color);g.setFill(color);g.setLineWidth(4);g.strokeLine(x,y,x+length,y);double sign=Math.signum(length);g.fillPolygon(new double[]{x+length,x+length-sign*12,x+length-sign*12},new double[]{y,y-6,y+6},3);g.setFont(Font.font(14));g.fillText(text,Math.min(x,x+length),y-12);}
        private void updateReadout(){String data=physics.law==3?String.format(java.util.Locale.US,"t %.2f s   |   vL %.2f m/s   vR %.2f m/s   |   total momentum %.3f kg·m/s",physics.time,physics.velocity,physics.rightVelocity,physics.mass*physics.velocity+physics.rightMass*physics.rightVelocity):String.format(java.util.Locale.US,"t %.2f s   |   distance %.2f m   |   velocity %.2f m/s   |   net force %.2f N",physics.time,physics.x,physics.velocity,physics.netForce());readout.setText(data);}
    }
}
