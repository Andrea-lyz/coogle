package android.content.pm;
public class PackageManager {
 public boolean installed = true;
 public int flags = 1, reads;
 public static class NameNotFoundException extends Exception {}
 public ApplicationInfo getApplicationInfo(String name, int options) throws NameNotFoundException {
  reads++;
  if (!installed) throw new NameNotFoundException();
  ApplicationInfo info = new ApplicationInfo(); info.flags = flags; return info;
 }
}
