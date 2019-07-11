package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.AtomicQueue;
import ru.maklas.mnet2.serialization.Serializer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;

/**
 * Job of a server socket is to accept new connections and handle subsockets.
 */
public class ServerSocket {

    final UDPSocket udp;
    private final ServerAuthenticator authenticator;
    final SocketMap socketMap;
    private AtomicQueue<ConnectionRequest> connectionRequests;
    DatagramPacket sendPacket; //update thread
    int inactivityTimeout = 15000;
    int bufferSize = 512;
    int pingFrequency = 2500;
    int resendFrequency = 125;
    Serializer serializer;
    Supplier<Serializer> serializerSupplier;

    public ServerSocket(int port, ServerAuthenticator authenticator, Supplier<Serializer> serializerSupplier) throws SocketException {
        this(new JavaUDPSocket(port), 512, 15000, 2500, 125, authenticator, serializerSupplier);
    }
    public ServerSocket(UDPSocket udp, int bufferSize, int inactivityTimeout, int pingFrequency, int resendFrequency, ServerAuthenticator authenticator, Supplier<Serializer> serializerSupplier) {
        this.udp = udp;
        this.bufferSize = bufferSize;
        this.inactivityTimeout = inactivityTimeout;
        this.pingFrequency = pingFrequency;
        this.resendFrequency = resendFrequency;
        this.authenticator = authenticator;
        this.socketMap = new SocketMap();
        this.connectionRequests = new AtomicQueue<ConnectionRequest>(1000);
        this.sendPacket = new DatagramPacket(new byte[0], 0);
        this.serializerSupplier = serializerSupplier;
        this.serializer = serializerSupplier.get();
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket.this.run();
            }
        }).start();
    }

    void run(){
        UDPSocket udp = this.udp;
        byte[] buffer = new byte[bufferSize];
        DatagramPacket packet = new DatagramPacket(buffer, bufferSize);
        int len;
        SocketMap socketMap = this.socketMap;

        while (true){
            try {
                udp.receive(packet);
            } catch (IOException e) {
                if (udp.isClosed()){
                    break;
                }
                continue;
            }
            len = packet.getLength();
            byte type = buffer[0];
            if (len <= 5) continue;

            SocketImpl mSocket = socketMap.get(packet);
            if (mSocket != null){
                mSocket.receiveData(buffer, type, len);
            } else if (type == PacketType.connectionRequest){
                Object req;
                try {
                    req = serializer.deserialize(buffer, 1, len - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                    req = null;
                }
                connectionRequests.put(new ConnectionRequest(packet.getAddress(), packet.getPort(), req));
            }
        }
    }

    public void update(){
        updateDCAndSockets();
        processAuth();
    }

    /**
     * Calls ConnectionProcessor to accept new connections as they arrive
     */
    private void processAuth() {
        ConnectionRequest poll = connectionRequests.poll();
        while (poll != null){
            if (socketMap.get(poll.address, poll.port) != null) {
                poll = connectionRequests.poll();
                continue; //Отбрасываем если кто-то уже коннектился и подтвердился.
            }

            //Создаём полупустой сокет
            SocketImpl socket = new SocketImpl(udp, poll.address, poll.port, bufferSize);
            //Авторизация
            Connection conn = new Connection(this, socket, poll.userRequest);
            authenticator.acceptConnection(conn);
            if (!conn.isMadeChoice()){
                conn.reject(null);
            }

            poll = connectionRequests.poll();
        }
    }

    /**
     * Отключает сокеты которые давно не отвечали
     */
    private void updateDCAndSockets(){
        long now = System.currentTimeMillis();
        synchronized (socketMap){
            for (SocketMap.SocketWrap wrap : socketMap.sockets) {
                SocketImpl socket = wrap.socket;
                if (socket.isConnected()) {
                    socket.checkResendPingAndInactivity(now);
                }
            }
        }
    }

    /**
     * @return how many sockets are connected right now
     */
    public int getSize(){
        return socketMap.size();
    }



    //***********//
    //* GET-SET *//
    //***********//

    public boolean isClosed() {
        return udp.isClosed();
    }

    public UDPSocket getUdp() {
        return udp;
    }

    public Array<Socket> getSockets(){
        return getSockets(new Array<Socket>());
    }

    public Array<Socket> getSockets(Array<Socket> sockets){
        if (sockets.size > 0) sockets.clear();
        synchronized (socketMap){
            for (SocketMap.SocketWrap socket : socketMap.sockets) {
                sockets.add(socket.socket);
            }
        }
        return sockets;
    }

    void removeMe(SocketImpl socket) {
        socketMap.remove(socket);
    }

    public void close(){
        Array<Socket> sockets = getSockets();
        for (Socket socket : sockets) {
            socket.close(DCType.SERVER_SHUTDOWN);
        }
        udp.close();
    }

    private class ConnectionRequest {
        InetAddress address;
        int port;
        Object userRequest;

        public ConnectionRequest(InetAddress address, int port, Object userRequest) {
            this.address = address;
            this.port = port;
            this.userRequest = userRequest;
        }
    }
}
