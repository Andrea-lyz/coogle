package io.github.zopulus.coogle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Runs production shell commands only against isolated module directories. */
public final class CompanionControlTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("coogle-control");
        Path active = root.resolve("active"), pending = root.resolve("pending");
        try {
            check(run(false, active, pending).equals("MISSING"), "Missing module not reported");
            Files.createDirectories(active);
            Files.writeString(active.resolve("module.prop"), "id=coogle_companion\n");
            check(run(true, active, pending).equals("UPDATE_REQUIRED"), "Old module accepted as controllable");
            check(!Files.exists(active.resolve("features.conf")), "Old module was modified");
            Files.writeString(active.resolve("module.prop"), "id=coogle_companion\ncontrolVersion=1\n");
            Files.writeString(active.resolve("features.sh"), "fixture\n");
            Files.writeString(active.resolve("applied-features.conf"), "hey=0\n");
            check(run(false, active, pending).equals("READY"), "Matching state not ready");
            check(run(true, active, pending).equals("PENDING"), "Changed state omitted reboot requirement");
            check(Files.readString(active.resolve("features.conf")).equals("hey=1\n"), "Hey switch not written");
            Files.createDirectories(pending);
            Files.copy(active.resolve("module.prop"), pending.resolve("module.prop"));
            Files.copy(active.resolve("features.sh"), pending.resolve("features.sh"));
            check(run(false, active, pending).equals("PENDING"), "Staged module not reported");
            check(Files.readString(active.resolve("features.conf")).equals("hey=0\n")
                    && Files.readString(pending.resolve("features.conf")).equals("hey=0\n"), "Staged update lost switch state");
            Files.createFile(pending.resolve("disable"));
            check(run(false, active, pending).equals("DISABLED"), "Disabled module accepted");
            System.out.println("PASS: Hey Google mirror, staged updates, reboot requirement and legacy detection");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static String run(boolean enabled, Path active, Path pending) throws Exception {
        String command = CompanionInstaller.controlCommand(enabled)
                .replace("/data/adb/modules/coogle_companion", unix(active))
                .replace("/data/adb/modules_update/coogle_companion", unix(pending));
        Process process = new ProcessBuilder("C:/Program Files/Git/bin/bash.exe", "-c", command).redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new AssertionError(result);
        return result;
    }
    private static String unix(Path path) {
        String value = path.toString().replace('\\', '/');
        return "/" + Character.toLowerCase(value.charAt(0)) + value.substring(2);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
