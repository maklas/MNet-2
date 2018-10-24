package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Array;

/**
 * Used to send packets in a batch. All individual packets still must of size less than bufferSize.
 */
public class ByteBatch {

    final Array<byte[]> array;

    public ByteBatch(int minSize) {
        this.array = new Array<byte[]>(minSize);
    }

    public ByteBatch() {
        this.array = new Array<byte[]>();
    }

    public void add(byte[] bytes){
        array.add(bytes);
    }

    public void clear(){
        array.clear();
    }

    public int size(){
        return array.size;
    }

    public byte[] get(int i){
        return array.get(i);
    }

    public void remove(int i){
        array.removeIndex(i);
    }

    public int calculateSize(){
        int sum = 6;
        for (byte[] bytes : array) {
            sum += bytes.length;
        }
        return sum;
    }

}
