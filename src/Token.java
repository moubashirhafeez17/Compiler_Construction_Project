public class Token {
    private final String tokenType;
    private final String lexeme;
    private final int lineNumber;

    public Token(String tokenType, String lexeme, int lineNumber) {
        this.tokenType = tokenType;
        this.lexeme = lexeme;
        this.lineNumber = lineNumber;
    }

    @Override
    public String toString() {
        return String.format("%-20s %-25s %d", tokenType, lexeme, lineNumber);
    }
}