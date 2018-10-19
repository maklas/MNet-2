package ru.maklas.mrudp2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;

public class PacketLossUDPSocket implements UDPSocket{

    private UDPSocket delegate;
    private double packetLossChance;

    /**
     * Creates {@link UDPSocket} out of existing {@link UDPSocket}, but this one gets a chanse of <b>not</b>
     * sending a {@link DatagramPacket} when asked to. Used for testing
     * @param delegate which socket to use to send Datagrams
     * @param packetLossChance Probability of a packet loss. Must be >=0 and <=100. Measured in percents %
     */
    public PacketLossUDPSocket(UDPSocket delegate, double packetLossChance) {
        if (packetLossChance < 0 || packetLossChance > 100){
            throw new RuntimeException("Packet loss chance must be from 0 to 100%");
        }
        this.delegate = delegate;
        this.packetLossChance = packetLossChance / 100;
    }

    @Override
    public int getLocalPort() {
        return delegate.getLocalPort();
    }

    @Override
    public void send(DatagramPacket packet) throws IOException {
        double random = Math.random();
        if (random > packetLossChance)
            delegate.send(packet);
    }

    @Override
    public void receive(DatagramPacket packet) throws IOException {
        delegate.receive(packet);
    }

    @Override
    public void setReceiveTimeout(int millis) throws SocketException {
        delegate.setReceiveTimeout(millis);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void connect(InetAddress address, int port) {
        delegate.connect(address, port);
    }
}
