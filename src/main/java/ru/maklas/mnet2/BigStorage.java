package ru.maklas.mnet2;

public class BigStorage {

    private int totalPackets;
    public SortedIntList<byte[]> parts;

    public BigStorage(int totalPackets) {
        this.totalPackets = totalPackets;
        parts = new SortedIntList<byte[]>();
    }

    public boolean put(int id, byte[] part){
        parts.insert(id, part);
        return parts.size >= totalPackets;
    }


}
