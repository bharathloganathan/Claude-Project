package org.example.docsync.docs;

import org.example.docsync.error.MarkerIntegrityException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkedDocument {
    public enum LineEnding { LF, CRLF }

    private static final Pattern MARKER = Pattern.compile("<!-- docsync:(begin|end) ([a-z0-9-]+) -->");
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9-]+");

    // bodyStart: offset after the begin line; endLineStart: offset of the end line
    private record Span(int bodyStart, int endLineStart) {
    }

    private final String raw;
    private final Map<String, Span> blocks;

    private MarkedDocument(String raw, Map<String, Span> blocks) {
        this.raw = raw;
        this.blocks = blocks;
    }

    public static MarkedDocument parse(String rawText, Set<String> knownIds) {
        Map<String, Span> blocks = new HashMap<>();
        Set<String> seen = new HashSet<>();
        String openId = null;
        int openLine = 0;
        int openBodyStart = 0;
        int pos = 0;
        int lineNo = 0;
        while (pos < rawText.length()) {
            lineNo++;
            int newline = rawText.indexOf('\n', pos);
            int next = newline < 0 ? rawText.length() : newline + 1;
            int contentEnd = newline < 0 ? rawText.length() : newline;
            if (newline > pos && rawText.charAt(newline - 1) == '\r') {
                contentEnd = newline - 1;
            }
            String content = rawText.substring(pos, contentEnd);
            Matcher marker = MARKER.matcher(content);
            if (marker.matches()) {
                boolean isBegin = marker.group(1).equals("begin");
                String id = marker.group(2);
                if (isBegin) {
                    if (!knownIds.contains(id)) {
                        throw fault("unknown block id", lineNo, id);
                    }
                    if (openId != null) {
                        throw fault("nested begin marker inside block '" + openId + "'", lineNo, id);
                    }
                    if (!seen.add(id)) {
                        throw fault("duplicate block id", lineNo, id);
                    }
                    openId = id;
                    openLine = lineNo;
                    openBodyStart = next;
                } else {
                    if (openId == null) {
                        throw fault("end marker without begin marker", lineNo, id);
                    }
                    if (!openId.equals(id)) {
                        throw fault("end marker does not match open block '" + openId + "'", lineNo, id);
                    }
                    blocks.put(id, new Span(openBodyStart, pos));
                    openId = null;
                }
            } else if (content.contains("docsync:begin") || content.contains("docsync:end")) {
                throw fault("malformed marker line", lineNo, null);
            }
            pos = next;
        }
        if (openId != null) {
            throw fault("begin marker without end marker", openLine, openId);
        }
        return new MarkedDocument(rawText, blocks);
    }

    public LineEnding lineEnding() {
        return raw.contains("\r\n") ? LineEnding.CRLF : LineEnding.LF;
    }

    public boolean hasBlock(String id) {
        return blocks.containsKey(id);
    }

    public Optional<String> body(String id) {
        Span span = blocks.get(id);
        if (span == null) {
            return Optional.empty();
        }
        String region = raw.substring(span.bodyStart(), span.endLineStart());
        if (region.isEmpty()) {
            return Optional.of("");
        }
        String withoutLast = region.substring(0, region.length() - (region.endsWith("\r\n") ? 2 : 1));
        return Optional.of(withoutLast.replace("\r\n", "\n"));
    }

    public String withBlock(String id, String body) {
        String eol = lineEnding() == LineEnding.CRLF ? "\r\n" : "\n";
        String normalized = checkedBody(id, body);
        Span span = blocks.get(id);
        StringBuilder out = new StringBuilder();
        if (span != null) {
            out.append(raw, 0, span.bodyStart());
            appendBodyLines(out, normalized, eol);
            out.append(raw, span.endLineStart(), raw.length());
            return out.toString();
        }
        out.append(raw);
        if (!raw.isEmpty()) {
            if (!raw.endsWith("\n")) {
                out.append(eol);
            }
            out.append(eol);
        }
        appendBlock(out, id, normalized, eol);
        return out.toString();
    }

    public static String create(String title, LinkedHashMap<String, String> blocksInOrder) {
        StringBuilder out = new StringBuilder("# ").append(title).append('\n');
        for (Map.Entry<String, String> block : blocksInOrder.entrySet()) {
            String normalized = checkedBody(block.getKey(), block.getValue());
            out.append('\n');
            appendBlock(out, block.getKey(), normalized, "\n");
        }
        return out.toString();
    }

    private static void appendBlock(StringBuilder out, String id, String normalizedBody, String eol) {
        out.append("<!-- docsync:begin ").append(id).append(" -->").append(eol);
        appendBodyLines(out, normalizedBody, eol);
        out.append("<!-- docsync:end ").append(id).append(" -->").append(eol);
    }

    private static void appendBodyLines(StringBuilder out, String normalizedBody, String eol) {
        if (normalizedBody.isEmpty()) {
            return;
        }
        for (String line : normalizedBody.split("\n", -1)) {
            out.append(line).append(eol);
        }
    }

    private static String checkedBody(String id, String body) {
        if (!VALID_ID.matcher(id).matches()) {
            throw new MarkerIntegrityException("Invalid block id '" + id
                    + "': ids must match [a-z0-9-]+. Manual text must not contain literal docsync markers.");
        }
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.contains("docsync:begin") || normalized.contains("docsync:end")) {
            throw new MarkerIntegrityException("Generated body for block '" + id
                    + "' contains marker syntax. Manual text must not contain literal docsync markers.");
        }
        return normalized;
    }

    private static MarkerIntegrityException fault(String problem, int line, String id) {
        String where = id == null ? "line " + line : "line " + line + ", id '" + id + "'";
        return new MarkerIntegrityException("Marker integrity fault: " + problem + " (" + where
                + "). Manual text must not contain literal docsync:begin or docsync:end markers; fix the file by hand.");
    }
}
