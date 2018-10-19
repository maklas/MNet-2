package ru.maklas.mrudp2;

import java.util.ArrayList;

public class MRUDPBatch {

    final ArrayList<byte[]> array;

    public MRUDPBatch(int minSize) {
        this.array = new ArrayList<byte[]>(minSize);
    }

    public MRUDPBatch() {
        this.array = new ArrayList<byte[]>();
    }

    public void add(byte[] bytes){
        array.add(bytes);
    }

    public void clear(){
        array.clear();
    }

    public int size(){
        return array.size();
    }

    public byte[] get(int i){
        return array.get(i);
    }

    public int calculateSize(){
        int sum = 6;
        for (byte[] bytes : array) {
            sum += bytes.length;
        }
        return sum;
    }

}
