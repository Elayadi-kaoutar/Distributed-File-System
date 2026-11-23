
/**
 * StorageHandlers.java
 *
 * HTTP handlers of the storage server: size, read and write (client
 * interface) and create and delete (command interface).
 */
package storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jsonhelper.BooleanReturn;
import jsonhelper.DataReturn;
import jsonhelper.PathRequest;
import jsonhelper.ReadRequest;
import jsonhelper.SizeReturn;
import jsonhelper.WriteRequest;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Base64;

public class StorageHandlers {
    private final File root;

    public StorageHandlers(File root) {
        this.root = root;
    }

    /** Code run once the request is a valid POST with a parsed JSON body. */
    private interface Action<T> {
        void run(HttpExchange exchange, T request) throws IOException;
    }

    /**
     * Builds a handler that enforces POST, parses the JSON body (400 if
     * invalid) and then runs the given action.
     */
    private <T> HttpHandler post(Class<T> type, Action<T> action) {
        return exchange -> {
            try {
                if (!HttpUtil.requirePost(exchange)) {
                    return;
                }
                T request = HttpUtil.readJson(exchange, type);
                if (request == null) {
                    HttpUtil.sendBadRequest(exchange);
                    return;
                }
                action.run(exchange, request);
            } catch (Exception e) {
                try {
                    HttpUtil.sendError(exchange, 500, "IOException", String.valueOf(e));
                } catch (Exception ignored) {
                    // response already started, nothing more to do
                }
            } finally {
                exchange.close();
            }
        };
    }

    /**
     * Converts a path to a local file. Sends IllegalArgumentException and
     * returns null if the path is invalid.
     */
    private File resolve(HttpExchange exchange, String path) throws IOException {
        try {
            return PathUtil.toLocalFile(root, path);
        } catch (IllegalArgumentException e) {
            HttpUtil.sendError(exchange, 404, "IllegalArgumentException", e.getMessage());
            return null;
        }
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        HttpUtil.sendError(exchange, 404, "FileNotFoundException", "File/path cannot be found.");
    }

    // ---------------------------------------------------------------- client

    /** POST /storage_size */
    public HttpHandler size() {
        return post(PathRequest.class, (exchange, req) -> {
            File file = resolve(exchange, req.path);
            if (file == null) {
                return;
            }
            if (!file.isFile()) {
                notFound(exchange);
                return;
            }
            HttpUtil.sendJson(exchange, 200, new SizeReturn(file.length()));
        });
    }

    /** POST /storage_read */
    public HttpHandler read() {
        return post(ReadRequest.class, (exchange, req) -> {
            File file = resolve(exchange, req.path);
            if (file == null) {
                return;
            }
            if (!file.isFile()) {
                notFound(exchange);
                return;
            }
            if (req.offset < 0 || req.length < 0 || req.offset + req.length > file.length()) {
                HttpUtil.sendError(exchange, 404, "IndexOutOfBoundsException",
                        "Range is outside the file.");
                return;
            }
            byte[] buffer = new byte[req.length];
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(req.offset);
                raf.readFully(buffer);
            } catch (IOException e) {
                HttpUtil.sendError(exchange, 500, "IOException", e.getMessage());
                return;
            }
            HttpUtil.sendJson(exchange, 200,
                    new DataReturn(Base64.getEncoder().encodeToString(buffer)));
        });
    }

    /** POST /storage_write */
    public HttpHandler write() {
        return post(WriteRequest.class, (exchange, req) -> {
            File file = resolve(exchange, req.path);
            if (file == null) {
                return;
            }
            if (!file.isFile()) {
                notFound(exchange);
                return;
            }
            if (req.offset < 0) {
                HttpUtil.sendError(exchange, 404, "IndexOutOfBoundsException",
                        "Offset is negative.");
                return;
            }
            byte[] data;
            try {
                data = Base64.getDecoder().decode(req.data);
            } catch (IllegalArgumentException | NullPointerException e) {
                HttpUtil.sendBadRequest(exchange);
                return;
            }
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(req.offset); // writing past the end grows the file
                raf.write(data);
            } catch (IOException e) {
                HttpUtil.sendError(exchange, 500, "IOException", e.getMessage());
                return;
            }
            HttpUtil.sendJson(exchange, 200, new BooleanReturn(true));
        });
    }

    // --------------------------------------------------------------- command

    /** POST /storage_create */
    public HttpHandler create() {
        return post(PathRequest.class, (exchange, req) -> {
            File file = resolve(exchange, req.path);
            if (file == null) {
                return;
            }
            boolean success = false;
            if (!file.equals(root) && !file.exists()) {
                File parent = file.getParentFile();
                if (parent.isDirectory() || parent.mkdirs()) {
                    try {
                        success = file.createNewFile();
                    } catch (IOException e) {
                        success = false;
                    }
                }
            }
            HttpUtil.sendJson(exchange, 200, new BooleanReturn(success));
        });
    }

    /** POST /storage_delete */
    public HttpHandler delete() {
        return post(PathRequest.class, (exchange, req) -> {
            File file = resolve(exchange, req.path);
            if (file == null) {
                return;
            }
            boolean success = false;
            if (!file.equals(root) && file.exists()) {
                success = deleteRecursively(file);
                if (success) {
                    pruneEmptyParents(file.getParentFile());
                }
            }
            HttpUtil.sendJson(exchange, 200, new BooleanReturn(success));
        });
    }

    /** Deletes a file, or a directory with everything inside it. */
    private static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        return file.delete();
    }

    /** Removes directories that became empty, going up but never the root. */
    private void pruneEmptyParents(File dir) {
        while (dir != null && !dir.equals(root)) {
            String[] content = dir.list();
            if (content == null || content.length > 0 || !dir.delete()) {
                return;
            }
            dir = dir.getParentFile();
        }
    }
}