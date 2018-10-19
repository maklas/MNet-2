package ru.maklas.mrudp2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Created by amaklakov on 02.11.2017.
 * Interface, abstracting DatagramSocket and allowing to replace implementation with some custom code
 */
public interface UDPSocket {

    /**
     * Local port to which this socket is bind
     */
    int getLocalPort();

    /**
     * Sends containment of {@link DatagramPacket}. Throws {@link IOException} in bad cases
     */
    void send(DatagramPacket packet) throws IOException ;

    /**
     * Blocks until the next datagram is received.
     * Throws {@link SocketException} if socket is getting close
     */
    void receive(DatagramPacket packet) throws IOException;

    void setReceiveTimeout(int millis) throws SocketException;

    /**
     * Any thread currently blocked in {@link #receive} upon this socket
     * will throw a {@link SocketException}.
     */
    void close();

    /**
     * @return Whether this socket is closed.
     */
    boolean isClosed();

    void connect(InetAddress address, int port);
}
