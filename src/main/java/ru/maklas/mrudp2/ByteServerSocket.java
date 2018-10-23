package ru.maklas.mrudp2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Iterator;

/**
 * Job of a server socket is to accept new connections and handle subsockets.
 */
public class ByteServerSocket {

    final UDPSocket udp;
    private final ConnectionProcessor connectionProcessor;
    private final SocketMap socketMap;
    private AtomicQueue<ConnectionRequest> connectionRequests;
    private DatagramPacket sendPacket; //update thread
    private int inactivityTimeout = 15000;
    private int bufferSize = 512;
    private int pingFrequency = 2500;
    private int resendFrequency = 125;

    public ByteServerSocket(int port, ConnectionProcessor connectionProcessor) throws SocketException {
        this(new JavaUDPSocket(port), 512, 15000, 2500, 125, connectionProcessor);
    }
    public ByteServerSocket(UDPSocket udp, int bufferSize, int inactivityTimeout, int pingFrequency, int resendFrequency, ConnectionProcessor connectionProcessor) {
        this.udp = udp;
        this.bufferSize = bufferSize;
        this.inactivityTimeout = inactivityTimeout;
        this.pingFrequency = pingFrequency;
        this.resendFrequency = resendFrequency;
        this.connectionProcessor = connectionProcessor;
        this.socketMap = new SocketMap();
        this.connectionRequests = new AtomicQueue<ConnectionRequest>(1000);
        this.sendPacket = new DatagramPacket(new byte[0], 0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ByteServerSocket.this.run();
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

            ByteSocket mSocket = socketMap.get(packet);
            if (mSocket != null){
                mSocket.receiveData(buffer, type, len);
            } else if (type == PacketType.connectionRequest){
                byte[] userRequest = new byte[len - 1];
                System.arraycopy(buffer, 1, userRequest, 0, len - 1);
                connectionRequests.put(new ConnectionRequest(packet.getAddress(), packet.getPort(), userRequest));
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
            if (socketMap.get(poll.address, poll.port) != null) continue; //Отбрасываем если кто-то уже коннектился и подтвердился.

            //Создаём полупустой сокет
            ByteSocket socket = new ByteSocket(udp, poll.address, poll.port, bufferSize);
            //Авторизация
            Response<byte[]> response = connectionProcessor.acceptConnection(socket, poll.userRequest);
            if (response == null) response = Response.refuse(new byte[0]);
            else if (response.getResponseData() == null){
                response.setResponseData(new byte[0]);
            }

            //Отвечаем. Если accept, инициализируем и заносим сокет в список.
            byte[] fullResponseData = buildFullResponsePacket(response);
            if (response.accepted()){
                socket.init(this, fullResponseData, inactivityTimeout, pingFrequency, resendFrequency);
                socketMap.put(poll.address, poll.port, socket);
            }
            sendPacket.setAddress(poll.address);
            sendPacket.setPort(poll.port);
            sendPacket.setData(fullResponseData);
            try {
                udp.send(sendPacket);
            } catch (IOException e) {
                e.printStackTrace();
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
                ByteSocket socket = wrap.socket;
                if (now - socket.lastTimeReceivedMsg > socket.inactivityTimeout) {
                    socket.queue.put(new ByteSocket.DisconnectionPacket(ByteSocket.DisconnectionPacket.TIMED_OUT, DCType.TIME_OUT));
                } else {
                    if (socket.isConnected()) {
                        socket.checkResendAndPing();
                    }
                }
            }
        }
    }

    /**
     * builds fullResponse for Response<> object
     */
    private byte[] buildFullResponsePacket(Response<byte[]> response) {
        byte[] resp = new byte[response.getResponseData().length + 1];
        resp[0] = response.accepted() ? PacketType.connectionResponseOk : PacketType.connectionResponseError;
        System.arraycopy(response.getResponseData(), 0, resp, 1, response.getResponseData().length);
        return resp;
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

    public Array<ByteSocket> getSockets(){
        return getSockets(new Array<ByteSocket>());
    }

    public Array<ByteSocket> getSockets(Array<ByteSocket> sockets){
        sockets.clear();
        synchronized (socketMap){
            for (SocketMap.SocketWrap socket : socketMap.sockets) {
                sockets.add(socket.socket);
            }
        }
        return sockets;
    }

    void removeMe(ByteSocket socket) {
        socketMap.remove(socket);
    }

    public void close(){
        Array<ByteSocket> sockets = getSockets();
        for (ByteSocket socket : sockets) {
            socket.close(DCType.SERVER_SHUTDOWN);
        }
        udp.close();
    }

    private class ConnectionRequest {
        InetAddress address;
        int port;
        byte[] userRequest;

        public ConnectionRequest(InetAddress address, int port, byte[] userRequest) {
            this.address = address;
            this.port = port;
            this.userRequest = userRequest;
        }
    }
}
