package naming;

import storage.StorageServer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node {
    public final String name;
    public final Boolean directory;
    public final List<StorageInfo>storages = new ArrayList<>();
    public final Node parent ;
    public final HashMap<String,Node> children = new HashMap<>();

    public Node(String name, Boolean directory, Node parent) {
        this.name = name;
        this.directory = directory;
        this.parent = parent;
    }
    public  static Node root(){
    return new Node("" , true,null);}
    public static Node directory(String name, Node parent) {
        return new Node(name, true, parent);
    }

    public static Node file(String name, Node parent, StorageInfo server) {
        Node node = new Node(name, false, parent);
        node.storages.add(server);
        return node;
    }
    public  Node getChild(String name){
        return children.get(name) ;
    }
    public boolean hasChild(String name){
        return children.containsKey(name) ;
    }
    public void addChild(String name, boolean isDir){
        if(!directory)throw new IllegalStateException(name+" is not a directory");
      Node child = new Node(name, isDir, this) ;
      children.put(name,child) ;
    }
    public Map<String,Node>getChildren(){return children;}
    public List<StorageInfo>getStorages(){return storages;}
    public void addStorage(StorageInfo storage){
       if(!storages.contains(storage)) storages.add(storage) ;
    }
    public String getPath(){
        if(parent==null) return "/";
        return parent.getPath()+"/"+name;
    }

}

