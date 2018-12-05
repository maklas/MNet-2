package ru.maklas.mnet2.serialization;

import com.esotericsoftware.kryo.Kryo;
import ru.maklas.mnet2.Supplier;

public class KryoSerializerProvider implements Supplier<Serializer> {

    private final int bufferSize;
    private final Supplier<Kryo> kryoProvider;

    public KryoSerializerProvider(int bufferSize, Supplier<Kryo> kryoProvider) {

        this.bufferSize = bufferSize;
        this.kryoProvider = kryoProvider;
    }

    @Override
    public final Serializer get() {
        return new KryoSerializer(kryoProvider.get(), bufferSize);
    }

}
