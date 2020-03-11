package pl.edu.mimuw.mm408932;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class IndexFiles {

    private IndexFiles() {}

    public static void indexDirectory (IndexWriter writer, Path dirPath, boolean newDir) {

        if (!Files.isReadable(dirPath)) {
            System.out.println("Document directory '" +dirPath.toAbsolutePath()+
                    "' does not exist or is not readable, please check the path");
            return;
        }

        try {
            if (isThisDirInIndex(dirPath.toString())) {
                System.out.println("This directory has been already indexed.");
                return;
            }
        }
        catch (IOException e) {
            System.out.println("Cannot check is the directory already in index." +
                    "This directory won't be added to the Index.");
            return;
        }

        if (Files.isDirectory(dirPath) && newDir)
            addInfoAboutDir(writer, dirPath, true);

        indexDocs(writer, dirPath, true);
    }

    private static boolean isThisDirInIndex (String dirPath) throws IOException {

        String index = System.getProperty("user.home") + "/.index";
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));

        for (int i = 0; i < reader.maxDoc(); i++) {

            Document doc = reader.document(i);
            String dirName = doc.get("dirName");

            if (dirName != null) {
                if (dirPath.equals(dirName))
                    return true;
            }
        }
        reader.close();

        return false;
    }


    private static void indexDocs(IndexWriter writer, Path path, boolean newDir) {

        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        indexDoc(writer, file, newDir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                indexDoc(writer, path, newDir);
            }
        }
        catch (IOException e) {
            System.out.println("The problem with looking for files in directory or subdirectories has occured." +
                    " It's possible that part of them isn't indexed.");
        }
    }

    private static String parseToPlainText(Path file) throws  IOException, SAXException, TikaException {
        BodyContentHandler handler = new BodyContentHandler();

        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        try (InputStream stream = Files.newInputStream(file)) {
            parser.parse(stream, handler, metadata);
            return handler.toString();
        }
    }

    private static String detectLanguage (String text) {

        OptimaizeLangDetector detector = new OptimaizeLangDetector();
        detector.loadModels();
        detector.addText(text.toCharArray(), 0, text.length());

        List<LanguageResult> result = detector.detectAll();
        return result.get(0).getLanguage();
    }

    private static void indexDoc(IndexWriter writer, Path file, boolean newDir) {

        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            return;

        String converted = null;
        String lang = null;
        try {
            converted = parseToPlainText(file);
            lang = detectLanguage(converted);
        }
        catch (IOException | SAXException | TikaException e) {
            System.out.println("Cannot convert content of file to text. The file won't be indexed.");
        }

        if (converted != null) {
            try {
                Document doc = new Document();
                doc.add(new StringField("path", file.toString(), Field.Store.YES));
                doc.add(new StringField("name", file.toFile().getName(), Field.Store.YES));
                if (lang.equals("pl"))
                    doc.add(new TextField("PolishText", converted, Field.Store.YES));
                else
                    doc.add(new TextField("EnglishText", converted, Field.Store.YES));

                if (newDir)
                    System.out.println("\tadding " + file);
                else
                    System.out.println("\tupdating " + file);

                writer.updateDocument(new Term("path", file.toString()), doc);

            }
            catch (IOException e) {
                System.out.println("Cannot update content of file: " + file);
            }
        }
    }

    private static void addInfoAboutDir (IndexWriter writer, Path path, boolean newDir){

        Document doc = new Document();
        doc.add(new StringField("dirName", path.toString(), Field.Store.YES));

        try {
            writer.addDocument(doc);
        } catch (IOException e) {
            System.out.println("Cannot add single document to the Index.");
        }

        if (newDir)
            System.out.println("\tadding info about directory" + path.toString());
        else
            System.out.println("\tupdating info about directory" + path.toString());
    }

    public static void deleteDocs(IndexWriter writer, Path path, boolean definiteDelete) {


        try {
            lookThroughDocsAndDelete(writer, path.toString(), definiteDelete);
        }
        catch (IOException e) {
            System.out.println("Cannot delete directory/file under the path: " + path);
        }
    }

    private static void lookThroughDocsAndDelete (IndexWriter writer, String deletedPath,
                                                  boolean definiteDelete) throws IOException {

        String index = System.getProperty("user.home") + "/.index";
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));

        for (int i = 0; i < reader.maxDoc(); i++) {

            Document doc = reader.document(i);
            String docPath = doc.get("path");
            String dirName = doc.get("dirName");

            if (docPath != null && docPath.indexOf(deletedPath) == 0) {
                writer.deleteDocuments(new Term("path", docPath));
                if (definiteDelete)
                    System.out.println("\tdeleting " + docPath);
            }
            if (dirName != null && dirName.equals(deletedPath)) {
                writer.deleteDocuments(new Term("dirName", dirName));
                if (definiteDelete)
                    System.out.println("\tdeleting info about the directory: " + dirName);
            }
        }
        reader.close();
    }

    public static void updateDoc(IndexWriter writer, Path path) {

        deleteDocs(writer, path, false);
        indexDirectory(writer, path, false);
    }

    public static ArrayList<String> findAllDirectories() {

        ArrayList<String> indexedDirs = new ArrayList<>();
        String index = System.getProperty("user.home") + "/.index";

        try {
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));

            for (int i = 0; i < reader.maxDoc(); i++) {

                Document doc = null;
                try {
                    doc = reader.document(i);
                } catch (IOException e) {
                    System.out.println("Cannot read the single document from the Index.");
                }
                String dirName = null;
                if (doc != null)
                    dirName = doc.get("dirName");
                if (dirName != null)
                    indexedDirs.add(dirName);
            }
            reader.close();
        }
        catch (IOException e) {
            System.out.println("Cannot read directories path from the Index.");
        }

        return indexedDirs;
    }

    public static void list() {

        ArrayList<String> indexedDirs = findAllDirectories();

        System.out.println("List of directories in Index:");
        for (String dirPath: indexedDirs) {
            System.out.println(dirPath);
        }
    }

    public static void reindex (IndexWriter writer) {

        ArrayList<String> indexedDirs = findAllDirectories();
        boolean deleted = false;

        try {
            writer.deleteAll();
            deleted = true;
        }
        catch (IOException e) {
            System.out.println("Cannot delete the Index, and as a consequence cannot reindex.");
        }

        if (deleted) {
            for (String dirPath: indexedDirs) {
                System.out.println("Reindexing the directory: " + dirPath);
                final Path path = Paths.get(dirPath);

                addInfoAboutDir(writer, path, false);
                indexDocs(writer, path, false);
            }
        }
    }

    public static void deleteIndex (IndexWriter writer) {

        try {
            writer.deleteAll();
            System.out.println("I've deleted all directories from the Index.");
        }
        catch (IOException e) {
            System.out.println("Cannot delete the index.");
        }
    }

    public static IndexWriter createWriter() throws IOException {

        String indexPath = System.getProperty("user.home") + "/.index";
        Directory dir = FSDirectory.open(Paths.get(indexPath));

        HashMap<String, Analyzer> map = new HashMap<>();
        map.put("EnglishText", new EnglishAnalyzer());
        map.put("PolishText", new PolishAnalyzer());
        Analyzer analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), map);

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        return new IndexWriter(dir, iwc);
    }
}
