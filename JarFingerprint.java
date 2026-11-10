import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prints a fingerprint of what is INSIDE a jar: every class and resource, by
 * name and content, ignoring the things that change on every build (file
 * dates, compression, entry order, and the manifest's Created-By line).
 *
 * Two jars with the same content fingerprint contain exactly the same engine,
 * even when their ordinary sha256 differs because they were packed on
 * different machines. Use it to confirm that a jar you rebuilt yourself is the
 * one that was tested:
 *
 *     java scripts/JarFingerprint.java surveye.jar
 *     java scripts/JarFingerprint.java surveye.jar other.jar      (compare)
 *     java scripts/JarFingerprint.java --list surveye.jar         (per entry)
 *
 * Needs Java 11 or newer to run as a single file. Standard library only.
 */
public final class JarFingerprint {
    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    public static void main(String[] args) throws Exception {
        boolean list = args.length > 0 && args[0].equals("--list");
        int first = list ? 1 : 0;
        if (args.length - first < 1) {
            System.err.println("usage: java scripts/JarFingerprint.java [--list] <jar> [<jar> ...]");
            System.exit(2);
        }
        String reference = null;
        boolean same = true;
        for (int i = first; i < args.length; i++) {
            Map<String, String> entries = entries(args[i]);
            String print = fingerprint(entries);
            System.out.println(print + "  " + args[i] + "  (" + entries.size() + " entries)");
            if (list) for (Map.Entry<String, String> e : entries.entrySet()) System.out.println("  " + e.getValue() + "  " + e.getKey());
            if (reference == null) reference = print; else if (!reference.equals(print)) same = false;
        }
        if (args.length - first > 1) {
            System.out.println(same ? "SAME content" : "DIFFERENT content");
            if (!same) System.exit(1);
        }
    }

    /** Entry name -> sha256 of its uncompressed bytes, sorted by name. */
    static Map<String, String> entries(String jar) throws Exception {
        Map<String, String> out = new TreeMap<String, String>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                if (entry.isDirectory() || entry.getName().equals(MANIFEST)) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    out.put(entry.getName(), hex(sha256(readAll(in))));
                }
            }
        }
        return out;
    }

    static String fingerprint(Map<String, String> entries) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (Map.Entry<String, String> e : entries.entrySet()) lines.append(e.getKey()).append('\0').append(e.getValue()).append('\n');
        return hex(sha256(lines.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[65536];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }
}
