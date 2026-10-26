package org.example.historyinaglimpse;
final class BatchCatalog {
 static String id(HistoricalEvent e){return switch(e.title()){
 case "Declaration of Independence"->"declaration-independence";
 case "French Revolution begins"->"french-revolution";
 case "Stockton–Darlington Railway"->"stockton-darlington";
 case "Early photographic processes"->"early-photography";
 case "Darwin's Origin of Species"->"darwin-evolution";
 case "Emancipation Proclamation"->"emancipation-proclamation";
 case "Bell's telephone patent"->"bell-telephone";
 case "Edison's electric lighting"->"edison-light";
 case "Wright brothers powered flight"->"wright-flight";
 case "First World War"->"first-world-war";
 case "Second World War"->"second-world-war";
 case "United Nations established"->"united-nations";
 case "United States Civil Rights Act"->"civil-rights";
 case "Apollo 11 Moon landing"->"moon-landing";
 case "Web software released freely"->"web-software";
 case "First-generation computers"->"first-generation-computers";
 case "September 11 attacks"->"september-11";
 case "Global financial crisis intensifies"->"financial-crisis";
 case "COVID-19 characterized as pandemic"->"covid-19";
 default->"";};}
 private BatchCatalog(){}
}
