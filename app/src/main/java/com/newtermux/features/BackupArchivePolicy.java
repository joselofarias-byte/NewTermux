package com.newtermux.features;

import java.util.HashSet;
import java.util.Set;

/** Validation shared by native archive preparation and deterministic JVM tests. */
public final class BackupArchivePolicy {
    private final Set<String> entries = new HashSet<>();
    private final Set<String> destinations = new HashSet<>();
    private final long payloadBudget;
    private long payloadBytes;

    public BackupArchivePolicy(long payloadBudget) {
        if (payloadBudget < 0) throw new IllegalArgumentException("Espacio de staging inválido");
        this.payloadBudget = payloadBudget;
    }

    public void entry(String name) {
        relativePath(name, false);
        if (!entries.add(name)) throw new IllegalArgumentException("Entrada ZIP duplicada: " + name);
    }

    public void payload(int bytes) {
        if (bytes < 0 || bytes > payloadBudget - payloadBytes)
            throw new IllegalArgumentException("El respaldo supera el espacio seguro de staging");
        payloadBytes += bytes;
    }

    public void destination(String component, int root, String path, String type, String zipEntry) {
        relativePath(path, true);
        if (path.endsWith("/")) throw new IllegalArgumentException("Ruta de metadata ambigua");
        if (root < 0) throw new IllegalArgumentException("Raíz inválida");
        if (!"file".equals(type) && !"dir".equals(type) && !"symlink".equals(type)
                && !"skipped-special".equals(type))
            throw new IllegalArgumentException("Tipo de metadata inválido: " + type);
        if (path.isEmpty() && !"dir".equals(type))
            throw new IllegalArgumentException("La raíz debe ser un directorio");
        String expected = dataEntryName(component, root, path);
        if (!destinations.add(expected))
            throw new IllegalArgumentException("Destino de metadata duplicado: " + expected);
        if ("file".equals(type) && !expected.equals(zipEntry))
            throw new IllegalArgumentException("El archivo ZIP no corresponde al destino de metadata");
    }

    public static String componentToken(String component) {
        if (component == null || component.isEmpty()) throw new IllegalArgumentException("Componente vacío");
        return component.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static String dataEntryName(String component, int root, String path) {
        relativePath(path, true);
        return "data/" + componentToken(component) + "/" + root + "/" + path;
    }

    public static void relativePath(String path, boolean allowEmpty) {
        if (path == null || (path.isEmpty() && !allowEmpty) || path.startsWith("/")
                || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Ruta de respaldo inválida");
        if (path.isEmpty()) return;
        // A directory ZIP entry may end in '/', but aliases within the path are rejected.
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        for (String part : normalized.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part))
                throw new IllegalArgumentException("Ruta de respaldo ambigua");
        }
    }
}
