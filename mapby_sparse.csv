import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads a compact country outline from bundled resources or an optional
 * polygon shapefile ZIP and prepares both a compact geographic representation
 * and a legacy SVG projection.  This class has no runtime dependencies beyond
 * Java 8.
 */
public final class BoundaryMap {
    private static final int VIEW_WIDTH = 1000;
    private static final int VIEW_HEIGHT = 620;
    private static final double PADDING = 28.0;
    private static final int LON_BINS = 7200; // 0.05 degree bins
    private static final int MAX_DBF_RECORDS = 5_000_000;
    private static final int MAX_DBF_RECORD_LENGTH = 1_000_000;
    private static final int MAX_SHAPE_RECORD_BYTES = 64 * 1024 * 1024;
    private static final int MAX_RETAINED_POINTS = 2_000_000;
    private static final int MAX_OUTPUT_POINTS = 220_000;
    private static final double EPS = 1e-10;

    private BoundaryMap() {
    }

    /** Immutable map geometry consumed by the dashboard renderer. */
    public static final class MapGeometry {
        public final String displayName;
        public final String sourceLabel;
        public final String pathD;
        /**
         * Feature/ring hierarchy encoded with the standard 1e-5 degree
         * polyline delta encoding.  Longitudes use the same antimeridian-safe
         * unwrapped domain as {@link #minLon} and {@link #maxLon}.
         */
        public final List<List<String>> encodedFeatures;
        public final double minLon;
        public final double maxLon;
        public final double minLat;
        public final double maxLat;
        public final double lonCenter;
        public final int viewWidth;
        public final int viewHeight;
        public final boolean admin2;
        public final int featureCount;
        public final List<String> warnings;

        private final List<GeoFeature> features;
        private final double projectionCos;
        private final double projectionScale;
        private final double projectionOffsetX;
        private final double projectionOffsetY;

        private MapGeometry(String displayName,
                            String sourceLabel,
                            String pathD,
                            Bounds bounds,
                            double lonCenter,
                            boolean admin2,
                            int featureCount,
                            List<String> warnings,
                            List<GeoFeature> features,
                            Projection projection) {
            this.displayName = displayName;
            this.sourceLabel = sourceLabel;
            this.pathD = pathD;
            this.encodedFeatures = encodeFeatures(features);
            this.minLon = bounds.minLon;
            this.maxLon = bounds.maxLon;
            this.minLat = bounds.minLat;
            this.maxLat = bounds.maxLat;
            this.lonCenter = normalizeLongitude(lonCenter);
            this.viewWidth = VIEW_WIDTH;
            this.viewHeight = VIEW_HEIGHT;
            this.admin2 = admin2;
            this.featureCount = featureCount;
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
            this.features = Collections.unmodifiableList(new ArrayList<GeoFeature>(features));
            this.projectionCos = projection.cosLat;
            this.projectionScale = projection.scale;
            this.projectionOffsetX = projection.offsetX;
            this.projectionOffsetY = projection.offsetY;
        }

        /** Projects a longitude using the same transform as {@link #pathD}. */
        public double projectX(double lon) {
            double unwrapped = unwrapLongitude(lon, lonCenter);
            return projectionOffsetX + (unwrapped - minLon) * projectionCos * projectionScale;
        }

        /** Projects a WGS84 point's longitude into the SVG viewBox. */
        public double projectX(double lon, double lat) {
            return projectX(lon);
        }

        /** Projects a latitude using the same transform as {@link #pathD}. */
        public double projectY(double lat) {
            return projectionOffsetY + (maxLat - lat) * projectionScale;
        }

        /** Projects a WGS84 point's latitude into the SVG viewBox. */
        public double projectY(double lon, double lat) {
            return projectY(lat);
        }

        /**
         * Returns true when a WGS84 point is inside (or on the boundary of)
         * at least one retained source feature.  Holes use an even-odd test.
         */
        public boolean contains(double lon, double lat) {
            if (!isFinite(lon) || !isFinite(lat)
                    || lon < -180.0 || lon > 180.0
                    || lat < -90.0 || lat > 90.0) {
                return false;
            }
            double x = unwrapLongitude(lon, lonCenter);
            double tol = Math.max(1e-9,
                    Math.max(maxLon - minLon, maxLat - minLat) * 1e-10);
            if (x < minLon - tol || x > maxLon + tol
                    || lat < minLat - tol || lat > maxLat + tol) {
                return false;
            }
            for (GeoFeature feature : features) {
                boolean inside = false;
                for (GeoRing ring : feature.rings) {
                    int state = pointInRing(x, lat, ring.xy, tol);
                    if (state == 2) {
                        return true;
                    }
                    if (state == 1) {
                        inside = !inside;
                    }
                }
                if (inside) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Load a boundary.  When {@code boundaryZip} is null/blank, the bundled
     * 1:50m outline is used.  Otherwise a matching polygon shapefile and DBF
     * are read from the ZIP.
     */
    public static MapGeometry load(String country,
                                   String boundaryZip,
                                   List<String> warnings) {
        WarningCollector warningCollector = new WarningCollector(warnings);
        CountryRegistry registry = loadCountryRegistry();
        if (boundaryZip == null || boundaryZip.trim().isEmpty()) {
            return loadBundled(registry.resolve(country), warningCollector);
        }
        ResolvedCountry resolved = registry.resolveForExternal(country);
        return loadShapefileZip(resolved, boundaryZip.trim(), warningCollector);
    }

    private static MapGeometry loadBundled(ResolvedCountry country,
                                           WarningCollector warnings) {
        List<GeoRing> parsed = parseWorldRings(country.worldRings, country.displayName);
        LongitudeStats longitudeStats = new LongitudeStats();
        for (GeoRing ring : parsed) {
            for (int i = 0; i < ring.xy.length; i += 2) {
                longitudeStats.add(ring.xy[i]);
            }
        }
        double center = longitudeStats.center();
        Bounds bounds = new Bounds();
        List<GeoRing> unwrapped = new ArrayList<GeoRing>();
        for (GeoRing ring : parsed) {
            double[] xy = new double[ring.xy.length];
            for (int i = 0; i < ring.xy.length; i += 2) {
                xy[i] = unwrapLongitude(ring.xy[i], center);
                xy[i + 1] = ring.xy[i + 1];
                bounds.add(xy[i], xy[i + 1]);
            }
            unwrapped.add(new GeoRing(xy));
        }
        if (!bounds.isValid()) {
            throw new IllegalArgumentException("Bundled boundary for '"
                    + country.displayName + "' contains no valid polygon coordinates.");
        }
        if (longitudeStats.crossesAntimeridian()) {
            warnings.add("Boundary crosses the antimeridian; longitudes were unwrapped around "
                    + formatDegrees(center) + ".");
        }
        warnings.add("Using the bundled 1:50m country outline; supply an Admin-2 boundary ZIP for detailed internal boundaries.");
        List<GeoFeature> features = new ArrayList<GeoFeature>();
        features.add(new GeoFeature(unwrapped));
        return finishGeometry(country.displayName,
                "Bundled world boundary (1:50m)",
                features, bounds, center, false, 1, warnings.local);
    }

    private static MapGeometry loadShapefileZip(ResolvedCountry country,
                                                 String boundaryZip,
                                                 WarningCollector warnings) {
        File file = new File(boundaryZip);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Boundary ZIP not found: " + boundaryZip);
        }
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            ZipLayers layers = locateLayers(zip);
            validateProjection(zip, layers, warnings);
            DbfMatch match = readDbfMatches(zip, layers, country);
            if (match.matches.isEmpty()) {
                throw new IllegalArgumentException("Country '" + country.requested
                        + "' was not found in " + file.getName()
                        + " using NAM_0, ISO_A3, ISO_A2, or WB_A3.");
            }
            if (!match.admin2) {
                warnings.add("The supplied layer has no NAM_2/ADM2 field; it will be shown as a country boundary without confirmed Admin-2 detail.");
            }

            ShapePass first = scanShapeFile(zip, layers.shp, match.matches,
                    0.0, 0.0, false, null, warnings);
            if (first.polygonRecords == 0 || first.longitudeStats.count == 0) {
                throw new IllegalArgumentException("No polygon records for '"
                        + country.displayName + "' were found in " + layers.shp.getName() + ".");
            }
            double center = first.longitudeStats.center();
            double lonSpan = Math.max(first.longitudeStats.approximateSpan(), 1e-6);
            double latSpan = Math.max(first.maxLat - first.minLat, 1e-6);
            double centerLat = (first.minLat + first.maxLat) * 0.5;
            double tolerance = simplificationTolerance(lonSpan, latSpan, centerLat,
                    match.admin2);

            Bounds exactBounds = new Bounds();
            final List<GeoFeature> collectedFeatures = new ArrayList<GeoFeature>();
            final long[] retainedPoints = new long[]{0L};
            ShapePass second = scanShapeFile(zip, layers.shp, match.matches,
                    center, tolerance, true, new ShapeConsumer() {
                        public void accept(GeoFeature feature) {
                            retainedPoints[0] += countPoints(feature);
                            if (retainedPoints[0] > MAX_RETAINED_POINTS) {
                                throw new IllegalArgumentException("Matched boundary geometry remains too detailed after simplification (more than "
                                        + MAX_RETAINED_POINTS + " vertices). Provide a smaller or simplified boundary layer.");
                            }
                            collectedFeatures.add(feature);
                        }
                    }, warnings, exactBounds);
            List<GeoFeature> features = collectedFeatures;

            if (features.isEmpty() || !exactBounds.isValid()) {
                throw new IllegalArgumentException("The matched boundary records for '"
                        + country.displayName + "' contain no usable polygon rings.");
            }
            if (first.longitudeStats.crossesAntimeridian()) {
                warnings.add("Boundary crosses the antimeridian; longitudes were unwrapped around "
                        + formatDegrees(center) + ".");
            }
            long invalid = Math.max(first.invalidCoordinates, second.invalidCoordinates);
            if (invalid > 0) {
                warnings.add(invalid + " invalid boundary coordinate(s) were ignored.");
            }

            int before = countPoints(features);
            if (before > MAX_OUTPUT_POINTS) {
                features = reduceToPointBudget(features, tolerance,
                        (first.minLat + first.maxLat) * 0.5, warnings);
                exactBounds = boundsOf(features);
            }
            String displayName = match.displayName == null || match.displayName.isEmpty()
                    ? country.displayName : match.displayName;
            String source = stripExtension(file.getName())
                    + (match.admin2 ? " · Admin 2" : " · country boundary");
            return finishGeometry(displayName, source, features, exactBounds,
                    center, match.admin2, features.size(), warnings.local);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read boundary ZIP '"
                    + boundaryZip + "': " + safeMessage(ex), ex);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                    // Nothing useful can be done during cleanup.
                }
            }
        }
    }

    private static MapGeometry finishGeometry(String displayName,
                                              String sourceLabel,
                                              List<GeoFeature> features,
                                              Bounds bounds,
                                              double center,
                                              boolean admin2,
                                              int featureCount,
                                              List<String> warnings) {
        Projection projection = new Projection(bounds);
        String path = buildPath(features, projection);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Boundary projection produced an empty SVG path for '"
                    + displayName + "'.");
        }
        return new MapGeometry(displayName, sourceLabel, path, bounds, center,
                admin2, featureCount, warnings, features, projection);
    }

    private static CountryRegistry loadCountryRegistry() {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        Map<String, WorldCountry> countries = new LinkedHashMap<String, WorldCountry>();
        BufferedReader reader = null;
        try {
            reader = utf8Resource("/resources/country_aliases.tsv");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length >= 2 && !columns[0].trim().isEmpty()) {
                    aliases.put(normalizeKey(columns[0]), columns[1].trim());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read bundled country aliases: "
                    + safeMessage(ex), ex);
        } finally {
            closeQuietly(reader);
        }

        try {
            reader = utf8Resource("/resources/world50.tsv");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] columns = line.split("\\t", 3);
                if (columns.length != 3) {
                    throw new IllegalStateException("Malformed bundled world boundary row.");
                }
                WorldCountry world = new WorldCountry(columns[0].trim(),
                        columns[1].trim(), columns[2].trim());
                countries.put(world.id, world);
                String nameKey = normalizeKey(world.name);
                if (!aliases.containsKey(nameKey)) {
                    aliases.put(nameKey, world.id);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read bundled world boundaries: "
                    + safeMessage(ex), ex);
        } finally {
            closeQuietly(reader);
        }
        return new CountryRegistry(aliases, countries);
    }

    private static BufferedReader utf8Resource(String resource) throws IOException {
        InputStream in = BoundaryMap.class.getResourceAsStream(resource);
        if (in == null) {
            String withoutSlash = resource.startsWith("/") ? resource.substring(1) : resource;
            in = BoundaryMap.class.getClassLoader().getResourceAsStream(withoutSlash);
        }
        if (in == null) {
            throw new IOException("Classpath resource not found: " + resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static List<GeoRing> parseWorldRings(String encoded, String country) {
        List<GeoRing> rings = new ArrayList<GeoRing>();
        String[] ringStrings = encoded.split("\\|");
        for (String ringString : ringStrings) {
            String[] points = ringString.split(";");
            double[] xy = new double[points.length * 2];
            int n = 0;
            for (String point : points) {
                String[] pair = point.split(",", 2);
                if (pair.length != 2) {
                    throw new IllegalStateException("Malformed bundled coordinate for '"
                            + country + "'.");
                }
                try {
                    double lon = Double.parseDouble(pair[0]);
                    double lat = Double.parseDouble(pair[1]);
                    if (!validCoordinate(lon, lat)) {
                        throw new NumberFormatException("coordinate outside WGS84 range");
                    }
                    xy[2 * n] = normalizeLongitude(lon);
                    xy[2 * n + 1] = lat;
                    n++;
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Malformed bundled coordinate for '"
                            + country + "': " + point, ex);
                }
            }
            xy = removeDuplicateClosure(trimCoordinates(xy, n));
            if (xy.length >= 6) {
                rings.add(new GeoRing(xy));
            }
        }
        if (rings.isEmpty()) {
            throw new IllegalStateException("Bundled boundary for '" + country
                    + "' has no polygon rings.");
        }
        return rings;
    }

    private static ZipLayers locateLayers(ZipFile zip) {
        Map<String, ZipEntry> shp = new LinkedHashMap<String, ZipEntry>();
        Map<String, ZipEntry> dbf = new LinkedHashMap<String, ZipEntry>();
        Map<String, ZipEntry> prj = new LinkedHashMap<String, ZipEntry>();
        Map<String, ZipEntry> cpg = new LinkedHashMap<String, ZipEntry>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName().replace('\\', '/');
            String lower = name.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            if (dot < 0) {
                continue;
            }
            String stem = lower.substring(0, dot);
            String extension = lower.substring(dot + 1);
            if ("shp".equals(extension)) {
                shp.put(stem, entry);
            } else if ("dbf".equals(extension)) {
                dbf.put(stem, entry);
            } else if ("prj".equals(extension)) {
                prj.put(stem, entry);
            } else if ("cpg".equals(extension)) {
                cpg.put(stem, entry);
            }
        }
        List<String> candidates = new ArrayList<String>();
        for (String stem : shp.keySet()) {
            if (dbf.containsKey(stem)) {
                candidates.add(stem);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Boundary ZIP must contain matching .shp and .dbf files.");
        }
        Collections.sort(candidates);
        String selected = candidates.get(0);
        for (String candidate : candidates) {
            String key = candidate.toLowerCase(Locale.ROOT);
            if (key.contains("adm2") || key.contains("admin2")) {
                selected = candidate;
                break;
            }
        }
        return new ZipLayers(shp.get(selected), dbf.get(selected),
                prj.get(selected), cpg.get(selected));
    }

    private static void validateProjection(ZipFile zip,
                                           ZipLayers layers,
                                           WarningCollector warnings) throws IOException {
        if (layers.prj == null) {
            warnings.add("Boundary ZIP has no .prj file; WGS84 longitude/latitude coordinates are assumed.");
            return;
        }
        String projection = readSmallText(zip, layers.prj, 64 * 1024);
        String key = normalizeKey(projection);
        String upper = projection.toUpperCase(Locale.ROOT);
        if (upper.contains("PROJCS[") || upper.contains("PROJCRS[")) {
            throw new IllegalArgumentException("Projected shapefiles are not supported; provide a WGS84 (EPSG:4326) boundary ZIP.");
        }
        if (!(key.contains("wgs1984") || key.contains("wgs84")
                || key.contains("epsg4326"))) {
            warnings.add("The .prj file does not explicitly identify WGS84; coordinate ranges will be validated as longitude/latitude.");
        }
    }

    private static DbfMatch readDbfMatches(ZipFile zip,
                                           ZipLayers layers,
                                           ResolvedCountry country) throws IOException {
        Charset charset = dbfCharset(zip, layers.cpg);
        DataInputStream in = new DataInputStream(new BufferedInputStream(zip.getInputStream(layers.dbf), 64 * 1024));
        try {
            byte[] first = new byte[32];
            in.readFully(first);
            int recordCount = littleInt(first, 4);
            int headerLength = littleUnsignedShort(first, 8);
            int recordLength = littleUnsignedShort(first, 10);
            if (recordCount < 0 || recordCount > MAX_DBF_RECORDS) {
                throw new IllegalArgumentException("Malformed DBF: unreasonable record count "
                        + recordCount + ".");
            }
            if (headerLength < 33 || headerLength > 1_000_000) {
                throw new IllegalArgumentException("Malformed DBF: invalid header length "
                        + headerLength + ".");
            }
            if (recordLength < 2 || recordLength > MAX_DBF_RECORD_LENGTH) {
                throw new IllegalArgumentException("Malformed DBF: invalid record length "
                        + recordLength + ".");
            }
            byte[] rest = new byte[headerLength - 32];
            in.readFully(rest);
            List<DbfField> fields = new ArrayList<DbfField>();
            int cumulativeOffset = 1;
            for (int position = 0; position + 32 <= rest.length; position += 32) {
                if ((rest[position] & 0xff) == 0x0d) {
                    break;
                }
                String name = asciiFieldName(rest, position, 11);
                int length = rest[position + 16] & 0xff;
                if (name.isEmpty() || length <= 0
                        || cumulativeOffset + length > recordLength) {
                    throw new IllegalArgumentException("Malformed DBF field descriptor near byte "
                            + (position + 32) + ".");
                }
                fields.add(new DbfField(name, cumulativeOffset, length));
                cumulativeOffset += length;
            }
            Map<String, DbfField> byName = new HashMap<String, DbfField>();
            for (DbfField field : fields) {
                byName.put(field.name.toUpperCase(Locale.ROOT), field);
            }
            String[] matchNames = new String[]{"NAM_0", "ISO_A3", "ISO_A2", "WB_A3"};
            List<DbfField> matchFields = new ArrayList<DbfField>();
            for (String name : matchNames) {
                DbfField field = byName.get(name);
                if (field != null) {
                    matchFields.add(field);
                }
            }
            if (matchFields.isEmpty()) {
                throw new IllegalArgumentException("Boundary DBF must contain at least one of NAM_0, ISO_A3, ISO_A2, or WB_A3.");
            }
            DbfField nameField = byName.get("NAM_0");
            boolean admin2 = byName.containsKey("NAM_2")
                    || byName.containsKey("ADM2CD_C")
                    || byName.containsKey("ADM2_CODE");
            BitSet matches = new BitSet(recordCount);
            byte[] record = new byte[recordLength];
            String displayName = null;
            for (int index = 0; index < recordCount; index++) {
                try {
                    in.readFully(record);
                } catch (EOFException ex) {
                    throw new IllegalArgumentException("Malformed DBF: file ended at record "
                            + (index + 1) + " of " + recordCount + ".");
                }
                if (record[0] == '*') {
                    continue;
                }
                boolean matched = false;
                for (DbfField field : matchFields) {
                    String value = decodeField(record, field, charset);
                    if (country.matchKeys.contains(normalizeKey(value))) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    matches.set(index);
                    if (displayName == null && nameField != null) {
                        displayName = decodeField(record, nameField, charset).trim();
                    }
                }
            }
            return new DbfMatch(matches, displayName, admin2, recordCount);
        } finally {
            in.close();
        }
    }

    private static Charset dbfCharset(ZipFile zip, ZipEntry cpg) throws IOException {
        if (cpg == null) {
            return Charset.forName("windows-1252");
        }
        String value = readSmallText(zip, cpg, 1024).trim();
        String key = value.replace("\uFEFF", "").trim();
        if ("65001".equals(key)) {
            key = "UTF-8";
        } else if ("1252".equals(key)) {
            key = "windows-1252";
        }
        try {
            return Charset.forName(key);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported DBF code page in "
                    + cpg.getName() + ": " + value, ex);
        }
    }

    private static ShapePass scanShapeFile(ZipFile zip,
                                           ZipEntry shp,
                                           BitSet matches,
                                           double center,
                                           double tolerance,
                                           boolean collect,
                                           ShapeConsumer consumer,
                                           WarningCollector warnings) throws IOException {
        return scanShapeFile(zip, shp, matches, center, tolerance, collect,
                consumer, warnings, null);
    }

    private static ShapePass scanShapeFile(ZipFile zip,
                                           ZipEntry shp,
                                           BitSet matches,
                                           double center,
                                           double tolerance,
                                           boolean collect,
                                           ShapeConsumer consumer,
                                           WarningCollector warnings,
                                           Bounds exactBounds) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(zip.getInputStream(shp), 128 * 1024));
        ShapePass pass = new ShapePass();
        Set<Integer> warnedTypes = new HashSet<Integer>();
        try {
            byte[] header = new byte[100];
            in.readFully(header);
            int fileCode = bigInt(header, 0);
            int version = littleInt(header, 28);
            int declaredType = littleInt(header, 32);
            if (fileCode != 9994 || version != 1000) {
                throw new IllegalArgumentException("Malformed shapefile header in "
                        + shp.getName() + ".");
            }
            if (!(declaredType == 0 || declaredType == 5
                    || declaredType == 15 || declaredType == 25)) {
                throw new IllegalArgumentException("Shapefile must contain Polygon, PolygonZ, or PolygonM records; found type "
                        + declaredType + ".");
            }
            int sequence = 0;
            byte[] recordHeader = new byte[8];
            while (true) {
                int got = readAtMost(in, recordHeader, 0, 8);
                if (got == 0) {
                    break;
                }
                if (got != 8) {
                    throw new IllegalArgumentException("Truncated shapefile record header in "
                            + shp.getName() + ".");
                }
                int recordNumber = bigInt(recordHeader, 0);
                int words = bigInt(recordHeader, 4);
                if (words < 2 || words > MAX_SHAPE_RECORD_BYTES / 2) {
                    throw new IllegalArgumentException("Malformed shapefile record "
                            + recordNumber + ": invalid content length.");
                }
                int bytes = words * 2;
                boolean selected = matches.get(sequence);
                sequence++;
                if (!selected) {
                    skipFully(in, bytes);
                    continue;
                }
                byte[] content = new byte[bytes];
                in.readFully(content);
                ParsedShape parsed = parseShape(content, recordNumber);
                if (parsed == null) {
                    continue;
                }
                if (!(parsed.type == 5 || parsed.type == 15 || parsed.type == 25)) {
                    if (!collect && warnedTypes.add(parsed.type)) {
                        warnings.add("Unsupported matching shape type " + parsed.type
                                + " was ignored.");
                    }
                    continue;
                }
                pass.polygonRecords++;
                if (!collect) {
                    for (int i = 0; i < parsed.xy.length; i += 2) {
                        double lon = parsed.xy[i];
                        double lat = parsed.xy[i + 1];
                        if (!validCoordinate(lon, lat)) {
                            pass.invalidCoordinates++;
                            continue;
                        }
                        pass.longitudeStats.add(lon);
                        pass.minLat = Math.min(pass.minLat, lat);
                        pass.maxLat = Math.max(pass.maxLat, lat);
                    }
                } else {
                    GeoFeature feature = shapeToFeature(parsed, center, tolerance,
                            exactBounds, pass);
                    if (feature != null && !feature.rings.isEmpty() && consumer != null) {
                        consumer.accept(feature);
                    }
                }
            }
        } finally {
            in.close();
        }
        return pass;
    }

    private static ParsedShape parseShape(byte[] content, int recordNumber) {
        if (content.length < 4) {
            throw new IllegalArgumentException("Malformed shapefile record "
                    + recordNumber + ": missing shape type.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int type = buffer.getInt();
        if (type == 0) {
            return null;
        }
        if (!(type == 5 || type == 15 || type == 25)) {
            return new ParsedShape(type, new int[0], new double[0]);
        }
        if (buffer.remaining() < 40) {
            throw new IllegalArgumentException("Malformed polygon record "
                    + recordNumber + ": header is truncated.");
        }
        buffer.position(buffer.position() + 32); // bounding box
        int partCount = buffer.getInt();
        int pointCount = buffer.getInt();
        if (partCount <= 0 || pointCount < 3 || partCount > pointCount) {
            throw new IllegalArgumentException("Malformed polygon record "
                    + recordNumber + ": invalid part/point counts.");
        }
        long required = 4L * partCount + 16L * pointCount;
        if (required > buffer.remaining()) {
            throw new IllegalArgumentException("Malformed polygon record "
                    + recordNumber + ": coordinate data is truncated.");
        }
        int[] parts = new int[partCount];
        for (int i = 0; i < partCount; i++) {
            parts[i] = buffer.getInt();
            if (parts[i] < 0 || parts[i] >= pointCount
                    || (i == 0 && parts[i] != 0)
                    || (i > 0 && parts[i] <= parts[i - 1])) {
                throw new IllegalArgumentException("Malformed polygon record "
                        + recordNumber + ": invalid part index.");
            }
        }
        double[] xy = new double[pointCount * 2];
        for (int i = 0; i < pointCount; i++) {
            xy[2 * i] = buffer.getDouble();
            xy[2 * i + 1] = buffer.getDouble();
        }
        return new ParsedShape(type, parts, xy);
    }

    private static GeoFeature shapeToFeature(ParsedShape parsed,
                                             double center,
                                             double tolerance,
                                             Bounds exactBounds,
                                             ShapePass pass) {
        List<GeoRing> rings = new ArrayList<GeoRing>();
        int pointCount = parsed.xy.length / 2;
        for (int p = 0; p < parsed.parts.length; p++) {
            int start = parsed.parts[p];
            int end = p + 1 < parsed.parts.length ? parsed.parts[p + 1] : pointCount;
            GeoRing ring = makeRing(parsed.xy, start, end, center, tolerance,
                    exactBounds, pass);
            if (ring != null) {
                rings.add(ring);
            }
        }
        return rings.isEmpty() ? null : new GeoFeature(rings);
    }

    private static GeoRing makeRing(double[] source,
                                    int start,
                                    int end,
                                    double center,
                                    double tolerance,
                                    Bounds exactBounds,
                                    ShapePass pass) {
        double[] work = new double[Math.max(0, end - start) * 2];
        int n = 0;
        for (int i = start; i < end; i++) {
            double lon = source[2 * i];
            double lat = source[2 * i + 1];
            if (!validCoordinate(lon, lat)) {
                pass.invalidCoordinates++;
                continue;
            }
            double x = unwrapLongitude(lon, center);
            if (n > 0 && almostEqual(work[2 * (n - 1)], x)
                    && almostEqual(work[2 * (n - 1) + 1], lat)) {
                continue;
            }
            work[2 * n] = x;
            work[2 * n + 1] = lat;
            n++;
        }
        work = trimCoordinates(work, n);
        work = removeDuplicateClosure(work);
        if (work.length < 6) {
            return null;
        }
        if (exactBounds != null) {
            for (int i = 0; i < work.length; i += 2) {
                exactBounds.add(work[i], work[i + 1]);
            }
        }
        double centerLat = averageLatitude(work);
        double[] simplified = simplifyClosed(work, tolerance, centerLat);
        if (simplified.length < 6 || Math.abs(signedArea(simplified)) < 1e-14) {
            simplified = work;
        }
        return new GeoRing(simplified);
    }

    private static double simplificationTolerance(double lonSpan,
                                                  double latSpan,
                                                  double centerLat,
                                                  boolean admin2) {
        double cos = Math.max(0.08, Math.abs(Math.cos(Math.toRadians(centerLat))));
        double unitsPerPixel = Math.max(lonSpan * cos / (VIEW_WIDTH - 2.0 * PADDING),
                latSpan / (VIEW_HEIGHT - 2.0 * PADDING));
        double pixelTolerance = admin2 ? 0.52 : 0.38;
        double tolerance = unitsPerPixel * pixelTolerance;
        return Math.max(1e-6, Math.min(0.12, tolerance));
    }

    private static List<GeoFeature> reduceToPointBudget(List<GeoFeature> features,
                                                         double baseTolerance,
                                                         double centerLat,
                                                         WarningCollector warnings) {
        int original = countPoints(features);
        List<GeoFeature> current = features;
        double tolerance = Math.max(baseTolerance, 1e-6);
        for (int iteration = 0; iteration < 5
                && countPoints(current) > MAX_OUTPUT_POINTS; iteration++) {
            double ratio = Math.sqrt((double) countPoints(current)
                    / (double) MAX_OUTPUT_POINTS);
            tolerance *= Math.max(1.35, ratio);
            List<GeoFeature> reduced = new ArrayList<GeoFeature>();
            for (GeoFeature feature : current) {
                List<GeoRing> rings = new ArrayList<GeoRing>();
                for (GeoRing ring : feature.rings) {
                    double[] simple = simplifyClosed(ring.xy, tolerance, centerLat);
                    if (simple.length >= 6) {
                        rings.add(new GeoRing(simple));
                    }
                }
                if (!rings.isEmpty()) {
                    reduced.add(new GeoFeature(rings));
                }
            }
            current = reduced;
        }
        warnings.add("Boundary geometry was simplified from " + original + " to "
                + countPoints(current) + " vertices for responsive browser display.");
        return current;
    }

    private static double[] simplifyClosed(double[] xy,
                                           double tolerance,
                                           double centerLat) {
        int n = xy.length / 2;
        if (n <= 5 || tolerance <= 0.0) {
            return xy;
        }
        double xScale = Math.max(0.08, Math.abs(Math.cos(Math.toRadians(centerLat))));
        int pivot = 1;
        double farthest = -1.0;
        double x0 = xy[0];
        double y0 = xy[1];
        for (int i = 1; i < n; i++) {
            double dx = (xy[2 * i] - x0) * xScale;
            double dy = xy[2 * i + 1] - y0;
            double distance = dx * dx + dy * dy;
            if (distance > farthest) {
                farthest = distance;
                pivot = i;
            }
        }
        if (pivot <= 0 || pivot >= n) {
            return xy;
        }
        double[] first = sliceArc(xy, 0, pivot, false);
        double[] second = sliceArc(xy, pivot, n, true);
        double[] a = simplifyOpen(first, tolerance, xScale);
        double[] b = simplifyOpen(second, tolerance, xScale);
        int aPoints = a.length / 2;
        int bPoints = b.length / 2;
        int total = aPoints + Math.max(0, bPoints - 2);
        if (total < 3) {
            return xy;
        }
        double[] result = new double[total * 2];
        System.arraycopy(a, 0, result, 0, a.length);
        int output = aPoints;
        for (int i = 1; i < bPoints - 1; i++) {
            result[2 * output] = b[2 * i];
            result[2 * output + 1] = b[2 * i + 1];
            output++;
        }
        if (output < 3) {
            return xy;
        }
        return trimCoordinates(result, output);
    }

    private static double[] sliceArc(double[] xy,
                                     int start,
                                     int end,
                                     boolean appendFirst) {
        int points = end - start + 1 + (appendFirst ? 1 : 0);
        double[] result = new double[points * 2];
        int output = 0;
        for (int i = start; i <= end && i < xy.length / 2; i++) {
            result[2 * output] = xy[2 * i];
            result[2 * output + 1] = xy[2 * i + 1];
            output++;
        }
        if (appendFirst) {
            result[2 * output] = xy[0];
            result[2 * output + 1] = xy[1];
            output++;
        }
        return trimCoordinates(result, output);
    }

    private static double[] simplifyOpen(double[] xy,
                                         double tolerance,
                                         double xScale) {
        int n = xy.length / 2;
        if (n <= 2) {
            return xy;
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        ArrayDeque<int[]> stack = new ArrayDeque<int[]>();
        stack.push(new int[]{0, n - 1});
        double toleranceSquared = tolerance * tolerance;
        while (!stack.isEmpty()) {
            int[] segment = stack.pop();
            int first = segment[0];
            int last = segment[1];
            double ax = xy[2 * first] * xScale;
            double ay = xy[2 * first + 1];
            double bx = xy[2 * last] * xScale;
            double by = xy[2 * last + 1];
            int best = -1;
            double bestDistance = -1.0;
            for (int i = first + 1; i < last; i++) {
                double px = xy[2 * i] * xScale;
                double py = xy[2 * i + 1];
                double distance = pointSegmentDistanceSquared(px, py, ax, ay, bx, by);
                if (distance > bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            if (best >= 0 && bestDistance > toleranceSquared) {
                keep[best] = true;
                stack.push(new int[]{first, best});
                stack.push(new int[]{best, last});
            }
        }
        int retained = 0;
        for (boolean value : keep) {
            if (value) {
                retained++;
            }
        }
        double[] result = new double[retained * 2];
        int output = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result[2 * output] = xy[2 * i];
                result[2 * output + 1] = xy[2 * i + 1];
                output++;
            }
        }
        return result;
    }

    private static String buildPath(List<GeoFeature> features,
                                    Projection projection) {
        StringBuilder path = new StringBuilder(Math.max(1024,
                countPoints(features) * 12));
        for (GeoFeature feature : features) {
            for (GeoRing ring : feature.rings) {
                if (ring.xy.length < 6) {
                    continue;
                }
                for (int i = 0; i < ring.xy.length; i += 2) {
                    double x = projection.x(ring.xy[i]);
                    double y = projection.y(ring.xy[i + 1]);
                    path.append(i == 0 ? 'M' : 'L');
                    appendSvgNumber(path, x);
                    path.append(' ');
                    appendSvgNumber(path, y);
                }
                path.append('Z');
            }
        }
        return path.toString();
    }

    /**
     * Keep geographic boundary payloads substantially smaller than coordinate
     * pair JSON while retaining every feature and ring.  The browser decodes
     * these values directly to Leaflet latitude/longitude polylines, so no SVG
     * projection or aspect-ratio transform is involved.
     */
    private static List<List<String>> encodeFeatures(List<GeoFeature> features) {
        List<List<String>> encoded = new ArrayList<List<String>>(features.size());
        for (GeoFeature feature : features) {
            List<String> rings = new ArrayList<String>(feature.rings.size());
            for (GeoRing ring : feature.rings) {
                if (ring.xy.length >= 6) {
                    rings.add(encodeRing(ring.xy));
                }
            }
            if (!rings.isEmpty()) {
                encoded.add(Collections.unmodifiableList(rings));
            }
        }
        return Collections.unmodifiableList(encoded);
    }

    private static String encodeRing(double[] xy) {
        StringBuilder output = new StringBuilder(Math.max(24, xy.length * 2));
        long previousLat = 0L;
        long previousLon = 0L;
        for (int i = 0; i < xy.length; i += 2) {
            long lon = Math.round(xy[i] * 100000.0);
            long lat = Math.round(xy[i + 1] * 100000.0);
            appendPolylineDelta(output, lat - previousLat);
            appendPolylineDelta(output, lon - previousLon);
            previousLat = lat;
            previousLon = lon;
        }
        return output.toString();
    }

    private static void appendPolylineDelta(StringBuilder output, long delta) {
        long value = delta < 0L ? ~(delta << 1) : delta << 1;
        while (value >= 0x20L) {
            output.append((char) ((0x20L | (value & 0x1fL)) + 63L));
            value >>= 5;
        }
        output.append((char) (value + 63L));
    }

    private static int pointInRing(double x,
                                   double y,
                                   double[] xy,
                                   double tolerance) {
        boolean inside = false;
        int n = xy.length / 2;
        if (n < 3) {
            return 0;
        }
        int j = n - 1;
        for (int i = 0; i < n; j = i++) {
            double xi = xy[2 * i];
            double yi = xy[2 * i + 1];
            double xj = xy[2 * j];
            double yj = xy[2 * j + 1];
            if (pointSegmentDistanceSquared(x, y, xi, yi, xj, yj)
                    <= tolerance * tolerance) {
                return 2;
            }
            boolean crosses = ((yi > y) != (yj > y));
            if (crosses) {
                double intersection = (xj - xi) * (y - yi) / (yj - yi) + xi;
                if (x < intersection) {
                    inside = !inside;
                }
            }
        }
        return inside ? 1 : 0;
    }

    private static double pointSegmentDistanceSquared(double px,
                                                      double py,
                                                      double ax,
                                                      double ay,
                                                      double bx,
                                                      double by) {
        double dx = bx - ax;
        double dy = by - ay;
        if (Math.abs(dx) < EPS && Math.abs(dy) < EPS) {
            dx = px - ax;
            dy = py - ay;
            return dx * dx + dy * dy;
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        double qx = ax + t * dx;
        double qy = ay + t * dy;
        dx = px - qx;
        dy = py - qy;
        return dx * dx + dy * dy;
    }

    private static Bounds boundsOf(List<GeoFeature> features) {
        Bounds bounds = new Bounds();
        for (GeoFeature feature : features) {
            for (GeoRing ring : feature.rings) {
                for (int i = 0; i < ring.xy.length; i += 2) {
                    bounds.add(ring.xy[i], ring.xy[i + 1]);
                }
            }
        }
        return bounds;
    }

    private static int countPoints(List<GeoFeature> features) {
        long count = 0;
        for (GeoFeature feature : features) {
            for (GeoRing ring : feature.rings) {
                count += ring.xy.length / 2;
            }
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private static int countPoints(GeoFeature feature) {
        long count = 0L;
        for (GeoRing ring : feature.rings) {
            count += ring.xy.length / 2;
        }
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private static String decodeField(byte[] record,
                                      DbfField field,
                                      Charset charset) {
        int start = field.offset;
        int end = Math.min(record.length, start + field.length);
        while (start < end && (record[start] == ' ' || record[start] == 0)) {
            start++;
        }
        while (end > start && (record[end - 1] == ' ' || record[end - 1] == 0)) {
            end--;
        }
        return new String(record, start, end - start, charset);
    }

    private static String asciiFieldName(byte[] bytes, int offset, int length) {
        int end = offset;
        int maximum = Math.min(bytes.length, offset + length);
        while (end < maximum && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII).trim();
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static double normalizeLongitude(double lon) {
        if (!isFinite(lon)) {
            return lon;
        }
        double normalized = lon % 360.0;
        if (normalized < -180.0) {
            normalized += 360.0;
        } else if (normalized >= 180.0) {
            normalized -= 360.0;
        }
        return normalized == -0.0 ? 0.0 : normalized;
    }

    private static double unwrapLongitude(double lon, double center) {
        double value = normalizeLongitude(lon);
        while (value - center > 180.0) {
            value -= 360.0;
        }
        while (value - center < -180.0) {
            value += 360.0;
        }
        return value;
    }

    private static boolean validCoordinate(double lon, double lat) {
        return isFinite(lon) && isFinite(lat)
                && lon >= -540.0 && lon <= 540.0
                && lat >= -90.000001 && lat <= 90.000001;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean almostEqual(double a, double b) {
        return Math.abs(a - b) <= EPS;
    }

    private static double[] trimCoordinates(double[] xy, int points) {
        if (points * 2 == xy.length) {
            return xy;
        }
        double[] result = new double[Math.max(0, points * 2)];
        System.arraycopy(xy, 0, result, 0, result.length);
        return result;
    }

    private static double[] removeDuplicateClosure(double[] xy) {
        int n = xy.length / 2;
        if (n > 3 && almostEqual(xy[0], xy[2 * (n - 1)])
                && almostEqual(xy[1], xy[2 * (n - 1) + 1])) {
            return trimCoordinates(xy, n - 1);
        }
        return xy;
    }

    private static double averageLatitude(double[] xy) {
        double sum = 0.0;
        int n = xy.length / 2;
        for (int i = 0; i < n; i++) {
            sum += xy[2 * i + 1];
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private static double signedArea(double[] xy) {
        int n = xy.length / 2;
        double sum = 0.0;
        int j = n - 1;
        for (int i = 0; i < n; j = i++) {
            sum += xy[2 * j] * xy[2 * i + 1]
                    - xy[2 * i] * xy[2 * j + 1];
        }
        return sum * 0.5;
    }

    private static void appendSvgNumber(StringBuilder out, double value) {
        long tenths = Math.round(value * 10.0);
        if (tenths % 10L == 0L) {
            out.append(tenths / 10L);
        } else {
            if (tenths < 0L && tenths > -10L) {
                out.append("-0");
            } else {
                out.append(tenths / 10L);
            }
            out.append('.').append(Math.abs(tenths % 10L));
        }
    }

    private static String formatDegrees(double value) {
        long tenths = Math.round(normalizeLongitude(value) * 10.0);
        if (tenths % 10L == 0L) {
            return (tenths / 10L) + "°";
        }
        String whole = tenths < 0L && tenths > -10L
                ? "-0" : Long.toString(tenths / 10L);
        return whole + "." + Math.abs(tenths % 10L) + "°";
    }

    private static String stripExtension(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static String readSmallText(ZipFile zip,
                                        ZipEntry entry,
                                        int maximum) throws IOException {
        InputStream in = zip.getInputStream(entry);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > maximum) {
                    throw new IllegalArgumentException("ZIP entry is unexpectedly large: "
                            + entry.getName());
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static int readAtMost(InputStream in,
                                  byte[] buffer,
                                  int offset,
                                  int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, offset + total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        byte[] fallback = null;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (fallback == null) {
                fallback = new byte[8192];
            }
            int read = in.read(fallback, 0, (int) Math.min(fallback.length, remaining));
            if (read < 0) {
                throw new EOFException("Unexpected end of shapefile.");
            }
            remaining -= read;
        }
    }

    private static int littleInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int littleUnsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int bigInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName() : message;
    }

    private static void closeQuietly(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Ignore close failures for immutable classpath resources.
            }
        }
    }

    /** Small command-line smoke test; not used by the Stata integration. */
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("usage: java BoundaryMap <country-or-ISO> [boundary.zip]");
            return;
        }
        List<String> warnings = new ArrayList<String>();
        MapGeometry geometry = load(args[0], args.length == 2 ? args[1] : null,
                warnings);
        System.out.println("name=" + geometry.displayName);
        System.out.println("source=" + geometry.sourceLabel);
        System.out.println("admin2=" + geometry.admin2);
        System.out.println("features=" + geometry.featureCount);
        System.out.println("bounds=" + geometry.minLon + "," + geometry.minLat
                + " to " + geometry.maxLon + "," + geometry.maxLat);
        System.out.println("center=" + geometry.lonCenter);
        System.out.println("pathChars=" + geometry.pathD.length());
        System.out.println("contains(center)=" + geometry.contains(
                (geometry.minLon + geometry.maxLon) * 0.5,
                (geometry.minLat + geometry.maxLat) * 0.5));
        for (String warning : geometry.warnings) {
            System.out.println("warning=" + warning);
        }
    }

    private interface ShapeConsumer {
        void accept(GeoFeature feature);
    }

    private static final class WarningCollector {
        final List<String> local = new ArrayList<String>();
        final List<String> external;

        WarningCollector(List<String> external) {
            this.external = external;
        }

        void add(String warning) {
            if (warning == null || warning.trim().isEmpty()) {
                return;
            }
            local.add(warning);
            if (external != null) {
                try {
                    external.add(warning);
                } catch (RuntimeException ignored) {
                    // The returned geometry still retains every warning.
                }
            }
        }
    }

    private static final class CountryRegistry {
        final Map<String, String> aliases;
        final Map<String, WorldCountry> countries;

        CountryRegistry(Map<String, String> aliases,
                        Map<String, WorldCountry> countries) {
            this.aliases = aliases;
            this.countries = countries;
        }

        ResolvedCountry resolve(String requested) {
            if (requested == null || requested.trim().isEmpty()) {
                throw new IllegalArgumentException("country() must contain a country name or ISO code.");
            }
            String normalized = normalizeKey(requested);
            String id = aliases.get(normalized);
            if (id == null && normalized.matches("\\d{1,3}")) {
                try {
                    id = String.format(Locale.ROOT, "%03d", Integer.parseInt(normalized));
                } catch (NumberFormatException ignored) {
                    id = null;
                }
            }
            WorldCountry world = id == null ? null : countries.get(id);
            if (world == null) {
                throw new IllegalArgumentException("Unknown country or ISO code: '"
                        + requested + "'.");
            }
            Set<String> keys = new HashSet<String>();
            keys.add(normalized);
            keys.add(normalizeKey(world.name));
            keys.add(normalizeKey(world.id));
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                if (world.id.equals(entry.getValue())) {
                    keys.add(entry.getKey());
                }
            }
            return new ResolvedCountry(requested.trim(), world.id, world.name,
                    world.rings, keys);
        }

        ResolvedCountry resolveForExternal(String requested) {
            if (requested == null || requested.trim().isEmpty()) {
                throw new IllegalArgumentException("country() must contain a country name or ISO code.");
            }
            try {
                return resolve(requested);
            } catch (IllegalArgumentException ignored) {
                String normalized = normalizeKey(requested);
                Set<String> keys = new HashSet<String>();
                keys.add(normalized);
                String id = aliases.get(normalized);
                if (id != null) {
                    keys.add(normalizeKey(id));
                    for (Map.Entry<String, String> entry : aliases.entrySet()) {
                        if (id.equals(entry.getValue())) keys.add(entry.getKey());
                    }
                }
                return new ResolvedCountry(requested.trim(), id == null ? normalized : id,
                        requested.trim(), "", keys);
            }
        }
    }

    private static final class WorldCountry {
        final String id;
        final String name;
        final String rings;

        WorldCountry(String id, String name, String rings) {
            this.id = id;
            this.name = name;
            this.rings = rings;
        }
    }

    private static final class ResolvedCountry {
        final String requested;
        final String id;
        final String displayName;
        final String worldRings;
        final Set<String> matchKeys;

        ResolvedCountry(String requested,
                        String id,
                        String displayName,
                        String worldRings,
                        Set<String> matchKeys) {
            this.requested = requested;
            this.id = id;
            this.displayName = displayName;
            this.worldRings = worldRings;
            this.matchKeys = matchKeys;
        }
    }

    private static final class GeoRing {
        final double[] xy;

        GeoRing(double[] xy) {
            this.xy = xy;
        }
    }

    private static final class GeoFeature {
        final List<GeoRing> rings;

        GeoFeature(List<GeoRing> rings) {
            this.rings = Collections.unmodifiableList(new ArrayList<GeoRing>(rings));
        }
    }

    private static final class Bounds {
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        void add(double lon, double lat) {
            if (!isFinite(lon) || !isFinite(lat)) {
                return;
            }
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
        }

        boolean isValid() {
            return isFinite(minLon) && isFinite(maxLon)
                    && isFinite(minLat) && isFinite(maxLat)
                    && minLon <= maxLon && minLat <= maxLat;
        }
    }

    private static final class Projection {
        final Bounds bounds;
        final double cosLat;
        final double scale;
        final double offsetX;
        final double offsetY;

        Projection(Bounds bounds) {
            if (bounds == null || !bounds.isValid()) {
                throw new IllegalArgumentException("Cannot project an empty boundary.");
            }
            this.bounds = bounds;
            double meanLat = (bounds.minLat + bounds.maxLat) * 0.5;
            cosLat = Math.max(0.08, Math.abs(Math.cos(Math.toRadians(meanLat))));
            double width = Math.max((bounds.maxLon - bounds.minLon) * cosLat, 1e-9);
            double height = Math.max(bounds.maxLat - bounds.minLat, 1e-9);
            double availableWidth = VIEW_WIDTH - 2.0 * PADDING;
            double availableHeight = VIEW_HEIGHT - 2.0 * PADDING;
            scale = Math.min(availableWidth / width, availableHeight / height);
            double renderedWidth = width * scale;
            double renderedHeight = height * scale;
            offsetX = (VIEW_WIDTH - renderedWidth) * 0.5;
            offsetY = (VIEW_HEIGHT - renderedHeight) * 0.5;
        }

        double x(double lon) {
            return offsetX + (lon - bounds.minLon) * cosLat * scale;
        }

        double y(double lat) {
            return offsetY + (bounds.maxLat - lat) * scale;
        }
    }

    private static final class LongitudeStats {
        final BitSet occupied = new BitSet(LON_BINS);
        long count;
        double rawMin = Double.POSITIVE_INFINITY;
        double rawMax = Double.NEGATIVE_INFINITY;

        void add(double longitude) {
            if (!isFinite(longitude)) {
                return;
            }
            double lon = normalizeLongitude(longitude);
            int bin = (int) Math.floor((lon + 180.0) / 360.0 * LON_BINS);
            if (bin < 0) {
                bin = 0;
            } else if (bin >= LON_BINS) {
                bin = LON_BINS - 1;
            }
            occupied.set(bin);
            rawMin = Math.min(rawMin, lon);
            rawMax = Math.max(rawMax, lon);
            count++;
        }

        double center() {
            Arc arc = arc();
            double degrees = -180.0 + (arc.start + arc.span * 0.5)
                    * 360.0 / LON_BINS;
            return normalizeLongitude(degrees);
        }

        double approximateSpan() {
            return arc().span * 360.0 / LON_BINS;
        }

        boolean crossesAntimeridian() {
            return count > 0 && rawMin < -150.0 && rawMax > 150.0
                    && approximateSpan() < rawMax - rawMin - 1.0;
        }

        private Arc arc() {
            if (occupied.isEmpty()) {
                throw new IllegalArgumentException("Boundary contains no valid longitudes.");
            }
            int first = occupied.nextSetBit(0);
            int previous = first;
            int largestGap = 0;
            int startAfterGap = first;
            for (int current = occupied.nextSetBit(first + 1);
                 current >= 0;
                 current = occupied.nextSetBit(current + 1)) {
                int gap = current - previous - 1;
                if (gap > largestGap) {
                    largestGap = gap;
                    startAfterGap = current;
                }
                previous = current;
            }
            int wrapGap = first + LON_BINS - previous - 1;
            if (wrapGap > largestGap) {
                largestGap = wrapGap;
                startAfterGap = first;
            }
            int span = Math.max(1, LON_BINS - largestGap);
            return new Arc(startAfterGap, span);
        }
    }

    private static final class Arc {
        final int start;
        final int span;

        Arc(int start, int span) {
            this.start = start;
            this.span = span;
        }
    }

    private static final class ZipLayers {
        final ZipEntry shp;
        final ZipEntry dbf;
        final ZipEntry prj;
        final ZipEntry cpg;

        ZipLayers(ZipEntry shp, ZipEntry dbf, ZipEntry prj, ZipEntry cpg) {
            this.shp = shp;
            this.dbf = dbf;
            this.prj = prj;
            this.cpg = cpg;
        }
    }

    private static final class DbfField {
        final String name;
        final int offset;
        final int length;

        DbfField(String name, int offset, int length) {
            this.name = name;
            this.offset = offset;
            this.length = length;
        }
    }

    private static final class DbfMatch {
        final BitSet matches;
        final String displayName;
        final boolean admin2;
        final int recordCount;

        DbfMatch(BitSet matches,
                 String displayName,
                 boolean admin2,
                 int recordCount) {
            this.matches = matches;
            this.displayName = displayName;
            this.admin2 = admin2;
            this.recordCount = recordCount;
        }
    }

    private static final class ParsedShape {
        final int type;
        final int[] parts;
        final double[] xy;

        ParsedShape(int type, int[] parts, double[] xy) {
            this.type = type;
            this.parts = parts;
            this.xy = xy;
        }
    }

    private static final class ShapePass {
        final LongitudeStats longitudeStats = new LongitudeStats();
        int polygonRecords;
        long invalidCoordinates;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
    }
}
