package ru.maklas.mrudp2;

import java.net.SocketException;

public class ServerLooper implements Runnable {

    ServerSocket serverSocket;
    private final ConnectionProcessor auth;

    public ServerLooper(int port, ConnectionProcessor auth) throws SocketException {
        this.serverSocket = new ServerSocket(new PacketLossUDPSocket(new JavaUDPSocket(port), 50), 512, auth);
        this.auth = auth;
        new Thread(this).start();
    }


    @Override
    public void run() {
        while (!serverSocket.isClosed()) {
            serverSocket.update();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {}
        }
    }
}
