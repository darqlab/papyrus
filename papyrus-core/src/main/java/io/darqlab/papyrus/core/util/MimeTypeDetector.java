package io.darqlab.papyrus.core.util;

import java.util.Map;

/**
 * Extension-based MIME type detection using standard Java only.
 * No external dependencies — magic-byte detection is handled in papyrus-extractor.
 */
public final class MimeTypeDetector {

    private static final Map<String, String> EXTENSION_MAP = Map.ofEntries(
            Map.entry("pdf",  "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("doc",  "application/msword"),
            Map.entry("xls",  "application/vnd.ms-excel"),
            Map.entry("ppt",  "application/vnd.ms-powerpoint"),
            Map.entry("html", "text/html"),
            Map.entry("htm",  "text/html"),
            Map.entry("txt",  "text/plain"),
            Map.entry("md",   "text/markdown"),
            Map.entry("csv",  "text/csv"),
            Map.entry("epub", "application/epub+zip"),
            Map.entry("jpg",  "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png",  "image/png"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("tif",  "image/tiff"),
            Map.entry("gif",  "image/gif"),
            Map.entry("bmp",  "image/bmp"),
            Map.entry("webp", "image/webp")
    );

    private MimeTypeDetector() {}

    /**
     * Detect MIME type from filename extension.
     *
     * @param filename the filename (with or without path)
     * @return MIME type string, or "application/octet-stream" if unknown
     */
    public static String detect(String filename) {
        if (filename == null || filename.isBlank()) {
            return "application/octet-stream";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "application/octet-stream";
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        return EXTENSION_MAP.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * Returns true if the MIME type represents a supported document format.
     */
    public static boolean isSupported(String mimeType) {
        return EXTENSION_MAP.containsValue(mimeType);
    }
}
