package android.content;
public class Context {
 public android.content.pm.PackageManager manager = new android.content.pm.PackageManager();
 public android.content.res.Resources resources = new android.content.res.Resources();
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public android.content.pm.PackageManager getPackageManager(){return manager;}
 public android.content.res.Resources getResources(){return resources;}
}
