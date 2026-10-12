package android.content.res;
public class Resources {
 public String provider = "com.google.android.googlequicksearchbox";
 public int getIdentifier(String name,String type,String owner){return 1;}
 public String getString(int id){return provider;}
}
