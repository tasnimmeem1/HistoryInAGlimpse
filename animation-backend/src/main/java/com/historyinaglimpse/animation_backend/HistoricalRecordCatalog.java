package com.historyinaglimpse.animation_backend;

import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public final class HistoricalRecordCatalog {
    private static final List<HistoricalRecord> BASE_RECORDS = List.of(
            base("Unification of Egypt", -3100, 1, "Around 3100 BCE, Egypt was unified into a single political entity. This marks the beginning of the Early Dynastic Period. The Narmer Palette records imagery associated with this transformation. The event starts the chart's sequence of Egyptian kingdoms.", "https://egymonuments.gov.eg/en/historical-periods/early-dynastic-period"),
            base("Alexander enters Persepolis", -330, 2, "In 330 BCE, Alexander reached Persepolis during his conquest of the Achaemenid Persian Empire. The palace complex was burned. This event marks the transition from Achaemenid rule to the world shaped by Alexander's campaigns and their successors.", "https://www.iranicaonline.org/articles/alexander-the-great-356-23-bc/"),
            base("Qin unifies China", -221, 4, "In 221 BCE, Qin conquered the remaining rival states and established China's first imperial dynasty. Although Qin rule lasted only about fifteen years, its centralized model influenced later Chinese history. The chart places this event before the Han dynasty.", "https://asia.si.edu/education/educator-resources/teaching-china-with-the-smithsonian/explore-by-dynasty/qin-dynasty/"),
            base("Beginning of Imperial Rome", -27, 3, "The imperial period of Rome is conventionally dated from 27 BCE. Augustus became the first Roman emperor. This begins the chart's western imperial Roman band, which extends to 476 CE. The eastern empire continued beyond that date.", "https://education.nationalgeographic.org/resource/imperial-rome/"),
            base("End of the Western Roman Empire", 476, 3, "The year 476 CE conventionally marks the end of the Western Roman Empire. It does not mark the end of the eastern empire. The chart therefore ends the western Roman band while keeping the eastern Roman chronology visible.", "https://education.nationalgeographic.org/resource/imperial-rome/"),
            base("The Hijrah", 622, 5, "In 622 CE, Prophet Muhammad and his followers migrated from Mecca to Medina. This migration, known as the Hijrah, became the epoch of the Islamic calendar. The event provides an important chronological marker in the chart's Islamic-world lane.", "https://en.wikisource.org/wiki/1911_Encyclop%C3%A6dia_Britannica/Hejira"),
            base("Genghis Khan proclaimed", 1206, 2, "In 1206, an assembly of Mongol chiefs elected Genghis Khan as supreme ruler of the Mongolian tribes. The unification provided the foundation for the expansion of the Mongol Empire across Asia and into Europe.", "https://www.iranicaonline.org/articles/iran-ii2-islamic-period-page-3/"),
            base("Fall of Constantinople", 1453, 3, "In 1453, Ottoman forces captured Constantinople. The conquest ended the Byzantine, or Eastern Roman, Empire and changed the city's place in the Ottoman world. On the chart, the eastern Roman band ends at this event.", "https://academic.oup.com/book/62441"),
            base("Beginning of Mughal rule", 1526, 6, "The Mughal dynasty began ruling in India in 1526. Its courts became renowned for wealth, culture and the arts. The chart's Mughal band extends to 1858 using the period given by The Metropolitan Museum of Art.", "https://www.metmuseum.org/exhibitions/listings/2001/jeweled-arts-of-mughal-india"),
            base("Declaration of Independence", 1776, 7, "On July 4, 1776, the Continental Congress adopted the Declaration of Independence. The document explained the colonies' decision to separate from British rule. Adoption on July 4 and the later signing of the engrossed copy were different steps.", "https://www.archives.gov/milestone-documents/declaration-of-independence"),
            base("French Revolution begins", 1789, 7, "In 1789, the French Revolution began. Unrest challenged the monarchy and the existing political order. Thomas Jefferson, then in Paris, witnessed the early events. The chart places this turning point beside the other late eighteenth-century Atlantic events.", "https://www.archives.gov/exhibits/eyewitness/assets/html/1.intro.html")
    );

    private final List<HistoricalRecord> records;
    private final Map<String, HistoricalRecord> byId;

    public HistoricalRecordCatalog() {
        List<HistoricalRecord> loaded = new ArrayList<>(BASE_RECORDS);
        readTsv("additional-events.tsv", 7, loaded);
        readTsv("expanded-events.tsv", 8, loaded);
        loaded.sort(Comparator.comparingInt(HistoricalRecord::year).thenComparing(HistoricalRecord::title));
        Map<String, Integer> collisions = new HashMap<>();
        List<HistoricalRecord> unique = new ArrayList<>();
        for (HistoricalRecord record : loaded) {
            String base = slug(record.title()) + "-" + Math.abs(record.year());
            int collision = collisions.merge(base, 1, Integer::sum);
            String id = collision == 1 ? base : base + "-" + collision;
            unique.add(new HistoricalRecord(id, record.title(), record.year(), record.lane(),
                    record.date(), record.description(), record.source()));
        }
        records = List.copyOf(unique);
        byId = records.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                HistoricalRecord::id, record -> record));
    }

    public List<HistoricalRecord> all() {
        return records;
    }

    public Optional<HistoricalRecord> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    private static void readTsv(String name, int expectedFields, List<HistoricalRecord> output) {
        try (var stream = HistoricalRecordCatalog.class.getResourceAsStream("/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing historical catalog resource: " + name);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] fields = line.split("\t", -1);
                    if (fields.length != expectedFields)
                        throw new IllegalStateException("Invalid historical catalog entry in " + name);
                    output.add(new HistoricalRecord("", fields[0], Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]), fields[3], fields[4], fields[5]));
                }
            }
        } catch (IOException | NumberFormatException ex) {
            throw new IllegalStateException("Cannot load historical catalog resource: " + name, ex);
        }
    }

    private static HistoricalRecord base(String title, int year, int lane, String description, String source) {
        String date = Math.abs(year) + (year < 0 ? " BCE" : " CE");
        return new HistoricalRecord("", title, year, lane, date, description, source);
    }

    private static String slug(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
