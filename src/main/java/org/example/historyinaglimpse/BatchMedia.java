package org.example.historyinaglimpse;
import com.fasterxml.jackson.databind.*;
import javafx.scene.image.Image;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;
/** Private media remains behind the Spring Boot proxy; successful assets are reused. */
final class BatchMedia {
 private static final Map<String,CompletableFuture<Image>> IMAGES=new ConcurrentHashMap<>();
 static String base(){return System.getProperty("history.animation.backend","http://127.0.0.1:8080").replaceAll("/+$","");}
 static CompletableFuture<JsonNode> metadata(String id){return CompletableFuture.supplyAsync(()->{try{JsonNode n=new ObjectMapper().readTree(bytes(id,"",1024*1024));if(!id.equals(n.path("id").asText()))throw new IOException("Wrong entry");return n;}catch(IOException e){throw new IllegalStateException(e);}});}
 static CompletableFuture<Image> image(String id,String route){String key=base()+"/"+id+"/"+route;var f=IMAGES.computeIfAbsent(key,k->CompletableFuture.supplyAsync(()->{Image image=new Image(new ByteArrayInputStream(bytes(id,route,12*1024*1024)));if(image.isError()||image.getWidth()<1||image.getHeight()<1)throw new IllegalStateException("Invalid image");return image;}));f.whenComplete((r,e)->{if(e!=null)IMAGES.remove(key,f);});return f;}
 private static byte[] bytes(String id,String route,int limit){if(!id.matches("[a-z0-9-]+")||!Set.of("","background","characters","foreground","map","document").contains(route))throw new IllegalArgumentException();HttpURLConnection c=null;try{c=(HttpURLConnection)URI.create(base()+"/api/era-scenes/"+id+(route.isEmpty()?"":"/"+route)).toURL().openConnection();c.setConnectTimeout(5000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);if(c.getResponseCode()!=200)throw new IOException("Media unavailable");try(var in=c.getInputStream()){byte[] b=in.readNBytes(limit+1);if(b.length==0||b.length>limit)throw new IOException("Invalid size");return b;}}catch(IOException e){throw new IllegalStateException(e);}finally{if(c!=null)c.disconnect();}}
 private BatchMedia(){}
}
