package android.provider;
import android.content.ContentResolver;
public class Settings {
    public static class System {
        public static int getInt(ContentResolver resolver, String key, int fallback) {
            return resolver.values.getOrDefault(key, fallback);
        }
    }
}
