/**
 * StorageHandlers.java
 *
 * HTTP handlers of the storage server: size, read and write (client
 * interface) and create and delete (command interface).
 */
package storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import commun.HttpUtil;
import commun.Remote;
import jsonhelper.BooleanReturn;
import jsonhelper.CopyRequest;
import jsonhelper.DataReturn;
import jsonhelper.ExceptionReturn;
import jsonhelper.PathRequest;
import jsonhelper.ReadRequest;
import jsonhelper.SizeReturn;
import jsonhelper.WriteRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.http.HttpResponse;
import java.util.Base64;

public class StorageHandlers {
    private static final int CHUNK = 1 << 20; // copy by blocks of 1 MB
    private static final Gson GSON = new Gson();

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
    public static  <T> HttpHandler post(Class<T> type, Action<T> action) {
        return exchange -> {
            try{
            if(!HttpUtil.requirePost(exchange)){
                return;
            }
            T req =HttpUtil.readJson(exchange,type);
            if(req==null){
                HttpUtil.sendBadRequest(exchange);
                return;
            }
            action.run(exchange,req);
        }catch (Exception e) {
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


    /** POST /storage_copy */
    public HttpHandler copy() {
        return post(CopyRequest.class, (exchange, req) -> {
            File file = resolve(exchange, req.path);
            if (file == null) {
                return;
            }
            if (file.equals(root) || file.isDirectory()) {
                HttpUtil.sendError(exchange, 404, "IOException", "Cannot copy onto a directory.");
                return;
            }
            try {
                String problem = copyFrom(req, file);
                if (problem != null) {
                    String type = problem.equals("FileNotFoundException")
                            ? "FileNotFoundException" : "IOException";
                    HttpUtil.sendError(exchange, 404, type, problem);
                    return;
                }
            } catch (IOException e) {
                HttpUtil.sendError(exchange, 404, "IOException", e.getMessage());
                return;
            }
            HttpUtil.sendJson(exchange, 200, new BooleanReturn(true));
        });
    }

    /**
     * Downloads the file from the other storage server into target.
     *
     * @return null on success, otherwise the exception type reported by the
     *         remote server (e.g. "FileNotFoundException").
     */
    private String copyFrom(CopyRequest req, File target) throws IOException {
        HttpResponse<String> size = Remote.post(req.server_ip, req.server_port,
                "/storage_size", new PathRequest(req.path));
        if (size.statusCode() != 200) {
            return remoteError(size);
        }
        long total = GSON.fromJson(size.body(), SizeReturn.class).size;

        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create parent directories.");
        }
        try (FileOutputStream out = new FileOutputStream(target)) { // truncates
            for (long offset = 0; offset < total; offset += CHUNK) {
                int length = (int) Math.min(CHUNK, total - offset);
                HttpResponse<String> part = Remote.post(req.server_ip, req.server_port,
                        "/storage_read", new ReadRequest(req.path, offset, length));
                if (part.statusCode() != 200) {
                    target.delete();
                    return remoteError(part);
                }
                out.write(Base64.getDecoder().decode(GSON.fromJson(part.body(), DataReturn.class).data));
            }
        }
        return null;
    }

    /** Extracts the exception type of a failed remote answer. */
    private static String remoteError(HttpResponse<String> response) {
        try {
            return GSON.fromJson(response.body(), ExceptionReturn.class).exception_type;
        } catch (RuntimeException e) {
            return "IOException";
        }
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