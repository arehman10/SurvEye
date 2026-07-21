package org.worldbank.surveye;

import java.io.BufferedWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stable, fully qualified entry point for Stata's {@code javacall} command.
 *
 * <p>The dashboard engine predates its Stata wrapper and remains in the default
 * package for command-line compatibility.  Java does not allow a named package
 * to import a default-package class, so this deliberately small adapter invokes
 * the engine by reflection.  Keeping the javacall-facing class in a named
 * package follows Stata's Java-plugin contract and avoids default-package class
 * lookup differences across Stata/JVM releases.</p>
 */
public final class StataPlugin {
    private static final String ENGINE_CLASS = "SurvEye";
    private static final String ENGINE_METHOD = "stata";

    private StataPlugin() {}

    /** Entry point required by javacall: public static int method(String[]). */
    public static int stata(String[] args) {
        try {
            ClassLoader loader = StataPlugin.class.getClassLoader();
            Class<?> engine = Class.forName(ENGINE_CLASS, true, loader);
            Method method = engine.getMethod(ENGINE_METHOD, String[].class);
            Object result = method.invoke(null, new Object[]{args});
            if (!(result instanceof Integer)) {
                throw new IllegalStateException("Dashboard engine returned a non-integer result.");
            }
            return ((Integer) result).intValue();
        } catch (InvocationTargetException error) {
            return bridgeFailure(args, error.getCause() == null ? error : error.getCause());
        } catch (Throwable error) {
            return bridgeFailure(args, error);
        }
    }

    private static int bridgeFailure(String[] args, Throwable error) {
        String detail = error.getMessage();
        String message = "Unable to start the dashboard engine (" + error.getClass().getSimpleName()
                + (detail == null || detail.trim().isEmpty() ? "" : ": " + detail.trim()) + ").";
        // The ado wrapper supplies a freshly allocated Stata tempfile as arg 2.
        // Writing a status record here keeps linkage failures user-friendly too.
        if (writeFallbackStatus(args, message)) return 0;
        System.err.println("surveye: " + message);
        return 499;
    }

    private static boolean writeFallbackStatus(String[] args, String message) {
        if (args == null || args.length < 2 || blank(args[1])) return false;
        try {
            Path status = Paths.get(args[1]).toAbsolutePath().normalize();
            if (args.length > 0 && !blank(args[0])) {
                Path config = Paths.get(args[0]).toAbsolutePath().normalize();
                if (samePath(status, config)) return false;
            }
            BufferedWriter writer = Files.newBufferedWriter(status, StandardCharsets.UTF_8);
            try {
                writer.write("success\t0");
                writer.newLine();
                writer.write("message\t" + singleLine(message));
                writer.newLine();
                writer.write("engine_version\tbridge");
                writer.newLine();
            } finally {
                writer.close();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean samePath(Path first, Path second) {
        if (first.equals(second)) return true;
        try {
            return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String singleLine(String value) {
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
