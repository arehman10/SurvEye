import java.util.ArrayList;
import java.util.List;

/** Opt-in integration test against the supplied real World Bank archive (not bundled). */
public final class OfficialBoundaryMatrix {
    private static final String[] COUNTRIES = {"Sri Lanka", "Nepal", "Pakistan", "Australia", "Fiji", "France", "Brazil", "United States of America"};
    // Independently counted from NAM_0/ISO_A3/ISO_A2/WB_A3 in the supplied DBF using pyshp.
    // Australia includes Ashmore and Cartier; France includes Clipperton, as coded by this archive.
    private static final int[] EXPECTED = {25, 77, 138, 566, 15, 107, 5506, 3144};
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Usage: OfficialBoundaryMatrix boundary-archive.zip");
        for (int i = 0; i < COUNTRIES.length; i++) {
            long start = System.nanoTime();
            List<String> warnings = new ArrayList<String>();
            BoundaryMap.MapGeometry g = BoundaryMap.load(COUNTRIES[i], args[0], warnings);
            if (!g.admin2 || g.featureCount != EXPECTED[i] || g.encodedFeatures.size() != EXPECTED[i] || g.featureLabels.size() != EXPECTED[i]) {
                throw new AssertionError(COUNTRIES[i] + ": feature/name counts differ from independent DBF counts");
            }
            int rings = 0;
            for (int j = 0; j < g.encodedFeatures.size(); j++) {
                rings += g.encodedFeatures.get(j).size();
                if (g.featureLabels.get(j).trim().isEmpty()) throw new AssertionError("Unnamed feature in " + COUNTRIES[i]);
            }
            if (!(g.minLon < g.maxLon && g.minLat < g.maxLat)) throw new AssertionError("Invalid extent");
            if (COUNTRIES[i].equals("Fiji") && g.maxLon - g.minLon > 50) throw new AssertionError("Fiji wraps around the entire world");
            System.out.printf(java.util.Locale.ROOT, "PASS %s | features=%d | rings=%d | lon=[%.5f,%.5f] | lat=[%.5f,%.5f] | load=%.3fs | warnings=%s%n",
                COUNTRIES[i], g.featureCount, rings, g.minLon, g.maxLon, g.minLat, g.maxLat, (System.nanoTime()-start)/1e9, warnings);
        }
    }
}
