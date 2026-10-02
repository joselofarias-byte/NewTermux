import com.newtermux.features.BackupArchivePolicy;

public final class BackupArchivePolicyTest {
    private static int checks;
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Unsafe archive accepted");
    }
    public static void main(String[] args) {
        for (String path : new String[]{"/etc/passwd", "../usr", "a/../../usr", "a//b", "a/./b", "a\\b", "a\0b"})
            rejects(() -> BackupArchivePolicy.relativePath(path, false));
        BackupArchivePolicy policy = new BackupArchivePolicy(10);
        policy.entry("META-INF/header.json");
        rejects(() -> policy.entry("META-INF/header.json"));
        policy.payload(6);
        rejects(() -> policy.payload(5));
        policy.payload(4);
        rejects(() -> policy.payload(1));
        policy.destination("home", 0, "", "dir", "");
        policy.destination("home", 0, "file", "file", "data/home/0/file");
        rejects(() -> policy.destination("home", 0, "file", "file", "data/home/0/file"));
        rejects(() -> policy.destination("home", 0, "other", "file", "data/models/0/file"));
        rejects(() -> policy.destination("home", 0, "", "symlink", ""));
        rejects(() -> policy.destination("home", -1, "file", "file", "data/home/0/file"));
        rejects(() -> policy.destination("home", 0, "foo/", "dir", ""));
        rejects(() -> policy.destination("home", 0, "foo", "unknown", ""));
        BackupArchivePolicy huge = new BackupArchivePolicy(Long.MAX_VALUE);
        huge.payload(Integer.MAX_VALUE);
        if (!"data/proot_debian/0/rootfs/bin/sh".equals(
                BackupArchivePolicy.dataEntryName("proot:debian", 0, "rootfs/bin/sh")))
            throw new AssertionError("v1 compatibility broken");

        // Android/Linux permits backslash as a literal filename character. It remains
        // forbidden in ZIP entry names but must be representable through metadata.
        BackupArchivePolicy.logicalPath("usr/share/test/foo\\bar", false);
        String escapedBackslash = BackupArchivePolicy.dataEntryName(
                "proot:debian", 0, "usr/share/test/foo\\bar");
        if (!"data/proot_debian/0/usr/share/test/foo%5Cbar".equals(escapedBackslash))
            throw new AssertionError("backslash filename was not escaped safely");

        String literalPercent = BackupArchivePolicy.dataEntryName(
                "proot:debian", 0, "usr/share/test/foo%5Cbar");
        if (!"data/proot_debian/0/usr/share/test/foo%255Cbar".equals(literalPercent)
                || literalPercent.equals(escapedBackslash))
            throw new AssertionError("archive path escaping is not collision-free");

        BackupArchivePolicy unusual = new BackupArchivePolicy(10);
        unusual.destination("proot:debian", 0, "usr/share/test/foo\\bar", "file", escapedBackslash);

        System.out.println("PASS: " + checks + " unsafe archive cases rejected; valid v1 and Android filename paths accepted");
    }
}
