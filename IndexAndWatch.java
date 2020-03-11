package pl.edu.mimuw.mm408932;

import org.apache.lucene.index.IndexWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class IndexAndWatch {

    public static void main(String[] args) {

        try (IndexWriter writer = IndexFiles.createWriter()) {

            for (int i = 0; i < args.length; i++) {

                if ("--purge".equals(args[i])) {
                    IndexFiles.deleteIndex(writer);
                } else if ("--add".equals(args[i])) {
                    String docsPath = args[i + 1];
                    final Path docDir = Paths.get(docsPath);
                    System.out.println("I'm indexing the directory: " + docsPath);
                    IndexFiles.indexDirectory(writer, docDir, true);
                    i++;
                } else if ("--rm".equals(args[i])) {
                    String deleteDirectory = args[i + 1];
                    final Path deletePath = Paths.get(deleteDirectory);
                    IndexFiles.deleteDocs(writer, deletePath, true);
                    i++;
                } else if ("--reindex".equals(args[i])) {
                    IndexFiles.reindex(writer);
                } else if ("--list".equals(args[i])) {
                    IndexFiles.list();
                }
            }
        }
        catch (IOException e) {
            System.out.println("Cannot open the writer.");
        }

        if (args.length == 0) {
            System.out.println("\nI'll register all directories in Index to keep an eye on them.");

            ArrayList<String> indexedDirs = IndexFiles.findAllDirectories();
            WatchDir.watchIndex(indexedDirs);
        }
    }
}
