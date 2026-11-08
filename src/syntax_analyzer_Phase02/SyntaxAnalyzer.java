package syntax_analyzer_Phase02;

import token.Token;
import token.TokenType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class SyntaxAnalyzer {
    // Thrown when a syntax error is detected; carries the line to report.
    private static class ParseException extends RuntimeException {
        final int line;

        ParseException(String message, int line) {
            super(message);
            this.line = line;
        }
    }

    private static final String GLOBAL = "Global";

    private static final Set<String> GRAMMAR_WORDS =
            Set.of("void", "char", "int", "if", "else", "while", "for", "do");
    private static final Set<String> TYPES = Set.of("void", "char", "int");
    private static final Set<String> OPERATORS =
            Set.of("+", "-", "*", "/", "%", "<", ">", "=", "==", "<=", ">=", "!=");

    private static final String TYPE_EXPECTED = "Keyword expected: type specifier (void, char or int)";
    private static final String OPERAND_EXPECTED = "Identifier or integer constant expected";

    private final List<Token> tokens = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    private int pos = 0;
    private String currentScope = GLOBAL;
    private boolean inFunctionHeader = false;

    // ------------------------------------------------------------------ driver

    public void analyzeFile(String inputFilePath, String outputFilePath) {
        try {
            readTokens(inputFilePath);
        } catch (IOException e) {
            System.err.println("Error reading token file: " + e.getMessage());
            return;
        }

        parseProgram();

        if (errors.isEmpty()) {
            System.out.println("Syntax analysis completed successfully. No syntax errors found.");
        } else {
            System.out.println("Syntax errors found (" + errors.size() + "):");
            for (String err : errors) System.out.println("  " + err);
        }
        System.out.println();
        symbolTable.print(System.out);

        try {
            symbolTable.writeCsv(outputFilePath);
            System.out.println();
            System.out.println("Symbol table written to: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Error writing symbol table: " + e.getMessage());
        }
    }

    public List<String> getErrors() { return errors; }

    public SymbolTable getSymbolTable() { return symbolTable; }

    // ------------------------------------------------------- reading the tokens

    // Reads the Phase 1 token file (Token,Lexeme,Line No) into memory - the only file read.
    private void readTokens(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            int record = 0;
            while ((line = br.readLine()) != null) {
                record++;
                if (line.trim().isEmpty()) continue;
                List<String> f = splitCsv(line);
                if (record == 1 && f.get(0).trim().equalsIgnoreCase("Token")) continue; // header
                if (f.size() < 3) {
                    System.err.println("Skipping malformed token record at line " + record + " of token file");
                    continue;
                }
                try {
                    TokenType type = TokenType.valueOf(f.get(0).trim());
                    int lineNo = Integer.parseInt(f.get(2).trim());
                    tokens.add(new Token(type, f.get(1), lineNo));
                } catch (IllegalArgumentException ex) {
                    System.err.println("Skipping invalid token record at line " + record + " of token file: " + line);
                }
            }
        }
    }

    // Minimal CSV splitter that understands quoted fields (e.g. the "," separator token).
    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    sb.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    // ------------------------------------------------------------ token helpers

    private boolean atEnd()            { return pos >= tokens.size(); }
    private Token cur()                { return atEnd() ? null : tokens.get(pos); }
    private Token peek(int k)          { return pos + k < tokens.size() ? tokens.get(pos + k) : null; }
    private Token next()               { return tokens.get(pos++); }
    private int lastLine()             { return tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).getLineNumber(); }

    private boolean isSep(Token t, String s) {
        return t != null && t.getType() == TokenType.SEPARATOR && t.getLexeme().equals(s);
    }

    private boolean isOp(Token t, String s) {
        return t != null && t.getType() == TokenType.OPERATOR && t.getLexeme().equals(s);
    }

    private boolean isOperator(Token t) {
        return t != null && t.getType() == TokenType.OPERATOR && OPERATORS.contains(t.getLexeme());
    }

    private boolean isConstant(Token t) {
        return t != null && (t.getType() == TokenType.INTEGER_CONSTANT || t.getType() == TokenType.CHAR_CONSTANT);
    }

    // Lower-case keyword for a token, or null if it is not a keyword (case-insensitive).
    private String keyword(Token t) {
        if (t == null) return null;
        String l = t.getLexeme().toLowerCase(Locale.ROOT);
        if (t.getType() == TokenType.KEYWORD) return l;
        if (t.getType() == TokenType.IDENTIFIER && GRAMMAR_WORDS.contains(l)) return l;
        return null;
    }

    private boolean isIdentifier(Token t) {
        return t != null && t.getType() == TokenType.IDENTIFIER && keyword(t) == null;
    }

    private boolean isTypeKeyword(Token t) {
        String k = keyword(t);
        return k != null && TYPES.contains(k);
    }

    private static String typeName(Token t) {
        String l = t.getLexeme().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);   // Void / Char / Int
    }

    // ---------------------------------------------------------- error handling

    /**
     * A required token is absent. If the next token is already on a later line, the omission
     * happened at the end of the previous line, so that line is reported (e.g. a missing ';').
     */
    private ParseException missing(String message) {
        if (atEnd()) return new ParseException(message, lastLine());
        int line = cur().getLineNumber();
        if (pos > 0 && tokens.get(pos - 1).getLineNumber() < line) {
            line = tokens.get(pos - 1).getLineNumber();
        }
        return new ParseException(message, line);
    }

    // The current token itself is wrong - report the line it is on.
    private ParseException unexpected(String message) {
        return new ParseException(message, atEnd() ? lastLine() : cur().getLineNumber());
    }

    private void report(ParseException e) {
        errors.add("Syntax Error at line " + e.line + ": " + e.getMessage());
    }

    /**
     * Skips every remaining token on the given line. Returns the net brace balance of the
     * skipped tokens ('{' = +1, '}' = -1) so the caller can keep block nesting correct.
     */
    private int skipLine(int line) {
        int net = 0;
        while (!atEnd() && cur().getLineNumber() == line) {
            if (isSep(cur(), "{")) net++;
            else if (isSep(cur(), "}")) net--;
            pos++;
        }
        return net;
    }

    private void expectSep(String sep, String message) {
        if (!isSep(cur(), sep)) throw missing(message);
        next();
    }

    // ------------------------------------------------------------------ program

    private void parseProgram() {
        if (tokens.isEmpty()) {
            errors.add("Syntax Error at line 1: Empty program, type specifier expected");
            return;
        }
        while (!atEnd()) {
            int start = pos;
            currentScope = GLOBAL;
            inFunctionHeader = false;
            try {
                parseExternal();
            } catch (ParseException e) {
                report(e);
                int net = skipLine(e.line);
                if (inFunctionHeader) {
                    // error inside "type name ( params )" - still parse the body so its
                    // declarations reach the symbol table and its errors are reported
                    inFunctionHeader = false;
                    try {
                        if (net > 0) {
                            parseBlockBody(net - 1);
                        } else {
                            if (isSep(cur(), "{")) next();
                            parseBlockBody(0);
                        }
                    } catch (ParseException e2) {
                        report(e2);
                        skipLine(e2.line);
                    }
                }
                if (pos == start && !atEnd()) pos++;   // guarantee progress
            }
        }
        currentScope = GLOBAL;
    }

    // Top level: a global declaration or a function definition.
    private void parseExternal() {
        Token t = cur();
        if (isSep(t, "}")) throw unexpected("Unexpected '}' with no matching '{'");
        if (!isTypeKeyword(t)) throw unexpected(TYPE_EXPECTED);

        Token t1 = peek(1);
        Token t2 = peek(2);
        if (isIdentifier(t1) && (isSep(t2, "(") || isSep(t2, "{"))) {
            parseFunction();
        } else {
            parseDeclaration();
        }
    }

    private void parseFunction() {
        next();                                   // return type
        Token name = next();                      // function identifier
        symbolTable.add(name.getLexeme(), "Function", GLOBAL, "");
        currentScope = name.getLexeme();
        inFunctionHeader = true;

        expectSep("(", "Opening parenthesis '(' expected");
        parseParams();
        expectSep(")", "Closing parenthesis ')' expected");
        expectSep("{", "'{' expected");
        inFunctionHeader = false;

        parseBlockBody(0);
        currentScope = GLOBAL;
    }

    private void parseParams() {
        if (isSep(cur(), ")")) return;            // empty parameter list
        while (true) {
            if (!isTypeKeyword(cur())) throw missing(TYPE_EXPECTED);
            String type = typeName(next());
            if (isIdentifier(cur())) {            // "void" alone has no identifier
                Token id = next();
                SymbolTable.Entry e = symbolTable.add(id.getLexeme(), type, currentScope, "");
                parseArrayPart(e);
            }
            if (isSep(cur(), ",")) { next(); continue; }
            break;
        }
    }

    // ------------------------------------------------------------------- blocks

    /**
     * Parses declarations/statements up to and including the closing '}'.
     * The opening '{' has already been consumed. {@code pendingOpens} is used by error
     * recovery when '{' tokens were skipped: that many nested blocks are opened first.
     */
    private void parseBlockBody(int pendingOpens) {
        if (pendingOpens > 0) parseBlockBody(pendingOpens - 1);

        while (true) {
            if (atEnd()) throw missing("'}' expected");
            if (isSep(cur(), "}")) { next(); return; }

            int start = pos;
            try {
                parseStatement();
            } catch (ParseException e) {
                report(e);
                int net = skipLine(e.line);
                if (net < 0) return;                       // a skipped '}' closed this block
                if (net > 0) parseBlockBody(net - 1);      // skipped '{' opened block(s)
                if (pos == start && !atEnd()) pos++;       // guarantee progress
            }
        }
    }

    // '{' block_body '}' - used after if / else / while / do.
    private void parseBraceBlock() {
        if (!isSep(cur(), "{")) throw missing("'{' expected");
        next();
        parseBlockBody(0);
    }

    // --------------------------------------------------------------- statements

    private void parseStatement() {
        Token t = cur();
        if (isSep(t, ";")) { next(); return; }                         // empty statement
        if (isSep(t, "{")) { next(); parseBlockBody(0); return; }      // nested compound statement

        String k = keyword(t);
        if (k != null) {
            switch (k) {
                case "int": case "char": case "void": parseDeclaration(); return;
                case "if":    parseIf();    return;
                case "while": parseWhile(); return;
                case "for":   parseFor();   return;
                case "do":    parseDo();    return;
                case "else":  throw unexpected("'else' without matching 'if'");
                default:      throw unexpected("Unexpected keyword '" + t.getLexeme() + "'");
            }
        }
        if (isIdentifier(t)) {
            parseExpression();
            expectSep(";", "Semicolon expected");
            return;
        }
        if (t.getType() == TokenType.OPERATOR || isConstant(t) || t.getType() == TokenType.STRING_CONSTANT) {
            throw unexpected("Identifier expected");
        }
        throw unexpected("Unexpected '" + t.getLexeme() + "'");
    }

    private void parseIf() {
        next();                                                       // if
        expectSep("(", "Opening parenthesis '(' expected");
        parseExpression();
        expectSep(")", "Closing parenthesis ')' expected");
        parseBraceBlock();
        if ("else".equals(keyword(cur()))) {
            next();
            if ("if".equals(keyword(cur()))) parseIf();               // else if
            else parseBraceBlock();
        }
    }

    private void parseWhile() {
        next();                                                       // while
        expectSep("(", "Opening parenthesis '(' expected");
        parseExpression();
        expectSep(")", "Closing parenthesis ')' expected");
        parseBraceBlock();
    }

    private void parseFor() {
        next();                                                       // for
        expectSep("(", "Opening parenthesis '(' expected");
        parseExpression();
        expectSep(";", "Semicolon expected");
        parseExpression();
        expectSep(";", "Semicolon expected");
        parseExpression();
        expectSep(")", "Closing parenthesis ')' expected");
        if (isSep(cur(), "{")) {
            next();
            parseBlockBody(0);
        } else {
            if (atEnd()) throw missing("'{' expected");
            parseStatement();                                         // single-statement body
        }
    }

    private void parseDo() {
        next();                                                       // do
        parseBraceBlock();
        if (!"while".equals(keyword(cur()))) throw missing("'while' expected: 'do' without 'while'");
        next();
        expectSep("(", "Opening parenthesis '(' expected");
        parseExpression();
        expectSep(")", "Closing parenthesis ')' expected");
        expectSep(";", "Semicolon expected");
    }

    // -------------------------------------------------------------- expressions

    // expression -> ID op ID | ID op constant | ID op ID op constant | ID op ID op ID
    private void parseExpression() {
        if (!isIdentifier(cur())) throw missing("Identifier expected");
        next();

        if (!isOperator(cur())) throw missing("Operator expected");
        next();

        Token second = cur();
        if (isConstant(second)) {                                     // ID op constant
            next();
            rejectStrayOperand();
            return;
        }
        if (!isIdentifier(second)) throw missing(OPERAND_EXPECTED);
        next();

        if (isOperator(cur())) {                                      // ID op ID op (ID | constant)
            next();
            Token third = cur();
            if (!isIdentifier(third) && !isConstant(third)) throw missing(OPERAND_EXPECTED);
            next();
        }
        rejectStrayOperand();
    }

    // Two operands in a row ("a = b c") means an operator is missing between them.
    private void rejectStrayOperand() {
        Token t = cur();
        if (isIdentifier(t) || isConstant(t) || (t != null && t.getType() == TokenType.STRING_CONSTANT)) {
            throw missing("Operator expected");
        }
    }

    // ------------------------------------------------------------- declarations

    // declaration -> type_specifier init_declarator_list ;
    private void parseDeclaration() {
        String type = typeName(next());
        while (true) {
            Token id = cur();
            if (!isIdentifier(id)) throw missing("Identifier expected");
            next();
            // the identifier is recorded as soon as it is parsed, in the same pass
            SymbolTable.Entry entry = symbolTable.add(id.getLexeme(), type, currentScope, "");
            parseArrayPart(entry);

            if (isOp(cur(), "=")) {                                   // init_declarator: id = constant
                next();
                if (!isConstant(cur())) throw missing("Constant expected (integer or character constant)");
                next();
            }
            if (isSep(cur(), ",")) { next(); continue; }
            break;
        }
        expectSep(";", "Semicolon expected");
    }

    // Optional "[ size ]" after a declared name. "[]" gives size 0.
    private void parseArrayPart(SymbolTable.Entry entry) {
        Token t = cur();
        if (isSep(t, "[")) {
            next();
            if (cur() != null && cur().getType() == TokenType.INTEGER_CONSTANT) {
                entry.setArraySize(cur().getLexeme());
                next();
            } else if (isSep(cur(), "]")) {
                entry.setArraySize("0");
            } else {
                throw missing("Integer constant or right bracket ']' expected");
            }
            if (!isSep(cur(), "]")) throw missing("Right bracket ']' expected");
            next();
        } else if (t != null && (t.getType() == TokenType.INTEGER_CONSTANT || isSep(t, "]"))) {
            throw missing("Left bracket '[' expected");
        }
    }
}