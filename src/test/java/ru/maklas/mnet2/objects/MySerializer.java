package ru.maklas.mnet2.objects;

import com.esotericsoftware.kryo.Kryo;
import ru.maklas.mnet2.Supplier;
import ru.maklas.mnet2.serialization.KryoSerializerProvider;

public class MySerializer extends KryoSerializerProvider{

    public MySerializer() {
        this(512);
    }
    public MySerializer(int bufferSize) {
        super(bufferSize, new Supplier<Kryo>() {
            @Override
            public Kryo get() {
                return fillKryo(new Kryo());
            }
        });
    }


    private static Kryo fillKryo(Kryo kryo){

        kryo.register(ConnectionRequest.class, 1);
        kryo.register(ConnectionResponse.class, 2);
        kryo.register(UpdateObject.class, 3);

        return kryo;
    }
}
