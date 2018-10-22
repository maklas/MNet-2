package ru.maklas.mrudp2;

/**
 * Job of the processor is to either accept new connection and add to the pool, or reject it.
 */
public interface ConnectionProcessor {

    /**
     * Make a decision if you want to accept new connection here.
     */
    Response<byte[]> acceptConnection(Socket socket, byte[] userData);
}
