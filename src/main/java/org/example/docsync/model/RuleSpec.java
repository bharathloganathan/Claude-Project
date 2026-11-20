package org.example.docsync.model;

/** A backend validation rule of one form field: required, plus an optional CONTAINS or REGEX pattern (S4). */
public record RuleSpec(String apiField, boolean required, Kind kind, String text) {
    public enum Kind { NONE, CONTAINS, REGEX }
}
