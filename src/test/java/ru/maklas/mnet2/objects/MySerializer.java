package ru.maklas.mnet2.objects;

import com.badlogic.gdx.utils.Supplier;
import com.esotericsoftware.kryo.Kryo;
import ru.maklas.mnet2.serialization.KryoSerializerProvider;

public class MySerializer extends KryoSerializerProvider{

    public MySerializer() {
        super(512, new Supplier<Kryo>() {
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
