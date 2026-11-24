/**
 * Registration.java
 *
 * Registration of the storage server with the naming server, done once at
 * startup: send the list of local files, delete the duplicates the naming
 * server asks for, then prune empty directories.
 */
package storage;

import com.google.gson.Gson;
import jsonhelper.FilesReturn;
import jsonhelper.RegisterRequest;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public final class Registration {
    private static final Gson GSON = new Gson();

    private Registration() {
    }

    /**
     * Registers with the naming server.
     *
     * @return true if the registration succeeded, false if the naming server
     *         answered 409 (this storage server is already registered).
     * @throws IOException if the naming server cannot be reached or answers
     *                     with an unexpected status.
     */
    public static boolean register(File root, String ip, int clientPort, int commandPort,
                                   int namingPort) throws IOException {
        List<String> files = new ArrayList<>();
        collectFiles(root, "", files);

        RegisterRequest request =
                new RegisterRequest(ip, clientPort, commandPort, files.toArray(new String[0]));
        HttpResponse<String> response = Remote.post("localhost", namingPort, "/register", request);

        if (response.statusCode() == 409) {
            return false;
        }
        if (response.statusCode() != 200) {
            throw new IOException("Registration failed, status " + response.statusCode());
        }

        FilesReturn duplicates = GSON.fromJson(response.body(), FilesReturn.class);
        if (duplicates != null && duplicates.files != null) {
            for (String path : duplicates.files) {
                PathUtil.toLocalFile(root, path).delete();
            }
        }
        prune(root, root);
        return true;
    }

    /** Adds every file below dir as a distributed path ("/a/b.txt"). */
    private static void collectFiles(File dir, String prefix, List<String> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String path = prefix + "/" + child.getName();
            if (child.isDirectory()) {
                collectFiles(child, path, out);
            } else {
                out.add(path);
            }
        }
    }

    /**
     * Deletes every directory below dir that contains no file, including
     * directories holding only empty directories. The root itself is kept.
     */
    private static void prune(File dir, File root) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    prune(child, root);
                }
            }
        }
        String[] left = dir.list();
        if (!dir.equals(root) && left != null && left.length == 0) {
            dir.delete();
        }
    }
}