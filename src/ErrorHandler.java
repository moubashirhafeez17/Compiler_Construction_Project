public class ErrorHandler {

    public static void reportError(String errorType, String detail, int lineNumber) {
        System.err.println("Error at line " + lineNumber + ": " + errorType + " -> " + detail);
    }

    public static void unterminatedComment(int lineNumber) {
        reportError("Un-terminated comment",
                "Comment started but never closed", lineNumber);
    }

    public static void stringExceedsLine(int lineNumber) {
        reportError("String constant exceeds line",
                "String literal not closed before end of line", lineNumber);
    }

    public static void charConstantTooLong(int lineNumber, String lexeme) {
        reportError("Char constant too long",
                "Invalid char constant: " + lexeme, lineNumber);
    }

    public static void undefinedSymbol(int lineNumber, String symbol) {
        reportError("Undefined symbol",
                "Invalid character/symbol: " + symbol, lineNumber);
    }
}
