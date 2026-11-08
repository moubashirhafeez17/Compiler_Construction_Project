package syntax_analyzer_Phase02;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Symbol table produced by the syntax analyzer.
 * Columns: Lexeme | Type | Scope | Array size
 * An identifier declared more than once simply gets more than one entry
 * (duplicate-declaration checking belongs to the next phase).
 */

public class SymbolTable {

    public static class Entry {
        private final String lexeme;
        private final String type;
        private final String scope;
        private String arraySize;   // "" = not an array, "0" = int a[], "10" = int a[10]

        Entry(String lexeme, String type, String scope, String arraySize) {
            this.lexeme = lexeme;
            this.type = type;
            this.scope = scope;
            this.arraySize = arraySize;
        }

        public String getLexeme()    { return lexeme; }
        public String getType()      { return type; }
        public String getScope()     { return scope; }
        public String getArraySize() { return arraySize; }
        void setArraySize(String arraySize) { this.arraySize = arraySize; }
    }

    private static final String[] HEADERS = {"Lexeme", "Type", "Scope", "Array size"};

    private final List<Entry> entries = new ArrayList<>();

    public Entry add(String lexeme, String type, String scope, String arraySize) {
        Entry e = new Entry(lexeme, type, scope, arraySize);
        entries.add(e);
        return e;
    }

    public List<Entry> getEntries() { return entries; }

    /** Pretty-prints the table to the given stream. */
    public void print(PrintStream out) {
        int[] w = {HEADERS[0].length(), HEADERS[1].length(), HEADERS[2].length(), HEADERS[3].length()};
        for (Entry e : entries) {
            w[0] = Math.max(w[0], e.lexeme.length());
            w[1] = Math.max(w[1], e.type.length());
            w[2] = Math.max(w[2], e.scope.length());
            w[3] = Math.max(w[3], e.arraySize.length());
        }
        String fmt = "%-" + (w[0] + 3) + "s%-" + (w[1] + 3) + "s%-" + (w[2] + 3) + "s%s%n";
        out.println("SYMBOL TABLE");
        out.printf(fmt, HEADERS[0], HEADERS[1], HEADERS[2], HEADERS[3]);
        int total = w[0] + w[1] + w[2] + w[3] + 9;
        out.println("-".repeat(total));
        for (Entry e : entries) {
            out.printf(fmt, e.lexeme, e.type, e.scope, e.arraySize);
        }
    }

    /** Writes the table as CSV (same style as the Phase 1 output). */
    public void writeCsv(String path) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write("Lexeme,Type,Scope,Array size\n");
            for (Entry e : entries) {
                bw.write(csv(e.lexeme) + "," + csv(e.type) + "," + csv(e.scope) + "," + csv(e.arraySize) + "\n");
            }
        }
    }

    private static String csv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}