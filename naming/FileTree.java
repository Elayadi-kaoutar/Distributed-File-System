/**
 * FileTree.java
 *
 * The directory tree of the distributed file system, kept in memory by the
 * naming server, and the list of registered storage servers.
 */
package naming;

import java.util.ArrayList;
import java.util.List;

public class FileTree {
    private final Node root = Node.root();
    private final List<StorageInfo> servers = new ArrayList<>();

    /**
     * Registers a storage server and merges its files into the tree.
     * A file is added, with its missing parent directories, unless it is a
     * duplicate: the path already exists (as a file or as a directory), or one
     * of its parents is a file. Invalid paths and the root "/" are ignored.
     *
     * @return the files the storage server must delete locally.
     * @throws IllegalStateException if this storage server is already registered.
     */
    public synchronized List<String> register(StorageInfo server, String[] files) {
        if (servers.contains(server)) {
            throw new IllegalStateException("This storage client already registered.");
        }
        servers.add(server);

        List<String> duplicates = new ArrayList<>();
        for (String path : files) {
            List<String> parts = parse(path);
            if (parts == null || parts.isEmpty()) {
                continue; // invalid path or root: silently ignored
            }
            if (!insertFile(parts, server)) {
                duplicates.add(path);
            }
        }
        return duplicates;
    }

    /**
     * Splits a path into its components, dropping empty ones.
     *
     * @return the components, or null if the path is invalid (it must start
     *         with '/', contain no ':' and no ".." component).
     */
    private static List<String> parse(String path) {
        if (path == null || !path.startsWith("/") || path.contains(":")) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                return null;
            }
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * Adds a file to the tree. Nothing is created unless the whole path can be
     * added, so a rejected file leaves no stray directory behind.
     *
     * @return false if the file is a duplicate or a file blocks its path.
     */
    private boolean insertFile(List<String> parts, StorageInfo server) {
        int last = parts.size() - 1;
        Node dir = root;
        int i = 0;

        // Walk through the parent directories that already exist.
        for (; i < last; i++) {
            Node child = dir.children.get(parts.get(i));
            if (child == null) {
                break;
            }
            if (!child.directory) {
                return false; // a file in the way
            }
            dir = child;
        }
        // All parents exist: the name itself must be free.
        if (i == last && dir.children.containsKey(parts.get(last))) {
            return false;
        }

        // Create the missing directories, then the file.
        for (; i < last; i++) {
            Node created = Node.directory(parts.get(i), dir);
            dir.children.put(created.name, created);
            dir = created;
        }
        dir.children.put(parts.get(last), Node.file(parts.get(last), dir, server));
        return true;
    }
}
