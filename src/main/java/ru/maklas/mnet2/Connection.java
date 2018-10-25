package ru.maklas.mnet2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

public class Connection {

    private Object request;
    private Object response;
    SocketImpl socket;
    private final ServerSocket serverSocket;
    private boolean accepted;
    private boolean rejected;


    public Connection(ServerSocket serverSocket, SocketImpl socket, Object request) {
        this.serverSocket = serverSocket;
        this.socket = socket;
        this.request = request;
    }

    public Object getRequest() {
        return request;
    }

    public InetAddress getAddress(){
        return socket.getRemoteAddress();
    }

    public int getPort(){
        return socket.getRemotePort();
    }

    public boolean isMadeChoice(){
        return accepted || rejected;
    }

    public Socket accept(Object response){
        if (isMadeChoice()) throw new RuntimeException("You've already accepter or rejected connection");
        accepted = true;
        this.response = response;

        byte[] fullResponsePacket = buildFullResponsePacket(response, true);
        socket.init(serverSocket,
                fullResponsePacket,
                serverSocket.inactivityTimeout,
                serverSocket.pingFrequency,
                serverSocket.resendFrequency,
                serverSocket.serializerSupplier.get());
        serverSocket.socketMap.put(socket);

        send(fullResponsePacket);
        return socket;
    }

    public void reject(Object response){
        if (isMadeChoice()) throw new RuntimeException("You've already accepter or rejected connection");
        rejected = true;
        this.response = response;

        byte[] fullResponsePacket = buildFullResponsePacket(response, false);
        socket.init(serverSocket,
                fullResponsePacket,
                serverSocket.inactivityTimeout,
                serverSocket.pingFrequency,
                serverSocket.resendFrequency,
                serverSocket.serializerSupplier.get());
        serverSocket.socketMap.put(socket);

        send(fullResponsePacket);
    }
    /**
     * builds fullResponse for Response<> object
     */
    byte[] buildFullResponsePacket(Object response, boolean accepted) {
        byte[] userResponse;
        if (response == null) {
            userResponse = new byte[0];
        }
        else {
            try {
                userResponse = serverSocket.serializer.serialize(response);
            } catch (Exception e) {
                e.printStackTrace();
                userResponse = new byte[0];
            }
        }
        byte[] fullResp = new byte[userResponse.length + 5];
        fullResp[0] = accepted ? PacketType.connectionResponseOk : PacketType.connectionResponseError;
        System.arraycopy(userResponse, 0, fullResp, 5, userResponse.length);
        return fullResp;
    }

    private void send(byte[] data){
        DatagramPacket sendPacket = serverSocket.sendPacket;
        sendPacket.setAddress(socket.address);
        sendPacket.setPort(socket.port);
        sendPacket.setData(data);
        try {
            serverSocket.udp.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "{" +
                "request=" + request +
                ", accepted=" + accepted +
                ", rejected=" + rejected +
                ", socket=" + socket +
                '}';
    }
}
