package ru.maklas.mnet2.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import ru.maklas.mnet2.Serializer;

public class KryoSerializer implements Serializer {

    private final Kryo kryo;
    private final Input input;
    private final Output output;

    public KryoSerializer(Kryo kryo, int bufferSize) {
        this.kryo = kryo;
        input = new Input(bufferSize);
        output = new Output(bufferSize);
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
    public byte[] serializeOff5(Object o) {
        synchronized (kryo){
            output.setPosition(5);
            kryo.writeClassAndObject(output, o);
            return output.toBytes();
        }
    }

    @Override
    public boolean useOffsetSerialization() {
        return true;
    }

    public Kryo getKryo(){
        return kryo;
    }


}
