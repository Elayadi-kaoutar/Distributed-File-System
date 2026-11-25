package naming;

public class StorageInfo {
    private final String ip ;
    private final int clientPort;
    private final int commandPort;
    StorageInfo(String ip, int clientPort, int commandPort){
        this.ip = ip;
        this.clientPort = clientPort;
        this.commandPort = commandPort;
    }
    @Override
    public boolean equals(Object o){
        if(o == this) return true;
        if(!(o instanceof StorageInfo)) return false;
        StorageInfo storageinfo = (StorageInfo)o;
        return this.ip.equals(storageinfo.ip) && this.clientPort == storageinfo.clientPort;
    }
    @Override
    public int hashCode(){
        return this.ip.hashCode() * 31 + this.clientPort;
    }
    @Override
    public String toString() {
        return ip + ":" + clientPort + "/" + commandPort;
    }

}
