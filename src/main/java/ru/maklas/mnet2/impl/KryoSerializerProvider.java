package ru.maklas.mnet2.impl;

import com.esotericsoftware.kryo.Kryo;
import ru.maklas.mnet2.Provider;
import ru.maklas.mnet2.Serializer;

public class KryoSerializerProvider implements Provider<Serializer> {

    private final int bufferSize;
    private final Provider<Kryo> kryoProvider;

    public KryoSerializerProvider(int bufferSize, Provider<Kryo> kryoProvider) {

        this.bufferSize = bufferSize;
        this.kryoProvider = kryoProvider;
    }

    @Override
    public final Serializer provide() {
        return new KryoSerializer(kryoProvider.provide(), bufferSize);
    }

}
