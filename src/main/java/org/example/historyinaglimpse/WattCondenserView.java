package org.example.historyinaglimpse;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.List;

/** Two schematic single-acting engine cycles; the right pane is reserved for a peer. */
final class WattCondenserView extends BorderPane implements AutoCloseable {
    final TabPane tabs=new TabPane();
    final List<Engine> engines=List.of(new Engine(false),new Engine(true));
    private final AmbientAudio audio;
    private final AnimationTimer timer;
    private long previous;
    private boolean closed;
    WattCondenserView(Runnable back){this(back,null);}
    WattCondenserView(Runnable back,AmbientAudio supplied){
        setStyle("-fx-background-color:#f5f0e6;-fx-font-family:'Georgia';");
        Label status=new Label();audio=supplied!=null?supplied:new CloudBirdAudio(message->status.setText(message),"/api/era-scenes/watt-condenser/audio");
        Button exit=new Button("← Back to chart");exit.setOnAction(e->back.run());
        Label heading=new Label("Watt’s separate condenser · 1769");heading.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#193c49;");
        ToggleButton mute=new ToggleButton("Mute ambiance");mute.setId("watt-mute");
        mute.setOnAction(e->{if(mute.isSelected()){audio.pause();mute.setText("Play ambiance");}else{audio.play();mute.setText("Mute ambiance");}});
        Slider volume=new Slider(0,1,.45);volume.setPrefWidth(95);volume.setAccessibleText("Shared ambiance volume");volume.valueProperty().addListener((o,a,b)->audio.setVolume(b.doubleValue()));
        FlowPane header=new FlowPane(12,8,exit,heading,mute,new Label("Volume"),volume);header.setPadding(new Insets(12));setTop(header);
        for(int i=0;i<2;i++){Tab t=new Tab(i==0?"1 · Cooling inside the cylinder":"2 · Watt’s separate condenser",engines.get(i));t.setClosable(false);tabs.getTabs().add(t);}
        tabs.setMinWidth(0);tabs.setId("watt-engine-tabs");tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,b)->{engines.get(a.intValue()).pause();previous=0;});
        StackPane blank=new StackPane();blank.setId("reserved-history-panel");blank.setMinWidth(220);blank.setStyle("-fx-background-color:#f4ead3;");
        SplitPane split=new SplitPane(tabs,blank);split.setDividerPositions(.58);setCenter(split);
        // One audio player for both tabs, with errors available on the sound control.
        status.textProperty().addListener((o,a,b)->mute.setTooltip(new Tooltip(b)));
        audio.setVolume(.45);audio.play();
        timer=new AnimationTimer(){@Override public void handle(long now){double dt=previous==0?0:Math.min(.05,(now-previous)/1e9);previous=now;engines.get(tabs.getSelectionModel().getSelectedIndex()).advance(dt);}};timer.start();
    }
    @Override public void close(){if(closed)return;closed=true;timer.stop();engines.forEach(Engine::pause);audio.close();}

    static final class Engine extends BorderPane {
        final boolean separate;
        final WattCycle cycle=new WattCycle();
        final Canvas canvas=new Canvas(1000,650);
        final Button run=new Button("Run"),reset=new Button("Reset");
        final Slider speed=new Slider(.25,2,1);
        final Label phase=new Label();
        boolean running;
        Engine(boolean separate){
            this.separate=separate;setPadding(new Insets(12));
            Label intro=new Label(separate?"Keep the cylinder hot. Cool the steam in a separate vessel.":"Condense steam inside the cylinder, then heat that cylinder again.");intro.setWrapText(true);intro.setStyle("-fx-font-size:17px;-fx-text-fill:#264653;");setTop(intro);
            StackPane stage=new StackPane(canvas);stage.setMinSize(0,0);canvas.widthProperty().bind(stage.widthProperty());canvas.heightProperty().bind(stage.heightProperty());
            canvas.widthProperty().addListener((o,a,b)->draw());canvas.heightProperty().addListener((o,a,b)->draw());setCenter(stage);
            run.setId(separate?"watt-run":"old-engine-run");run.setStyle("-fx-background-color:#245c68;-fx-text-fill:white;-fx-padding:8 18;");
            run.setOnAction(e->{running=!running;run.setText(running?"Pause":"Resume");});reset.setOnAction(e->{pause();cycle.reset();run.setText("Run");draw();});
            speed.setPrefWidth(135);speed.setAccessibleText("Animation speed");Label rate=new Label();rate.textProperty().bind(javafx.beans.binding.Bindings.format("Speed: %.2f×",speed.valueProperty()));
            FlowPane controls=new FlowPane(12,8,run,reset,rate,speed);phase.setWrapText(true);
            Label note=new Label("Schematic single-acting cycle · counterweight return · timing and colours are illustrative");note.setWrapText(true);note.setStyle("-fx-font-size:11px;-fx-text-fill:#79664c;");
            VBox bottom=new VBox(8,controls,phase,note);bottom.setPadding(new Insets(8,0,0,0));setBottom(bottom);draw();
        }
        void pause(){running=false;run.setText(cycle.elapsed==0?"Run":"Resume");}
        void advance(double dt){if(running)cycle.step(dt,speed.getValue());draw();}
        private void label(GraphicsContext g,String text,double x,double y){g.setFill(Color.web("#263d42"));g.setFont(Font.font("Georgia",18));g.fillText(text,x,y);}
        void draw(){
            double w=canvas.getWidth(),h=canvas.getHeight();if(w<=0||h<=0)return;
            var g=canvas.getGraphicsContext2D();g.setFill(Color.web("#f0e7d5"));g.fillRect(0,0,w,h);g.save();double s=Math.min(w/1000,h/650);g.translate((w-1000*s)/2,(h-650*s)/2);g.scale(s,s);
            int stage=cycle.stage();double position=cycle.piston(),piston=200+210*position;
            g.setFill(new LinearGradient(0,0,0,650,false,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#faf4e5")),new Stop(1,Color.web("#d8c6a6"))));g.fillRect(0,0,1000,650);
            g.setFill(Color.web("#244b50"));g.fillRoundRect(25,15,950,48,9,9);g.setFill(Color.web("#fff4d9"));g.setFont(Font.font("Georgia",FontWeight.BOLD,22));g.fillText(separate?"WATT · hot cylinder + cold condenser":"EARLIER DESIGN · one cylinder heated and cooled",45,47);
            // Counterweight return is schematic; the working stroke is caused by a pressure difference.
            g.setStroke(Color.web("#71624d"));g.setLineWidth(4);g.strokePolyline(new double[]{385,385,710,710},new double[]{piston-90,90,90,350-210*position},4);
            g.setFill(Color.web("#7d7567"));g.fillOval(692,72,36,36);g.setStroke(Color.web("#dbc9a3"));double angle=position*6;g.strokeLine(710,90,710+15*Math.cos(angle),90+15*Math.sin(angle));
            g.setFill(Color.web("#716554"));g.fillRoundRect(680,350-210*position,60,55,5,5);label(g,"Return weight",650,430);
            // Boiler and admission pipe.
            g.setFill(Color.web("#aa7542"));g.fillRoundRect(50,350,150,180,28,28);g.setFill(Color.web("#65a8b6"));g.fillRoundRect(65,430,120,80,12,12);label(g,"Boiler",85,595);
            g.setFill(Color.web("#ad5530"));g.fillRect(65,535,120,22);for(int i=0;i<5;i++){double flicker=10+7*Math.sin(cycle.elapsed*5+i);g.setFill(i%2==0?Color.ORANGE:Color.GOLD);g.fillOval(75+i*22,535-flicker,15,flicker+14);}
            pipe(g,200,455,310,455);g.setFill(stage==0?Color.web("#efb15b"):Color.web("#777265"));g.fillRect(243,442,14,26);
            boolean cool=!separate&&(stage==1||stage==2||stage==3);
            Color wall=cool?Color.web("#5b9cba"):Color.web("#d28a44");g.setFill(wall);g.fillRoundRect(300,155,170,330,14,14);
            g.setFill(Color.web("#f4e5c7"));g.fillRect(315,170,140,300);g.setFill(Color.web(cool?"#b6d6df":"#f2c98c"));g.fillRect(315,piston+16,140,Math.max(0,470-piston-16));
            // Moving piston, rod and crosshead; cylinder and boiler remain stationary.
            g.setFill(Color.web("#707774"));g.fillRect(315,piston,140,16);g.fillRect(379,piston-105,12,105);g.fillRoundRect(357,piston-110,56,12,4,4);
            label(g,"Cylinder",322,525);label(g,separate?"HOT throughout":"Heated ↔ cooled",285,553);
            g.setFill(Color.web("#a56b33"));g.fillRect(285,490,200,13);
            // Steam particles are confined below the piston in the working chamber.
            if(stage==0||stage==1){g.setFill(Color.web("#fff7e8",.8));for(int i=0;i<12;i++){double px=325+(i*37%120),py=piston+25+((i*23+cycle.elapsed*30)%Math.max(1,440-piston));if(py<464)g.fillOval(px,py,9,7);}}
            if(separate){
                pipe(g,470,455,805,455);g.setFill(stage==1||stage==2?Color.web("#4397ad"):Color.web("#777265"));g.fillRect(540,442,14,26);
                g.setFill(Color.web("#438da6"));g.fillRoundRect(805,350,135,170,18,18);g.setFill(Color.web("#a9dae1"));g.fillRoundRect(818,363,109,144,12,12);
                label(g,"COLD condenser",785,335);
                if(stage==1||stage==2){for(int i=0;i<11;i++){double t=(cycle.elapsed*.65+i/11.0)%1;g.setFill(Color.web("#f8f1d9"));g.fillOval(475+t*325,447,8,8);}drops(g,825,385,90,100);}
                pipe(g,870,520,870,580);g.setFill(Color.web("#568e9c"));g.fillRect(845,578,50,20);label(g,"Air pump / drain",765,625);
                label(g,"Cooling water",795,305);g.setStroke(Color.web("#3d91b3"));g.strokeLine(950,380,950,480);
            }else{
                label(g,"Cold-water injection",510,315);g.setStroke(Color.web("#3d91b3"));g.setLineWidth(6);g.strokePolyline(new double[]{555,500,500,450},new double[]{330,330,440,440},4);
                if(stage==1||stage==2){double top=Math.max(360,piston+22);drops(g,325,top,120,Math.max(1,465-top));}
                label(g,"Heat lost; reheating needed",520,565);
            }
            if(stage==0){g.setFill(Color.web("#fff3d7"));for(int i=0;i<5;i++){double t=(cycle.elapsed+i*.2)%1;g.fillOval(205+t*100,449,8,8);}}
            if(stage==2){g.setStroke(Color.web("#b75b30"));g.setFill(Color.web("#b75b30"));g.setLineWidth(4);g.strokeLine(255,200,255,275);g.fillPolygon(new double[]{255,247,263},new double[]{285,270,270},3);label(g,"Pressure",175,180);label(g,"difference",175,202);}
            g.restore();phase.setText(switch(stage){case 0->"1 · Admit steam; the return weight raises the piston.";case 1->separate?"2 · Open the condenser valve: steam flows to the cold vessel and condenses.":"2 · Inject cold water: steam condenses inside the cylinder.";case 2->"3 · Lower pressure below the piston allows a downward working stroke.";default->separate?"4 · Remove condensate and air; cylinder stays hot for the next cycle.":"4 · Drain condensate; the cooled cylinder must be reheated next cycle.";});
        }
        private void pipe(GraphicsContext g,double x1,double y1,double x2,double y2){g.setStroke(Color.web("#766e5e"));g.setLineWidth(16);g.strokeLine(x1,y1,x2,y2);g.setStroke(Color.web("#c6b48c"));g.setLineWidth(9);g.strokeLine(x1,y1,x2,y2);}
        private void drops(GraphicsContext g,double x,double y,double w,double h){g.setFill(Color.web("#327fba"));for(int i=0;i<14;i++){double px=x+(i*37%Math.max(1,w-8)),py=y+(i*19+cycle.elapsed*75)%h;g.fillOval(px,py,5,9);}}
    }
}
