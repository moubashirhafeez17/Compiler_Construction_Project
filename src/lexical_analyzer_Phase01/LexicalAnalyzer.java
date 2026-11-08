package lexical_analyzer_Phase01;

import token.*;

import java.io.*;
import java.util.*;

public class LexicalAnalyzer {

    private final List<Token> tokens = new ArrayList<>();
    private int lineNumber = 1;
    private int index = 0;
    private String source = "";
    private boolean errorOnCurrentLine = false;

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "inline", "int", "long", "register", "restrict", "return", "short",
            "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
            "unsigned", "void", "volatile", "while", "_Bool", "_Complex", "_Imaginary"
    ));

    private static final String[] MULTI_OPERATORS = {
            "<<=", ">>=", "...",
            "++", "--", "->", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^="
    };

    private static final String SINGLE_OPERATORS = "+-*/%=<>!&|^~.";
    private static final String SEPARATORS       = "(){}[];,?:#";

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
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private void writeTokens(String path) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write("Token,Lexeme,Line No\n");
            for (Token t : tokens) {
                bw.write(escapeCsv(t.getType().name()) + ","
                       + escapeCsv(t.getLexeme()) + ","
                       + t.getLineNumber() + "\n");
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void tokenize() {
        index = 0;
        lineNumber = 1;
        errorOnCurrentLine = false;
        int len = source.length();

        while (index < len) {
            char c = source.charAt(index);

            if (c == '\n') { lineNumber++; index++; errorOnCurrentLine = false; continue; }
            if (Character.isWhitespace(c)) { index++; continue; }
            if (errorOnCurrentLine) { index++; continue; }

            if (c == '/' && index + 1 < len && source.charAt(index + 1) == '*') {
                handleComment(); continue;
            }
            if (c == '"')  { handleStringConstant(); continue; }
            if (c == '\'') { handleCharConstant();   continue; }

            if (Character.isLetter(c) || c == '_') { handleIdentifierOrKeyword(); continue; }
            if (Character.isDigit(c))              { handleIntegerConstant();     continue; }

            // Try multi-char operators FIRST so "..." works
            if (handleMultiOperator()) continue;

            if (isOperatorStart(c)) { handleSingleOperator(); continue; }

            if (SEPARATORS.indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.SEPARATOR, String.valueOf(c), lineNumber));
                index++; continue;
            }

            ErrorHandler.undefinedSymbol(lineNumber, String.valueOf(c));
            errorOnCurrentLine = true;
            index++;
        }
    }

    private void handleComment() {
        int startLine = lineNumber;
        index += 2;
        boolean closed = false;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                index += 2; closed = true; break;
            }
            if (c == '\n') lineNumber++;
            index++;
        }
        if (!closed) {
            ErrorHandler.unterminatedComment(startLine);
            errorOnCurrentLine = true;
        }
    }

    private void handleStringConstant() {
        int startLine = lineNumber;
        index++;
        StringBuilder sb = new StringBuilder();
        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '\n') {
                ErrorHandler.stringExceedsLine(startLine);
                errorOnCurrentLine = true;
                return; // main loop consumes the newline
            }
            if (c == '"') {
                index++;
                tokens.add(new Token(TokenType.STRING_CONSTANT, sb.toString(), startLine));
                return;
            }
            if (c == '\\' && index + 1 < source.length()) {
                sb.append(c).append(source.charAt(index + 1));
                index += 2;
                continue;
            }
            sb.append(c);
            index++;
        }
        ErrorHandler.stringExceedsLine(startLine);
        errorOnCurrentLine = true;
    }

    private void handleCharConstant() {
        int startLine = lineNumber;
        int start = index;
        index++;

        List<Character> chars = new ArrayList<>();
        boolean closed = false;

        while (index < source.length()) {
            char c = source.charAt(index);
            if (c == '\n') break;
            if (c == '\\' && index + 1 < source.length()) {
                chars.add(source.charAt(index + 1));
                index += 2;
                continue;
            }
            if (c == '\'') { closed = true; index++; break; }
            chars.add(c);
            index++;
        }

        if (!closed) {
            ErrorHandler.unterminatedChar(startLine);
            errorOnCurrentLine = true;
            return;
        }
        if (chars.isEmpty()) {
            ErrorHandler.emptyCharConstant(startLine);
            errorOnCurrentLine = true;
            return;
        }
        if (chars.size() > 1) {
            ErrorHandler.charConstantTooLong(startLine,
                    source.substring(start, Math.min(index, source.length())));
            errorOnCurrentLine = true;
            return;
        }
        tokens.add(new Token(TokenType.CHAR_CONSTANT, String.valueOf(chars.get(0)), startLine));
    }

    private void handleIdentifierOrKeyword() {
        int start = index;
        while (index < source.length()) {
            char c = source.charAt(index);
            if (Character.isLetterOrDigit(c) || c == '_') index++;
            else break;
        }
        String lexeme = source.substring(start, index);
        TokenType type = KEYWORDS.contains(lexeme) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
        tokens.add(new Token(type, lexeme, lineNumber));
    }

    private void handleIntegerConstant() {
        int start = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;

        // 123abc → invalid identifier
        if (index < source.length() &&
                (Character.isLetter(source.charAt(index)) || source.charAt(index) == '_')) {
            while (index < source.length() &&
                    (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_')) {
                index++;
            }
            ErrorHandler.undefinedSymbol(lineNumber, source.substring(start, index));
            errorOnCurrentLine = true;
            return;
        }
        tokens.add(new Token(TokenType.INTEGER_CONSTANT,
                source.substring(start, index), lineNumber));
    }

    /** Try to match a multi-char operator at current index. */
    private boolean handleMultiOperator() {
        for (String op : MULTI_OPERATORS) {
            if (source.startsWith(op, index)) {
                tokens.add(new Token(TokenType.OPERATOR, op, lineNumber));
                index += op.length();
                return true;
            }
        }
        return false;
    }

    private boolean isOperatorStart(char c) {
        return SINGLE_OPERATORS.indexOf(c) >= 0;
    }

    private void handleSingleOperator() {
        char c = source.charAt(index);
        tokens.add(new Token(TokenType.OPERATOR, String.valueOf(c), lineNumber));
        index++;
    }
}