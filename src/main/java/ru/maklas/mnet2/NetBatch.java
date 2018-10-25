package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Array;
import ru.maklas.mnet2.serialization.Serializer;

/**
 * <p>You can send your data via MNet in batches. Just create new instance and put all serializable objects inside of it,
 * then send with {@link Socket#send(NetBatch)}. Receiver will get them as if they all were sent with
 * {@link Socket#send(Object)}.
 * <p>NetBatch is reusable. Just call clear() after sending it and you can fill it up again.
 * Be careful. If you try to send too much data it won't fit with
 */
public class NetBatch {

    private final Array<Object> objects;
    private final ByteBatch byteBatch = new ByteBatch();

    public NetBatch(int minSize) {
        objects = new Array<Object>(minSize);
    }

    public NetBatch() {
        this.objects = new Array<Object>();
    }

    public void add(Object o){
        objects.add(o);
    }

    public int size(){
        return objects.size;
    }

    public Object get(int i){
        return objects.get(i);
    }

    public Object remove(int i){
        return objects.removeIndex(i);
    }

    public boolean remove(Object o){
        return objects.removeValue(o, true);
    }

    public void clear(){
        objects.clear();
    }

    /**
     * Converts this NetBatch to MRUDPBatch to be sent via MRUDPSocket
     */
    public ByteBatch convertAndGet(Serializer serializer){
        ByteBatch byteBatch = this.byteBatch;
        byteBatch.clear();
        for (Object o : objects) {
            byteBatch.add(serializer.serialize(o));
        }
        return byteBatch;
    }

    /**
     * Used only for testing batch byte[]-sizes!
     */
    public int testCalculateSize(Serializer serializer){
        this.byteBatch.clear();
        return convertAndGet(serializer).calculateSize();
    }

}
