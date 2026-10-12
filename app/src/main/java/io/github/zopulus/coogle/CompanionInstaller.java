package io.github.zopulus.coogle;

import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Root is used only after the user asks to check/install the companion. */
public final class CompanionInstaller {
    private static final String DIR="/data/adb/modules/coogle_companion";
    public static String check() throws Exception {
        String output = run("d="+DIR+"; u=/data/adb/modules_update/coogle_companion; "
                +"if [ -f \"$u/module.prop\" ]; then "
                +"if [ -f \"$u/disable\" ] || [ -f \"$u/remove\" ]; then echo DISABLED; "
                +"elif grep -q '^hey=0$' \"$u/features.conf\" 2>/dev/null; then echo SUPPORT_OFF; else echo PENDING; fi; "
                +"elif [ ! -f \"$d/module.prop\" ]; then echo MISSING; "
                +"elif [ -f \"$d/disable\" ] || [ -f \"$d/remove\" ]; then echo DISABLED; "
                +"elif grep -q '^hey=0$' \"$d/features.conf\" 2>/dev/null; then echo SUPPORT_OFF; "
                +"elif [ -f \"$d/component-error\" ]; then echo DISABLED; "
                +"elif [ -f \"$d/features.conf\" ] && [ -f \"$d/applied-features.conf\" ] && ! cmp -s \"$d/features.conf\" \"$d/applied-features.conf\"; then echo PENDING; "
                +"else echo READY; fi",20).trim();
        String state = output.substring(output.lastIndexOf('\n') + 1).trim();
        if (!java.util.Set.of("MISSING", "DISABLED", "PENDING", "READY", "SUPPORT_OFF").contains(state))
            throw new java.io.IOException("无法读取附属模块状态");
        return state;
    }
    public static void install(Context context) throws Exception {
        Path zip=context.getCacheDir().toPath().resolve("coogle-companion.zip");
        try (InputStream source=context.getAssets().open("coogle-companion.zip")) { Files.copy(source,zip,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        run("/data/adb/ksu/bin/ksud module install '"+zip.toString().replace("'","'\\''")+"'",120);
    }
    private static String run(String command,int timeout) throws Exception {
        Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
        Thread drain=new Thread(() -> {
            try (InputStream input=process.getInputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while ((n=input.read(buffer))>=0) if (output.size()<65536) output.write(buffer,0,Math.min(n,65536-output.size()));
            } catch (Exception ignored) {}
        },"Coogle-root-output");
        drain.start();
        if (!process.waitFor(timeout,TimeUnit.SECONDS)) { process.destroyForcibly(); drain.join(1000); throw new java.io.IOException("操作超时"); }
        drain.join(1000);
        if (process.exitValue()!=0) throw new java.io.IOException("Root 操作失败，请确认 KernelSU 授权及挂载支持");
        return output.toString(StandardCharsets.UTF_8);
    }
    private CompanionInstaller() {}
}
