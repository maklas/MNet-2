package ru.maklas.mnet2.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class KryoSerializer implements Serializer {

    private final Kryo kryo;
    private final Input input;
    private final Output output;

    public KryoSerializer(Kryo kryo, int bufferSize) {
        this.kryo = kryo;
        input = new Input(bufferSize);
        output = new Output(bufferSize);
    }

    public Kryo getKryo(){
        return kryo;
    }

    @Override
    public byte[] serialize(Object o) {
        synchronized (kryo){
            output.setPosition(0);
            kryo.writeClassAndObject(output, o);
            return output.toBytes();
        }
    }

    @Override
    public byte[] serialize(Object o, int offset) {
        synchronized (kryo){
            output.setPosition(offset);
            kryo.writeClassAndObject(output, o);
            return output.toBytes();
        }
    }

    @Override
    public int serialize(Object o, byte[] buffer, int offset) {
        byte[] serialized = serialize(o);
        System.arraycopy(serialized, 0, buffer, offset, serialized.length);
        return serialized.length;
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes.length == 0){
            return null;
        }
        synchronized (kryo){
            input.setBuffer(bytes);
            return kryo.readClassAndObject(input);
        }
    }

    @Override
    public Object deserialize(byte[] bytes, int offset, int length) {
        if (length <= 0) return null;
        synchronized (kryo){
            input.setBuffer(bytes, offset, length);
            return kryo.readClassAndObject(input);
        }
    }
}
