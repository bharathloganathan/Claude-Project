package org.example.api;

import org.example.model.Student;

import java.util.List;

public final class JsonWriter {
    private JsonWriter() {
    }

    public static String quote(String value) {
        String text = value == null ? "" : value;
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public static String student(Student student) {
        return "{\"id\":" + quote(student.id())
            + ",\"name\":" + quote(student.name())
            + ",\"email\":" + quote(student.email())
            + ",\"department\":" + quote(student.department())
            + ",\"phone\":" + quote(student.phone())
            + ",\"status\":" + quote(student.status())
            + "}";
    }

    public static String students(List<Student> students) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < students.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(student(students.get(i)));
        }
        return out.append(']').toString();
    }

    public static String error(ErrorCode code, String message) {
        return "{\"error\":" + quote(message) + ",\"code\":" + quote(code.name()) + "}";
    }

    public static String deleteResult(boolean deleted) {
        return "{\"deleted\":" + deleted + "}";
    }
}
