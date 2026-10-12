package android.content;
import android.content.res.Resources;
public class Context {
    private final Resources resources = new Resources();
    private final ContentResolver resolver = new ContentResolver();
    public ContentResolver getContentResolver() { return resolver; }
    public Resources getResources() { return resources; }
    public String getPackageName() { return "clock.plugin"; }
}
