package token;

public class Token {
    private final TokenType tokenType;
    private final String lexeme;
    private final int lineNumber;

    public Token(TokenType tokenType, String lexeme, int lineNumber) {
        this.tokenType = tokenType;
        this.lexeme = lexeme;
        this.lineNumber = lineNumber;
    }
    public TokenType getType() { return tokenType; }
    public String getLexeme()   { return lexeme; }
    public int getLineNumber()  { return lineNumber; }

    @Override
    public String toString() {
        return String.format("%-20s %-25s %d", tokenType, lexeme, lineNumber);
    }
}