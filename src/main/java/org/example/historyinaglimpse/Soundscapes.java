package org.example.historyinaglimpse;

final class Soundscapes {
    record Setting(String file, String label) {}
    static Setting forEvent(HistoricalEvent event) {
        int index=EventCatalog.EVENTS.indexOf(event);
        if (event.illustrationSheet().isBlank() || index == 12)
            return new Setting("", "Silent scene: no people or animals");
        return switch (index) {
            case 11 -> new Setting("cloud-birds","Bird chirping from Supabase");
            case 13 -> new Setting("cloud-farming","Farming ambience");
            case 14,15,16,20,21,32,51,53,59 -> new Setting("market","Market voices, bargaining, footsteps and small clinks");
            case 2,6,26 -> new Setting("horses","Hooves, travel and wind");
            case 4,7,10 -> new Setting("crowd","Crowd voices and passing footsteps");
            case 0,1,3,5,8,9,17,18,19,22,23,24,25,27,28,29,30,31,33,34,35,36,46,50,52,55,58 ->
                new Setting("council","Low conversation, gathering voices and room sounds");
            case 38,39,42,43,44,47,48,54,57,60 -> new Setting("workshop","Workshop voices, footsteps and tools");
            default -> new Setting("","Silent scene: no people or animals");
        };
    }
    private Soundscapes() {}
}
