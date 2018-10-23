package ru.maklas.mnet2;

/**
 * Job of the processor is to either accept new connection and add to the pool, or reject it.
 */
public interface ByteConnectionProcessor {

    /**
     * Make a decision if you want to accept new connection here.
     */
    Response<byte[]> acceptConnection(ByteSocket socket, byte[] userData);
}
