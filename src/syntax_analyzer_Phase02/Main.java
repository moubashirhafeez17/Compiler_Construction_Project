package syntax_analyzer_Phase02;

/**
 * Phase 2 entry point.
 * Input  : token file produced by Phase 1 (default: output.csv)
 * Output : symbol table (printed on screen and written to symbol_table.csv)
 */

public class Main {
    public static void main(String[] args) {
        String inputFile = "output.csv";
        String outputFile = "symbol_table.csv";

        if (args.length >= 1) inputFile = args[0];
        if (args.length >= 2) outputFile = args[1];

        SyntaxAnalyzer analyzer = new SyntaxAnalyzer();
        analyzer.analyzeFile(inputFile, outputFile);
    }
}