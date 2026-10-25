package zoro.benojir.callrecorder.helpers;

public final class RecordingCategory {
    private RecordingCategory() { }
    public static final String[] VALUES = {"phone", "whatsapp", "telegram", "messenger", "instagram", "manual"};
    public static String forPackage(String name) {
        if ("com.whatsapp".equals(name) || "com.whatsapp.w4b".equals(name)) return "whatsapp";
        if ("org.telegram.messenger".equals(name) || "org.telegram.messenger.web".equals(name)
                || "org.thunderdog.challegram".equals(name)) return "telegram";
        if ("com.facebook.orca".equals(name) || "com.facebook.mlite".equals(name)) return "messenger";
        if ("com.instagram.android".equals(name)) return "instagram";
        return null;
    }
    public static String safeCategory(String category) {
        for (String value : VALUES) if (value.equals(category)) return value;
        return "manual";
    }
    public static String label(String category) {
        switch (safeCategory(category)) {
            case "phone": return "Phone calls";
            case "whatsapp": return "WhatsApp";
            case "telegram": return "Telegram";
            case "messenger": return "Messenger";
            case "instagram": return "Instagram";
            default: return "Manual";
        }
    }
}
