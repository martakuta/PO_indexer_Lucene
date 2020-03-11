package pl.edu.mimuw.mm408932;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

import org.apache.lucene.index.IndexWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchDir {

    private static Logger logger = LoggerFactory.getLogger(WatchDir.class);

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;

    @SuppressWarnings("unchecked")
    private  static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

        Path prev = keys.get(key);
        if (prev == null) {
            logger.info("register: {}", dir);
        } else {
            if (!dir.equals(prev)) {
                logger.info("update: {} -> {}", prev, dir);
            }
        }
        keys.put(key, dir);
    }

    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void processEvents() {
        for (; ; ) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                logger.warn("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // TBD - provide example of how OVERFLOW event is handled
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                logger.info("{}: {}", event.kind().name(), child);

                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        // ignore to keep sample readable
                    }
                }

                try (IndexWriter writer = IndexFiles.createWriter()) {

                    if (kind == ENTRY_CREATE) {
                        IndexFiles.indexDirectory(writer, child, false);
                    } else if (kind == ENTRY_DELETE) {
                        System.out.println("------------------" + child.toString());
                        IndexFiles.deleteDocs(writer, child, true);
                    } else if (kind == ENTRY_MODIFY) {
                        IndexFiles.updateDoc(writer, child);
                    }
                }
                catch (IOException e) {
                    logger.warn("Cannot make changes after creating / updating / deleting.", e);
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    private WatchDir(ArrayList<String> indexedDirs) throws IOException {

        this.watcher = FileSystems.getDefault()
                .newWatchService();
        this.keys = new HashMap<WatchKey, Path>();


        for (String dir : indexedDirs) {
            Path path = Paths.get(dir);
            logger.info("Scanning {} ...", dir);
            try {
                registerAll(path);
            }
            catch (IOException e) {
                logger.warn("Cannot register directory: " + path +
                        "Watcher won't notice any changes in it and its subdirectories");
            }
            logger.info("Done.");
        }
    }

    public static void watchIndex(ArrayList<String> indexedDirs) {

        Runtime.getRuntime()
                .addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        logger.info("Exiting...");
                    }
                });

        WatchDir watcher = null;
        try {
            watcher = new WatchDir(indexedDirs);
        }
        catch (IOException e) {
            logger.error("Cannot create watcher.");
        }
        System.out.println("I've finished registering directories.\n\nI'm observing directories in Index and waiting for changes...");
        if (watcher != null)
            watcher.processEvents();
    }
}
