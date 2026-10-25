package zoro.benojir.callrecorder.helpers;

/** Android audio-source values, kept independent of the framework for regression tests. */
public final class AudioSettings {
    private AudioSettings() { }
    public static int[] sources(String selection, boolean privileged, boolean phone) {
        if ("auto".equals(selection)) return privileged && phone ? new int[]{4, 1} : new int[]{6, 1};
        int source;
        try { source = Integer.parseInt(selection); } catch (NumberFormatException e) { source = 1; }
        if (source < 1 || source > 7) source = 1;
        if (!privileged && source >= 2 && source <= 4) return new int[0];
        return new int[]{source};
    }
    public static int sampleRate(String codec, String value) {
        if ("amr_nb".equals(codec)) return 8000;
        if ("amr_wb".equals(codec)) return 16000;
        return bounded(value, 44100, 8000, 48000);
    }
    public static int bitRate(String codec, String value) {
        if ("amr_nb".equals(codec)) return 12200;
        if ("amr_wb".equals(codec)) return 23850;
        return bounded(value, 128000, 32000, 256000);
    }
    private static int bounded(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (NumberFormatException e) { return fallback; }
    }
    public static String sourceName(int source) {
        switch (source) {
            case 2: return "Voice uplink";
            case 3: return "Voice downlink";
            case 4: return "Voice call";
            case 5: return "Camcorder microphone";
            case 6: return "Voice recognition microphone";
            case 7: return "Voice communication microphone";
            default: return "Microphone";
        }
    }
}
