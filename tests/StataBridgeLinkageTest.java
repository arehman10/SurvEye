import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Verifies that bridge-level linkage failures still honor the status contract. */
public final class StataBridgeLinkageTest {
    private StataBridgeLinkageTest() {}

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("surveye-bridge-linkage-");
        try {
            Path config = temporary.resolve("config.tsv");
            Path status = temporary.resolve("status.tsv");
            int returnCode = org.worldbank.surveye.StataPlugin.stata(new String[]{
                    config.toString(), status.toString()
            });
            check(returnCode == 0, "Bridge did not use the fallback status contract: " + returnCode);
            check(Files.isRegularFile(status), "Bridge fallback status file was not created.");
            List<String> lines = Files.readAllLines(status, StandardCharsets.UTF_8);
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (String line : lines) {
                int tab = line.indexOf('\t');
                check(tab > 0 && line.indexOf('\t', tab + 1) < 0,
                        "Malformed bridge status record: " + line);
                String key = line.substring(0, tab);
                check(!values.containsKey(key), "Duplicate bridge status key: " + key);
                values.put(key, line.substring(tab + 1));
            }
            check("0".equals(values.get("success")), "Bridge fallback did not report failure: " + values);
            check(values.get("message") != null && values.get("message").contains("ClassNotFoundException"),
                    "Linkage detail is missing: " + values);
            check("bridge".equals(values.get("engine_version")),
                    "Bridge fallback must distinguish itself from the engine version: " + values);
            check(values.size() == 3, "Bridge fallback contains unexpected status fields: " + values);
            System.out.println("PASS Stata bridge linkage-failure status contract");
        } finally {
            if (Files.exists(temporary.resolve("status.tsv"))) Files.delete(temporary.resolve("status.tsv"));
            Files.deleteIfExists(temporary);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
