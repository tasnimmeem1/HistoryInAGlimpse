package org.example.historyinaglimpse;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Native screen activity over a private cloud image, with a fixed photographic frame. */
final class PhotoActivityPane extends BorderPane implements AutoCloseable {
    final Canvas canvas = new Canvas();
    final String mode;
    boolean running = true;
    double time;
    private Image background;
    private boolean closed;
    private long previous;
    private final AnimationTimer timer;
    private final double rx, ry, rw, rh;
    final Button playback = new Button("Pause"), restart = new Button("Restart"), openLink = new Button("Open link");
    private boolean hovered;
    private final double[] marketValues;
    private final String[] marketDates;

    PhotoActivityPane(String id, JsonNode settings) {
        mode = settings.path("view").asText();
        JsonNode data=settings.path("marketData");
        if(mode.equals("market-screen")&&(!data.isArray()||data.size()<2||data.size()>200))data=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
        marketValues=new double[data.isArray()?data.size():0];marketDates=new String[marketValues.length];
        for(int i=0;i<marketValues.length;i++){JsonNode point=data.get(i);double value=point.path("value").asDouble(Double.NaN);String date=point.path("date").asText();if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("Invalid market value");java.time.LocalDate.parse(date);if(i>0&&date.compareTo(marketDates[i-1])<=0)throw new IllegalArgumentException("Market dates must increase");marketValues[i]=value;marketDates[i]=date;}

        JsonNode region = settings.path("screen_region");
        rx = region.path("x").asDouble(mode.equals("web-screen")?.225:.04); ry = region.path("y").asDouble(mode.equals("web-screen")?.148:.08);
        rw = region.path("width").asDouble(mode.equals("web-screen")?.405:.92); rh = region.path("height").asDouble(mode.equals("web-screen")?.422:.8);
        if(rx<0 || ry<0 || rw<=0 || rh<=0 || rx+rw>1 || ry+rh>1)
            throw new IllegalArgumentException("Invalid screen region");
        StackPane stage = new StackPane(canvas);
        stage.setMinSize(0,0);
        canvas.widthProperty().bind(stage.widthProperty());
        canvas.heightProperty().bind(stage.heightProperty());
        canvas.widthProperty().addListener((o,a,b)->draw());
        canvas.heightProperty().addListener((o,a,b)->draw());
        setCenter(stage);
        Label note = new Label(mode.equals("market-screen")
            ? "S&P 500 quarter-end closing levels. Source: UBS filing in SEC EDGAR."
            : "Illustrative early-web browser. Watch a page load, then follow a link.");
        note.setWrapText(true); note.setPadding(new Insets(10));
        note.setStyle("-fx-font-size:12px;-fx-text-fill:#675944;");
        if(mode.equals("web-screen")) {
            playback.setOnAction(e->{if(running)pause();else play();});
            restart.setOnAction(e->{time=0;play();draw();});
            openLink.setOnAction(e->followLink());
            FlowPane controls=new FlowPane(10,8,playback,restart,openLink);
            controls.setPadding(new Insets(8,10,0,10));
            setBottom(new VBox(controls,note));
            canvas.setOnMouseMoved(e->{double[] p=webPoint(e.getX(),e.getY());hovered=onLink(p[0],p[1]);canvas.setCursor(hovered?javafx.scene.Cursor.HAND:javafx.scene.Cursor.DEFAULT);});
            canvas.setOnMouseExited(e->{hovered=false;});
            canvas.setOnMouseClicked(e->{double[] p=webPoint(e.getX(),e.getY());if(onLink(p[0],p[1]))followLink();});
        } else {
            playback.setOnAction(e->{if(running)pause();else play();});restart.setOnAction(e->{time=0;play();draw();});
            FlowPane controls=new FlowPane(10,8,playback,restart);controls.setPadding(new Insets(8,10,0,10));setBottom(new VBox(controls,note));
        }
        BatchMedia.image(id,"background").whenComplete((image,error)->Platform.runLater(()->{
            if(closed)return;
            if(error!=null){Label failed=new Label("Screen image unavailable. Reopen to retry.");stage.getChildren().setAll(failed);return;}
            background=image;draw();
        }));
        timer=new AnimationTimer(){public void handle(long now){
            double dt=previous==0?0:Math.min(.1,(now-previous)/1e9);previous=now;advance(dt);
        }};
        timer.start();
    }
    void advance(double dt){if(running){time+=Math.max(0,dt);draw();}}
    void play(){if(closed)return;running=true;previous=0;playback.setText("Pause");}
    void pause(){running=false;previous=0;playback.setText("Play");}
    void draw(){
        double w=canvas.getWidth(),h=canvas.getHeight();
        if(w<=0 || h<=0)return;
        GraphicsContext g=canvas.getGraphicsContext2D();
        g.setFill(Color.web("#ead9b5"));g.fillRect(0,0,w,h);
        if(background==null)return;
        double s=Math.min(w/background.getWidth(),h/background.getHeight());
        double iw=background.getWidth()*s,ih=background.getHeight()*s;
        double x=(w-iw)/2,y=(h-ih)/2;
        g.drawImage(background,x,y,iw,ih);
        if(mode.equals("web-screen")) {
            double sx=x+rx*iw,sy=y+ry*ih,sw=rw*iw,sh=rh*ih;
            double scale=Math.min(sw/1000,sh/650);
            g.save();g.beginPath();g.rect(sx,sy,sw,sh);g.clip();
            g.setFill(Color.web("#254e60"));g.fillRect(sx,sy,sw,sh);
            g.translate(sx+(sw-1000*scale)/2,sy+(sh-650*scale)/2);g.scale(scale,scale);
            software(g);g.restore();return;
        }
        g.save();g.translate(x+rx*iw,y+ry*ih);g.scale(rw*iw/1000,rh*ih/600);
        g.beginPath();g.rect(0,0,1000,600);g.clip();
        if(mode.equals("market-screen"))market(g);else software(g);
        g.restore();
    }
    private void market(GraphicsContext g){
        g.setFill(Color.web("#081e2b",.98));g.fillRect(0,0,1000,600);
        text(g,"S&P 500 during the financial crisis",58,45,30,"#e9edf0");
        text(g,"Quarter-end closing levels • 2007–2009",59,78,20,"#a3bec8");
        if(marketValues.length<2){text(g,"Run FIX_SCENES.sql, then reopen this entry to load the chart data.",58,230,23,"#e9edf0");return;}
        double left=80,top=125,cw=600,ch=345,min=500,max=1600;
        for(int tick=600;tick<=1600;tick+=200){double y=top+(max-tick)/(max-min)*ch;g.setStroke(Color.web("#86adbc",.25));g.setLineWidth(1);g.strokeLine(left,y,left+cw,y);text(g,Integer.toString(tick),18,y+6,18,"#b6cbd2");}
        long first=java.time.LocalDate.parse(marketDates[0]).toEpochDay(),last=java.time.LocalDate.parse(marketDates[marketDates.length-1]).toEpochDay();
        double[] xs=new double[marketValues.length],ys=new double[xs.length];
        int selected=Math.min(xs.length-1,(int)((time%30)/24*(xs.length-1)));
        for(int i=0;i<xs.length;i++){xs[i]=left+(java.time.LocalDate.parse(marketDates[i]).toEpochDay()-first)*cw/(last-first);ys[i]=top+(max-marketValues[i])/(max-min)*ch;}
        g.setStroke(Color.web("#9bb3ba",.45));g.setLineWidth(2);g.beginPath();for(int i=0;i<xs.length;i++){if(i==0)g.moveTo(xs[i],ys[i]);else g.lineTo(xs[i],ys[i]);}g.stroke();
        for(int i=1;i<=selected;i++){g.setStroke(Color.web(marketValues[i]<marketValues[i-1]?"#f08079":"#7bc4ac"));g.setLineWidth(4);g.strokeLine(xs[i-1],ys[i-1],xs[i],ys[i]);}
        for(int i=0;i<xs.length;i++){g.setFill(Color.web(i==selected?"#ffe2aa":"#789da9"));g.fillOval(xs[i]-4,ys[i]-4,8,8);if(i%3==0||i==xs.length-1){text(g,marketDates[i].substring(0,7),xs[i]-29,501,16,"#b6cbd2");}}
        g.setStroke(Color.web("#eac98a",.5));g.setLineWidth(1);g.strokeLine(xs[selected],top,xs[selected],top+ch);g.setFill(Color.web("#ffe2aa"));g.fillOval(xs[selected]-7,ys[selected]-7,14,14);
        g.setFill(Color.web("#163542"));g.fillRoundRect(719,117,253,354,12,12);
        text(g,"Selected close",741,155,21,"#b8d1d7");text(g,marketDates[selected],741,196,22,"#edf3ed");
        text(g,String.format(java.util.Locale.ROOT,"%,.2f",marketValues[selected]),740,248,34,"#ffe2aa");
        text(g,"index points",742,278,18,"#b8d1d7");
        double change=(marketValues[selected]/marketValues[0]-1)*100;
        text(g,String.format(java.util.Locale.ROOT,"%+.1f%%",change),741,335,29,change<0?"#f19b90":"#8ed4ba");text(g,"since first shown close",741,365,16,"#b8d1d7");
        text(g,marketValues.length+" dated observations",741,417,17,"#b8d1d7");
        text(g,"The line connects sampled quarter-end closes, not daily prices.",58,548,20,"#d1dbd7");
        text(g,"Historical data • UBS / SEC EDGAR",58,580,17,"#92b5c0");
    }
    static Image preview(String id,JsonNode settings,Image picture){
        PhotoActivityPane pane=new PhotoActivityPane(id,settings);pane.pause();pane.background=picture;pane.time=12;
        pane.canvas.widthProperty().unbind();pane.canvas.heightProperty().unbind();pane.canvas.setWidth(1000);pane.canvas.setHeight(667);pane.draw();
        Image result=pane.canvas.snapshot(null,null);pane.close();return result;
    }
    void followLink(){time=18;play();draw();}
    private double[] webPoint(double x,double y){
        if(background==null)return new double[]{-1,-1};
        double s=Math.min(canvas.getWidth()/background.getWidth(),canvas.getHeight()/background.getHeight());
        double iw=background.getWidth()*s,ih=background.getHeight()*s;
        double sw=rw*iw,sh=rh*ih,scale=Math.min(sw/1000,sh/650);
        if(scale<=0)return new double[]{-1,-1};
        double sx=(canvas.getWidth()-iw)/2+rx*iw+(sw-1000*scale)/2;
        double sy=(canvas.getHeight()-ih)/2+ry*ih+(sh-650*scale)/2;
        return new double[]{(x-sx)/scale,(y-sy)/scale};
    }
    private boolean onLink(double x,double y){return time%30>=8 && x>=110 && x<=610 && y>=340 && y<=390;}
    private static void text(GraphicsContext g,String text,double x,double y,double size,String color){g.setFont(Font.font("Georgia",size));g.setFill(Color.web(color));g.fillText(text,x,y);}
    private static double ease(double p){p=Math.max(0,Math.min(1,p));return p*p*(3-2*p);}
    private void software(GraphicsContext g){
        double phase=time%30;
        boolean second=phase>=18;
        boolean loading=phase<8 || phase>=18&&phase<21;
        double progress=phase<5?0:phase<8?(phase-5)/3:phase<18?1:phase<21?(phase-18)/3:1;
        g.setFill(Color.web("#254e60"));g.fillRect(0,0,1000,650);
        g.setFill(Color.web("#3c6676"));for(int i=0;i<7;i++)g.fillRect(0,i*100,1000,1);
        text(g,"THE WEB OPENS A WINDOW",48,42,23,"#eff3e9");
        g.setFill(Color.web("#162f3c",.45));g.fillRoundRect(58,77,904,476,12,12);
        g.setFill(Color.web("#e4e3dc"));g.fillRoundRect(46,65,904,476,10,10);
        g.setFill(Color.web("#c4d0d1"));g.fillRoundRect(47,66,902,40,9,9);
        g.setFill(Color.web("#174855"));g.fillOval(61,78,15,15);
        text(g,"World Wide Web",91,93,22,"#193640");
        text(g,"–   □   ×",835,93,20,"#35525b");
        text(g,"Back     Home     Reload",67,132,18,"#28434b");
        g.setFill(Color.web("#f9faf5"));g.fillRoundRect(67,148,858,38,4,4);
        String address=second?"http://info.cern.ch/hypertext/WWW/TheProject.html":"http://info.cern.ch/";
        int typed=phase<5?Math.min(address.length(),(int)(phase/4*address.length())):address.length();
        text(g,address.substring(0,typed)+(phase<5&&(int)(phase*2)%2==0?"|":""),81,174,18,"#234651");
        g.setFill(Color.web("#faf9f2"));g.fillRect(67,201,858,288);
        if(!loading) {
            double since=second?phase-21:phase-8;
            text(g,second?"Following a hyperlink":"World Wide Web",102,247,32,"#263d43");
            g.setStroke(Color.web("#d6d4c9"));g.setLineWidth(1);g.strokeLine(102,265,888,265);
            String[] lines=second?new String[]{"One click connects documents across computers.","A page can point to another page anywhere on the web.","Explore, follow a link, and discover something new."}:new String[]{"A world of connected information.","Read a document, then follow a link to another page.","Simple pages. Shared knowledge. New connections."};
            int letters=(int)(Math.max(0,since)*42);
            for(int i=0;i<lines.length;i++){int n=Math.min(lines[i].length(),Math.max(0,letters));text(g,lines[i].substring(0,n),103,298+i*27,20,"#465454");letters-=lines[i].length();}
            if(since>3){String link=second?"↳ Explore another connection":"↳ Discover how links connect pages";text(g,link,112,383,21,hovered?"#975d26":"#1c6599");g.setStroke(Color.web(hovered?"#975d26":"#1c6599"));g.strokeLine(133,388,second?435:469,388);}
            if(since>4){g.setFill(Color.web("#e9eee6"));g.fillRoundRect(101,412,785,51,5,5);text(g,"The web software was released freely in 1993.",117,444,20,"#3c594e");}
        } else {
            text(g,phase<5?"Typing a web address…":"Requesting the next document…",102,266,26,"#435e66");
            for(int i=0;i<3;i++){g.setFill(Color.web("#d6ded9"));g.fillRoundRect(103,293+i*45,600-i*95,13,5,5);}
        }
        g.setFill(Color.web("#cad6d1"));g.fillRect(67,503,858,8);g.setFill(Color.web("#3f8e89"));g.fillRect(67,503,858*progress,8);
        text(g,loading?(phase<5?"Entering address":"Loading document"):"Document ready",69,532,16,"#3c5459");
        // A visible request travels to a server, then a response returns to the browser.
        g.setStroke(Color.web("#9ab7bd"));g.setLineWidth(2);g.strokeLine(180,592,806,592);
        g.setFill(Color.web("#d5dfda"));g.fillRoundRect(90,566,90,51,6,6);g.fillRoundRect(805,566,100,51,6,6);
        text(g,"Browser",98,597,17,"#234651");text(g,"Server",826,597,17,"#234651");
        for(int i=0;i<4;i++){double p=(time*.34-i*.17)%1;if(p<0)p+=1;boolean returning=((int)(time/3))%2==1;double px=returning?806-626*p:180+626*p;g.setFill(Color.web(returning?"#edc982":"#8ed4cf"));g.fillRoundRect(px-6,586,13,13,3,3);}
        double cx=760,cy=421;
        if(phase>=14&&phase<18){double move=ease((phase-14)/3);cx=760+(350-760)*move;cy=421+(379-421)*move;}
        if(phase>=18&&phase<21){cx=350;cy=379;}
        g.setFill(Color.web("#fefdf6"));g.setStroke(Color.web("#253f49"));g.setLineWidth(2);
        double[] xs={cx,cx,cx+10,cx+19,cx+26,cx+16};double[] ys={cy,cy+35,cy+25,cy+42,cy+38,cy+20};g.fillPolygon(xs,ys,6);g.strokePolygon(xs,ys,6);
        if(phase>=17.5&&phase<18.5){double r=12+18*Math.sin(Math.PI*(phase-17.5));g.setStroke(Color.web("#b7853b"));g.setLineWidth(3);g.strokeOval(cx-r,cy-r,r*2,r*2);}
    }
    @Override public void close(){closed=true;pause();timer.stop();}
}
