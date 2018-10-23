package ru.maklas.mnet2.impl;

import ru.maklas.mnet.*;
import ru.maklas.mrudp.*;

import java.net.InetAddress;
import java.util.concurrent.*;

public class ServerSocketImpl implements ServerSocket{

    private final String name;
    private final MRUDPServerSocket sock;
    private final ModelAdapter modelAdapter;
    private final Serializer serializer;
    private final int pingUpdateTime;
    private final Provider<Serializer> provider;
    private int counter = 0;
    private MNetQueue<Runnable> connectionQueue = new MNetQueue<Runnable>(1000);

    /**
     * Simple constructor
     * @param port Port on which to open server
     * @param bufferSize bufferSize for each Sub-Socket
     * @param model ServerModel for connection establishment
     * @param provider Provides serialzier for ServerSocket and each Sub-Socket
     * @throws Exception In case UDP socket can't be opened on desired port
     */
    public ServerSocketImpl(int port, int bufferSize, ServerAuthenticator model, Provider<Serializer> provider) throws Exception{
        this("ServerSubSocket", new JavaUDPSocket(port), bufferSize, 12000, 5000, model, provider);
    }

    /**
     * Complex constructor
     * @param name Name for every Sub-Socket that's created by server
     * @param sock UDPsocket on which to open server
     * @param bufferSize bufferSize for each Sub-Socket
     * @param dcTimeDueToInactivity dc on inactivity time for each Sub-Socket
     * @param pingUpdateTime Ping update time for every Sub-Socket
     * @param model ServerModel for connection establishment
     * @param provider Provides serialzier for ServerSocket and each Sub-Socket
     * @throws Exception In case UDP socket can't be opened on desired port
     */
    public ServerSocketImpl(String name, UDPSocket sock, int bufferSize, int dcTimeDueToInactivity, int pingUpdateTime, ServerAuthenticator model, Provider<Serializer> provider) {
        this.name = name;
        this.pingUpdateTime = pingUpdateTime;
        this.provider = provider;
        this.serializer = provider.provide();
        this.modelAdapter = new ModelAdapter(model);
        this.sock = new MRUDPServerSocket(sock, bufferSize, modelAdapter,  dcTimeDueToInactivity);
    }

    @Override
    public void start(){
        sock.start();
    }

    @Override
    public void close() {
        sock.close();
    }

    @Override
    public void processNewConnections() {
        Runnable poll = connectionQueue.poll();
        while (poll != null){
            poll.run();
            poll = connectionQueue.poll();
        }
    }

    byte[] serialize(Object o){
        Serializer serializer = this.serializer;

        if (serializer.useOffsetSerialization()) {
            byte[] bytes = serializer.serializeOff5(o);
            byte[] ret = new byte[bytes.length - 5];
            System.arraycopy(bytes, 5, ret, 0, ret.length);
            return ret;
        } else {
            return serializer.serialize(o);
        }
    }


    private class ModelAdapter implements ServerModel{

        private final ServerAuthenticator model;

        public ModelAdapter(ServerAuthenticator model) {
            this.model = model;
        }

        @Override
        public ConnectionResponsePackage<byte[]> validateNewConnection(final InetAddress address, final int port, byte[] userData) {
            final Object req;
            try {
                req = serializer.deserialize(userData);
            } catch (Exception e) {
                e.printStackTrace();
                return ConnectionResponsePackage.refuse(new byte[0]);
            }

            FutureTask<ConnectionResponsePackage<byte[]>> connectionResponsePackageFutureTask = new FutureTask<ConnectionResponsePackage<byte[]>>(new Callable<ConnectionResponsePackage<byte[]>>() {
                @Override
                public ConnectionResponsePackage<byte[]> call() throws Exception {
                    ConnectionResponsePackage<?> response = model.validateNewConnection(address, port, req);
                    byte[] serializedResponseData = serialize(response.getResponseData());
                    if (response.accepted()) {
                        return ConnectionResponsePackage.accept(serializedResponseData);
                    } else {
                        return ConnectionResponsePackage.refuse(serializedResponseData);
                    }
                }
            });
            connectionQueue.put(connectionResponsePackageFutureTask);
            try {
                return connectionResponsePackageFutureTask.get(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return ConnectionResponsePackage.refuse(new byte[0]);
            } catch (ExecutionException e) {
                return ConnectionResponsePackage.refuse(new byte[0]);
            } catch (TimeoutException e) {
                return ConnectionResponsePackage.refuse(new byte[0]);
            }
        }

        @Override
        public void registerNewConnection(MRUDPSocketImpl mrudpSocket, ConnectionResponsePackage<byte[]> data, byte[] userData) {
            mrudpSocket.setUserData(this);
            final Socket socket = new SocketImpl(name + "-" + (counter++), mrudpSocket, provider.provide());
            socket.setPingUpdateTime(pingUpdateTime);
            Serializer serializer = ServerSocketImpl.this.serializer;
            Object deserializedBack = serializer.deserialize(data.getResponseData());
            final Object deserializedUserData = serializer.deserialize(userData);

            final ConnectionResponsePackage<Object> sentResponsePackage;
            if (data.accepted()){
                sentResponsePackage = ConnectionResponsePackage.accept(deserializedBack);
            } else {
                sentResponsePackage = ConnectionResponsePackage.refuse(deserializedBack);
            }
            connectionQueue.put(new Runnable() {
                @Override
                public void run() {
                    model.registerNewConnection(socket, sentResponsePackage, deserializedUserData);
                }
            });
        }

        @Override
        public void onSocketDisconnected(MRUDPSocketImpl socket, final String msg) {
            final Socket userData = (Socket) socket.getUserData();
            connectionQueue.put(new Runnable() {
                @Override
                public void run() {
                    model.onSocketDisconnected(userData, msg);
                }
            });
        }
    }
}
