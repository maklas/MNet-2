package ru.maklas.mnet2.impl;

import com.esotericsoftware.kryo.util.ObjectMap;
import ru.maklas.mnet2.*;
import ru.maklas.mnet2.SocketProcessor;
import ru.maklas.mnet2.ByteSocket;
import ru.maklas.mnet2.Serializer;
import ru.maklas.mnet2.Socket;

import java.io.IOException;
import java.net.InetAddress;

public class SocketImpl implements Socket {

    private final ByteSocket sock;
    private final Serializer serializer;
    private Object userData;
    private final SocketProcessorAdapter adapter;
    private ObjectMap<DCListener, ByteDCListener> dcMap = new ObjectMap<DCListener, ByteDCListener>();
    private ObjectMap<PingListener, BytePingListener> pingMap = new ObjectMap<PingListener, BytePingListener>();


    public SocketImpl(InetAddress address, int port, Serializer serializer) throws Exception {
        this.serializer = serializer;
        this.sock = new ByteSocket(address, port);
        this.sock.setUserData(this);
        this.adapter = new SocketProcessorAdapter();
    }

    public SocketImpl(UDPSocket sock, InetAddress address, int port, int bufferSize, int inactivityTimeout, int pingFrequency, int resendFrequency, Serializer serializer) throws Exception {
        this.serializer = serializer;
        this.sock = new ByteSocket(sock, address, port, bufferSize, inactivityTimeout, pingFrequency, resendFrequency);
        this.sock.setUserData(this);
        this.adapter = new SocketProcessorAdapter();
    }

    SocketImpl(ByteSocket socket, Serializer serializer){
        this.serializer = serializer;
        this.sock = socket;
        this.sock.setUserData(this);
        this.adapter = new SocketProcessorAdapter();
    }

    @Override
    public ServerResponse connect(int timeout, Object request) throws IOException{
        byte[] serialize = serializeForConnection(request);
        ConnectionResponse connect = sock.connect(serialize, timeout);
        Object deserialize = null;
        try {
            deserialize = serializer.deserialize(connect.getData());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (deserialize == null){
            return new ServerResponse(connect.getType(), null);
        }
        return new ServerResponse(connect.getType(), deserialize);
    }

    @Override
    public void connectAsync(final int timeout, final Object request, final Handler<ServerResponse> handler){
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerResponse connect;
                try {
                    connect = connect(timeout, request);
                } catch (IOException e) {
                    connect = new ServerResponse(ResponseType.NO_RESPONSE, new byte[0]);
                }
                handler.handle(connect);
            }
        }).start();
    }

    @Override
    public void stop() {
        sock.stop();
    }

    @Override
    public int getBufferSize() {
        return sock.getBufferSize();
    }

    @Override
    public void send(Object o) {
        sock.send(serializer.serialize(o));
    }

    @Override
    public void sendBatch(NetBatch batch) {
        sock.sendBatch(batch.convertAndGet(serializer));
    }

    @Override
    public void sendSerialized(byte[] data){
        sock.send(data);
    }

    @Override
    public void sendUnreliable(Object o) {
        sock.sendUnreliable(serializer.serialize(o));
    }


    public void update(SocketProcessor processor) {
        adapter.objectProcessor = processor;
        sock.update(adapter);
        adapter.objectProcessor = null;
    }

    public ByteSocket getByteSocket(){
        return sock;
    }

    public Serializer getSerializer() {
        return serializer;
    }

    @Override
    public void addDCListener(final DCListener listener) {
        ByteDCListener bdl = new ByteDCListener() {
            @Override
            public void socketClosed(ByteSocket socket, String msg) {
                listener.socketClosed(SocketImpl.this, msg);
            }
        };
        dcMap.put(listener, bdl);
        sock.addDcListener(bdl);
    }

    @Override
    public void addPingListener(final PingListener listener) {
        BytePingListener l = new BytePingListener() {
            @Override
            public void onPingChange(ByteSocket socket, float ping) {
                listener.notify(SocketImpl.this, ping);
            }
        };
        pingMap.put(listener, l);
        sock.addPingListener(l);
    }

    @Override
    public void removeDCListener(DCListener listener) {
        sock.removeDcListener(dcMap.remove(listener));
    }

    @Override
    public void removePingListener(PingListener listener) {
        sock.removePingListener(pingMap.remove(listener));
    }

    @Override
    public void removeAllListeners() {
        dcMap.clear();
        pingMap.clear();
        sock.removeAllListeners();
    }

    @Override
    public void close() {
        sock.close();
    }

    @Override
    public void close(String msg) {
        sock.close(msg);
    }

    @Override
    public void setPingFrequency(int millis) {
        sock.setPingFrequency(millis);
    }

    @Override
    public Object setUserData(Object userData) {
        Object oldData = this.userData;
        this.userData = userData;
        return oldData;
    }

    @Override
    public boolean isConnected() {
        return sock.isConnected();
    }


    @Override
    public SocketState getState() {
        return sock.getState();
    }


    @Override
    public InetAddress getRemoteAddress() {
        return sock.getRemoteAddress();
    }


    @Override
    public int getRemotePort() {
        return sock.getRemotePort();
    }


    @Override
    public int getLocalPort() {
        return sock.getUdp().getLocalPort();
    }


    @Override
    public boolean isProcessing() {
        return sock.isProcessing();
    }


    @Override
    public float getPing() {
        return sock.getPing();
    }


    @Override
    public <T> T getUserData() {
        return (T) userData;
    }

    @Override
    public boolean isClosed() {
        return sock.isClosed();
    }

    private class SocketProcessorAdapter implements ByteSocketProcessor{

        SocketProcessor objectProcessor;

        @Override
        public void process(ByteSocket socket, byte[] data) {
            Object deserialize = null;
            try {
                deserialize = serializer.deserialize(data);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            objectProcessor.process((Socket) socket.getUserData(), deserialize);
        }
    }

    @Override
    public String toString() {
        return "ObjectSocket{" +
                "sock=" + sock +
                ", serializer=" + serializer.getClass().getSimpleName() +
                ", userData=" + userData +
                '}';
    }

    private byte[] serializeForConnection(Object o){
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

}
