public class Main {
    public static void main(String[] args) {
        String inputFile = "input.c";
        String outputFile = "output.txt";

        if (args.length >= 1) inputFile = args[0];
        if (args.length >= 2) outputFile = args[1];

        LexicalAnalyzer analyzer = new LexicalAnalyzer();
        analyzer.analyzeFile(inputFile, outputFile);
    }
}