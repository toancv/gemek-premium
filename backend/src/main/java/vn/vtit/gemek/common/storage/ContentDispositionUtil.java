/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.storage;

import java.nio.charset.StandardCharsets;

/**
 * Builds a safe {@code Content-Disposition: attachment} header value per RFC 6266 / RFC 5987.
 *
 * <p>Used to FORCE a download (never inline render) for document attachments, with a filename that
 * is safe against header/query-param injection. A filename can contain quotes, CR/LF, control chars,
 * or non-ASCII (Vietnamese) text — any of which could break the signed presigned-URL query param or
 * inject extra header content if passed through raw. This builder emits:
 *
 * <pre>attachment; filename="&lt;ascii-fallback&gt;"; filename*=UTF-8''&lt;percent-encoded-utf8&gt;</pre>
 *
 * where the quoted {@code filename} is a stripped ASCII fallback for legacy agents and
 * {@code filename*} carries the full UTF-8 name percent-encoded per RFC 5987 for modern agents.
 */
public final class ContentDispositionUtil {

    /** RFC 5987 {@code attr-char} set — characters allowed UNENCODED in an ext-value (besides ALPHA/DIGIT). */
    private static final String ATTR_CHAR_SPECIALS = "!#$&+-.^_`|~";

    /** Fallback name used when sanitization leaves nothing usable. */
    private static final String DEFAULT_NAME = "download";

    /**
     * Private constructor — utility class, never instantiated.
     */
    private ContentDispositionUtil() {
    }

    /**
     * Builds a safe {@code attachment} Content-Disposition value for the given display filename.
     *
     * @param filename the original (possibly unsafe / non-ASCII) display filename; null/blank → "download".
     * @return the sanitized {@code Content-Disposition} header value (never null, never contains CR/LF).
     */
    public static String attachment(String filename) {
        String asciiFallback = toAsciiFallback(filename);
        String encoded = toRfc5987(filename);
        // filename* carries the full UTF-8 name; the quoted filename is the legacy ASCII fallback.
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * Produces a quoted-string-safe ASCII fallback: drops control chars, quotes, and backslashes, and
     * replaces any non-ASCII char with {@code '_'} so the quoted {@code filename} can never break the
     * header or inject content.
     *
     * @param filename the raw filename, may be null.
     * @return a non-empty ASCII fallback safe inside a quoted-string.
     */
    private static String toAsciiFallback(String filename) {
        if (filename == null) {
            return DEFAULT_NAME;
        }
        StringBuilder sb = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            // Drop quote, backslash, and any control char (incl. CR/LF) — these break quoting/headers.
            if (c == '"' || c == '\\' || c < 0x20 || c == 0x7F) {
                continue;
            }
            // Replace any non-ASCII char with '_' (the UTF-8 name rides in filename*).
            sb.append(c > 0x7F ? '_' : c);
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? DEFAULT_NAME : result;
    }

    /**
     * Percent-encodes the filename as an RFC 5987 ext-value (UTF-8 octets, {@code attr-char} unencoded).
     *
     * @param filename the raw filename, may be null.
     * @return the percent-encoded value (never null, never empty).
     */
    private static String toRfc5987(String filename) {
        String name = (filename == null || filename.isBlank()) ? DEFAULT_NAME : filename;
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int ch = b & 0xFF;
            boolean isAlphaNum = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
            if (isAlphaNum || ATTR_CHAR_SPECIALS.indexOf(ch) >= 0) {
                sb.append((char) ch);
            } else {
                // Percent-encode every other octet (incl. CR/LF/quote/space and all UTF-8 multibyte octets).
                sb.append('%')
                        .append(Character.toUpperCase(Character.forDigit((ch >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(ch & 0xF, 16)));
            }
        }
        return sb.toString();
    }
}
