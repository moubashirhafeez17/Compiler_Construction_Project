import java.io.*;
import java.util.*;

public class LexicalAnalyzer {

    private final List<Token> tokens = new ArrayList<>();
    private int lineNumber = 1;
    private int index = 0;
    private String source = "";
    private boolean errorOnCurrentLine = false;

    // C Keywords
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "inline", "int", "long", "register", "restrict", "return", "short",
            "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
            "unsigned", "void", "volatile", "while", "_Bool", "_Complex", "_Imaginary"
    ));

    // Multi-character operators (longest match first)
    private static final String[] MULTI_OPERATORS = {
            "<<=", ">>=", "...",
            "++", "--", "->", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^="
    };

    // Single character operators
    private static final String SINGLE_OPERATORS = "+-*/%=<>!&|^~";

    // Separators / Punctuation
    private static final String SEPARATORS = "(){}[];,.:?";

    public void analyzeFile(String inputFilePath, String outputFilePath) {
        try {
            source = readFile(inputFilePath);
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
            return;
        }

        tokenize();

        try {
            writeTokens(outputFilePath);
            System.out.println("Lexical analysis completed. Output written to: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error writing output file: " + e.getMessage());
        }
    }

    private String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void writeTokens(String path) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write(String.format("%-20s %-25s %s%n", "Token", "Lexeme", "Line No"));
            bw.write("------------------------------------------------------------\n");
            for (Token t : tokens) {
                bw.write(t.toString() + "\n");
            }
        }
    }

    // TOKENIZER

    private void tokenize() {
        index = 0;
        lineNumber = 1;
        int len = source.length();

        while (index < len) {
            char c = source.charAt(index);

            // Handle newline
            if (c == '\n') {
                lineNumber++;
                index++;
                errorOnCurrentLine = false;
                continue;
            }

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }

            // If error already on this line, skip until newline
            if (errorOnCurrentLine) {
                index++;
                continue;
            }

            // Comments
            if (c == '/' && index + 1 < len && source.charAt(index + 1) == '*') {
                handleComment();
                continue;
            }

            // String constants
            if (c == '"') {
                handleStringConstant();
                continue;
            }

            // Char constants
            if (c == '\'') {
                handleCharConstant();
                continue;
            }

            // Identifiers / Keywords
            if (Character.isLetter(c) || c == '_') {
                handleIdentifierOrKeyword();
                continue;
            }

            // Integer constants
            if (Character.isDigit(c)) {
                handleIntegerConstant();
                continue;
            }

            // Operators
            if (isOperatorStart(c)) {
                handleOperator();
                continue;
            }

            // Separators
            if (SEPARATORS.indexOf(c) >= 0) {
                tokens.add(new Token("Separator", String.valueOf(c), lineNumber));
                index++;
                continue;
            }

            // Undefined symbol
            ErrorHandler.undefinedSymbol(lineNumber, String.valueOf(c));
            errorOnCurrentLine = true;
            index++;
        }
    }

    // HANDLERS

    private void handleComment() {
        int startLine = lineNumber;
        index += 2; // skip /*
        boolean closed = false;

        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                index += 2;
                closed = true;
                break;
            }
            if (c == '\n') {
                lineNumber++;
            }
            index++;
        }

        if (!closed) {
            ErrorHandler.unterminatedComment(startLine);
        }
        // No token generated for comments
    }

    private void handleStringConstant() {
        int startLine = lineNumber;
        int start = index;
        index++; // skip opening "
        StringBuilder sb = new StringBuilder();

        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '\n') {
                // String exceeded line without closing
                ErrorHandler.stringExceedsLine(startLine);
                errorOnCurrentLine = true;
                // Do not increment lineNumber here; main loop will handle newline
                return;
            }
            if (c == '"') {
                index++; // skip closing "
                tokens.add(new Token("String Constant", sb.toString(), startLine));
                return;
            }
            if (c == '\\' && index + 1 < source.length()) {
                sb.append(c);
                sb.append(source.charAt(index + 1));
                index += 2;
                continue;
            }
            sb.append(c);
            index++;
        }

        // End of file reached without closing quote
        ErrorHandler.stringExceedsLine(startLine);
        errorOnCurrentLine = true;
    }

    private void handleCharConstant() {
        int startLine = lineNumber;
        int start = index;
        index++; // skip opening '

        List<Character> chars = new ArrayList<>();
        boolean closed = false;

        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '\n') {
                break;
            }
            if (c == '\\' && index + 1 < source.length()) {
                chars.add(source.charAt(index + 1));
                index += 2;
                continue;
            }
            if (c == '\'') {
                closed = true;
                index++;
                break;
            }
            chars.add(c);
            index++;
        }

        if (!closed) {
            // Treat as undefined/incomplete - skip line
            ErrorHandler.undefinedSymbol(startLine, source.substring(start, Math.min(index, source.length())));
            errorOnCurrentLine = true;
            return;
        }

        if (chars.size() != 1) {
            ErrorHandler.charConstantTooLong(startLine, source.substring(start, index));
            errorOnCurrentLine = true;
            return;
        }

        tokens.add(new Token("Char Constant", String.valueOf(chars.getFirst()), startLine));
    }

    private void handleIdentifierOrKeyword() {
        int start = index;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (Character.isLetterOrDigit(c) || c == '_') {
                index++;
            } else {
                break;
            }
        }
        String lexeme = source.substring(start, index);
        if (KEYWORDS.contains(lexeme)) {
            tokens.add(new Token("Keyword", lexeme, lineNumber));
        } else {
            tokens.add(new Token("Identifier", lexeme, lineNumber));
        }
    }

    private void handleIntegerConstant() {
        int start = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            index++;
        }
        // Only unsigned integers allowed; if letter follows immediately, treat as error
        if (index < source.length() && (Character.isLetter(source.charAt(index)) || source.charAt(index) == '_')) {
            // This is actually an invalid identifier like 123abc
            while (index < source.length() &&
                    (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_')) {
                index++;
            }
            ErrorHandler.undefinedSymbol(lineNumber, source.substring(start, index));
            errorOnCurrentLine = true;
            return;
        }
        tokens.add(new Token("Integer Constant", source.substring(start, index), lineNumber));
    }

    private boolean isOperatorStart(char c) {
        return SINGLE_OPERATORS.indexOf(c) >= 0;
    }

    private void handleOperator() {
        // Try multi-character operators first (longest match)
        for (String op : MULTI_OPERATORS) {
            if (source.startsWith(op, index)) {
                tokens.add(new Token("Operator", op, lineNumber));
                index += op.length();
                return;
            }
        }
        // Single character operator
        char c = source.charAt(index);
        tokens.add(new Token("Operator", String.valueOf(c), lineNumber));
        index++;
    }
}