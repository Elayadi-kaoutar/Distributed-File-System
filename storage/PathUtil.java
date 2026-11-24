

package storage;

import java.io.File;
import java.io.IOException;

public final class PathUtil {
    private PathUtil() {
    }

    /**
     * A valid path starts with '/', contains no ':' and contains no ".."
     * component (which could escape the root directory).
     */
    public static boolean isValid(String path) {
        if (path == null || !path.startsWith("/") || path.contains(":")) {
            return false;
        }
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts a distributed path to the matching local file. Empty components
     * are dropped, so "/" is the root itself and "//a///b" is "/a/b".
     *
     * @throws IllegalArgumentException if the path is invalid or resolves
     *                                  outside the root directory.
     */
    public static File toLocalFile(File root, String path) {
        if (!isValid(path)) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        File file = root;
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                file = new File(file, part);
            }
        }
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
                throw new IllegalArgumentException("Path escapes root: " + path);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot resolve path: " + path);
        }
        return file;
    }
}