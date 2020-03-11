package pl.edu.mimuw.mm408932;


import java.io.IOException;
import java.nio.file.Paths;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter;
import org.apache.lucene.search.uhighlight.PassageFormatter;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.FSDirectory;
import org.jline.builtins.Completers;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchFiles {

    private static Logger logger = LoggerFactory.getLogger(SearchFiles.class);

    private SearchFiles() {}

    public static void main(String[] args) {

        try (Terminal terminal = TerminalBuilder.builder()
                .jna(false)
                .jansi(true)
                .build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new Completers.FileNameCompleter())
                    .build();

            String index = System.getProperty("user.home") + "/.index";
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
            IndexSearcher searcher = new IndexSearcher(reader);
            Analyzer analyzer;

            boolean term = true;
            boolean phrase = false;
            boolean fuzzy = false;
            String lang = "en";
            boolean details = false;
            int limit = Integer.MAX_VALUE;
            boolean color = false;

            while (true) {
                String line = lineReader.readLine("> ");

                if (line == null) break;
                line = line.trim();
                if (line.length() == 0) break;

                if (line.charAt(0) == '%') {
                    String commandType = line.substring(1);

                    if (commandType.substring(0, 4).equals("lang")) {
                        if (commandType.equals("lang pl"))
                            lang = "pl";
                        else if (commandType.equals("lang en"))
                            lang = "en";
                    } else if (commandType.equals("term")) {
                        term = true;
                        phrase = false;
                        fuzzy = false;
                    } else if (commandType.equals("phrase")) {
                        term = false;
                        phrase = true;
                        fuzzy = false;
                    } else if (commandType.equals("fuzzy")) {
                        term = false;
                        phrase = false;
                        fuzzy = true;
                    } else if (commandType.substring(0, 5).equals("limit")) {
                        String number = commandType.substring(5);
                        limit = Integer.parseInt(number);
                        if (limit == 0)
                            limit = Integer.MAX_VALUE;
                    } else if (commandType.substring(0, 5).equals("color")) {
                        if (commandType.equals("color on"))
                            color = true;
                        else if (commandType.equals("color off"))
                            color = false;
                    } else if (commandType.substring(0, 7).equals("details")) {
                        if (commandType.equals("details on"))
                            details = true;
                        else if (commandType.equals("details off"))
                            details = false;
                    }
                    continue;
                }

                String field = null;
                if (lang.equals("pl")) {
                    analyzer = new PolishAnalyzer();
                    field = "PolishText";
                }
                else {
                    analyzer = new EnglishAnalyzer();
                    field = "EnglishText";
                }
                QueryParser parserContent = new QueryParser(field, analyzer);

                Query queryContent = null;
                if (term) {
                    queryContent = new TermQuery(new Term(field, line));
                } else if (phrase) {
                    String[] terms = line.split(" ");
                    queryContent = new PhraseQuery(field, terms);
                } else if (fuzzy) {
                    queryContent = new FuzzyQuery(new Term(field, line));
                }

                if (queryContent != null) {
                    Query parsedQuery = null;
                    try {
                        parsedQuery = parserContent.parse(queryContent.toString(field));
                    } catch (ParseException e) {
                        System.out.println("Cannot parse the query, so it won't be searched.");
                    }
                    searchQuery(searcher, parsedQuery, limit, analyzer, details, color, field);
                }
            }
            reader.close();
        } catch (IOException e) {
            logger.error("Cannot build the terminal and read queries.");
        }
    }

    private static void searchQuery(IndexSearcher searcher, Query query, int limit, Analyzer analyzer,
                                    boolean details, boolean color, String field) {

        try {
            TopDocs results = searcher.search(query, limit);
            ScoreDoc[] hits = results.scoreDocs;

            int numTotalHits = Math.toIntExact(results.totalHits.value);
            System.out.println("File count: " + numTotalHits);


            for (int i = 0; i < numTotalHits; i++) {

                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");

                if (path != null) {
                    System.out.println(new AttributedStringBuilder().append("")
                            .style(AttributedStyle.DEFAULT.bold())
                            .append(path)
                            .toAnsi());
                } else {
                    System.out.println("No path for this document");
                }

                if (details) {
                    UnifiedHighlighter highlighter = new UnifiedHighlighter(searcher, analyzer);
                    if (color) {
                        PassageFormatter formatter = new DefaultPassageFormatter("\033[31m",
                                "\u001b[0m", " ... ", false);
                        highlighter.setFormatter(formatter);
                    } else {
                        PassageFormatter formatter = new DefaultPassageFormatter("\u001b[1m",
                                "\u001b[0m", " ... ", false);
                        highlighter.setFormatter(formatter);
                    }
                    String[] fragments = highlighter.highlight(field, query, results, 5);

                    System.out.println(fragments[i]);
                }
            }
        } catch (IOException e) {
            System.out.println("Cannot search the query in the Index.");
        }
    }
}
