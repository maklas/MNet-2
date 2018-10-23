package ru.maklas.mrudp2;

/**
 * Class that implement this method can process events coming from Socket
 */
public interface SocketProcessor {

    /**
     * Receives next packet from connected socket. Acts like iterator (socket.forEachNextPacket( (data) -> {}))
     * can be interrupted with help of SocketIterator.
     * @param sock socket from which data has come.
     * @param data data that's received
     */
    void process(ByteSocket sock, byte[] data);

}
