package ru.maklas.mrudp2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;

import static ru.maklas.mrudp2.PacketType.*;

public class ByteSocket implements SocketIterator {

    /**
     * Size of receiving packet queue. If it overflows, data might be lost.
     */
    public static int receivingQueueSize = 5000;
    private static final byte[] zeroLengthByte = new byte[0];


    //Main useful stuff
    private final InetAddress address;
    private final int port;
    private final UDPSocket udp;
    volatile SocketState state;
    private int bufferSize;
    private Thread receiveThread;
    private int dcTimeDueToInactivity;
    private boolean isClientSocket;
    private volatile int lastInsertedSeq = -1;
    volatile long lastTimeReceivedMsg = 0;
    private final AtomicInteger seq = new AtomicInteger();
    private Pool<ResendPacket> sendPacketPool = new Pool<ResendPacket>() {
        @Override
        protected ResendPacket newObject() {
            return new ResendPacket();
        }
    };
    private long resendCD = 20;
    private volatile DisconnectionPacket dcPacket;
    //Отправленные надёжные пакеты, требущие подтверждения или пересылаются.
    private final SortedIntList<ResendPacket> requestList = new SortedIntList<ResendPacket>();
    //Очередь с принятными отсортированными сообщениями.
    //byte[] - Пакет, String - Сообщение дисконнекта, Float - пинг.
    final AtomicQueue<Object> queue = new AtomicQueue<Object>(receivingQueueSize);
    //Очередь в которой полученные пакеты сортируются, если они были получены в неправильной последовательности
    //byte[] - пакет или пинг если длинна == 0, byte[][] - batch пакет
    private final SortedIntList<Object> receivingSortQueue = new SortedIntList<Object>();

    //Ping
    private float currentPing;
    private long lastPingSendTime;
    private long pingCD = 2500;

    //datagram packets
    private DatagramPacket sendPacket;
    private DatagramPacket ackPacket;
    private DatagramPacket receivePacket;
    private DatagramPacket pingResponsePacket;
    private DatagramPacket serverConnectionResponse; //Ответ сервера на connectionRequest.
    private byte[] sendBuffer;
    private byte[] receiveBuffer;
    private byte[] ackBuffer;
    private byte[] pingResponseBuffer;

    //processing
    private boolean processing = false; //true if processing right now
    private boolean interrupted = false; //true if interrupted processing.


    //Utils
    private ByteServerSocket server;
    private Array<PingListener> pingListeners = new Array<PingListener>();
    private Array<DCListener> dcListeners = new Array<DCListener>();
    private Object userData = null;

    /**
     * DEFAULT CONSTRCUTOR FOR SOCKET
     */
    public ByteSocket(InetAddress address, int port, int bufferSize, int dcTimeDueToInactivity) throws SocketException {
        this.address = address;
        this.port = port;
        this.bufferSize = bufferSize;
        this.isClientSocket = true;
        this.sendBuffer = new byte[bufferSize];
        this.receiveBuffer = new byte[bufferSize];
        this.ackBuffer = new byte[bufferSize];
        this.pingResponseBuffer = new byte[13];
        this.pingResponseBuffer[0] = pingResponse;
        this.sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length);
        this.sendPacket.setAddress(address);
        this.sendPacket.setPort(port);
        this.receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        this.ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        this.ackPacket.setAddress(address);
        this.ackPacket.setPort(port);
        this.pingResponsePacket = new DatagramPacket(pingResponseBuffer, pingResponseBuffer.length);
        this.udp = new JavaUDPSocket();
        this.state = SocketState.NOT_CONNECTED;
        this.dcTimeDueToInactivity = dcTimeDueToInactivity;
        this.serverConnectionResponse = null;
        this.pingResponsePacket.setAddress(address);
        this.pingResponsePacket.setPort(port);
    }

    /**
     * ONLY USED BY SERVERS_SOCKET to construct new SUBSOCKET
     */
    ByteSocket(UDPSocket udp, InetAddress address, int port, int bufferSize, int dcTimeDueToInactivity) {
        this.address = address;
        this.port = port;
        this.bufferSize = bufferSize;
        this.udp = udp;
        this.isClientSocket = false;
        this.state = SocketState.NOT_CONNECTED;
        this.dcTimeDueToInactivity = dcTimeDueToInactivity;
    }

    /**
     * Initialization done by server after Socket was accepted
     */
    void init(ByteServerSocket serverSocket, byte[] fullResponseData) {
        this.server = serverSocket;
        this.serverConnectionResponse = new DatagramPacket(fullResponseData, fullResponseData.length);
        serverConnectionResponse.setAddress(address);
        serverConnectionResponse.setPort(port);
        this.lastTimeReceivedMsg = System.currentTimeMillis();
        this.state = SocketState.CONNECTED;
        this.sendBuffer = new byte[bufferSize];
        this.receiveBuffer = new byte[bufferSize];
        this.ackBuffer = new byte[bufferSize];
        this.sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length);
        this.sendPacket.setAddress(address);
        this.sendPacket.setPort(port);
        this.receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        this.ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        this.ackPacket.setAddress(address);
        this.ackPacket.setPort(port);
        this.pingResponseBuffer = new byte[13];
        this.pingResponseBuffer[0] = pingResponse;
        this.pingResponsePacket = new DatagramPacket(pingResponseBuffer, pingResponseBuffer.length);
        this.pingResponsePacket.setAddress(address);
        this.pingResponsePacket.setPort(port);
        this.lastPingSendTime = System.currentTimeMillis();
    }

    public ConnectionResponse connect(byte[] data, int timeout) throws IOException {
        if (!isClientSocket || state != SocketState.NOT_CONNECTED)
            return new ConnectionResponse(ResponseType.WRONG_STATE);
        state = SocketState.CONNECTING;

        udp.setReceiveTimeout(timeout);
        udp.connect(address, port);
        sendPacket.setAddress(address);
        sendPacket.setPort(port);
        int attempts;
        int wait;
        if (timeout < 600){
            wait = 200;
            attempts = 3;
        } else if (timeout < 2000){
            wait = 333;
            attempts = timeout / wait;
        } else {
            wait = 666;
            attempts = timeout / wait;
        }

        //Make request
        sendBuffer[0] = PacketType.connectionRequest;
        System.arraycopy(data, 0, sendBuffer, 1, data.length);
        long beforeFirstRequest = System.currentTimeMillis();

        //send until all attempts are over or we get response.
        udp.setReceiveTimeout(wait);
        while (attempts > 0) {
            try {
                udp.send(sendPacket);
                udp.receive(receivePacket);
                break;
            } catch (IOException e) {
                if (udp.isClosed()) {
                    state = SocketState.CLOSED;
                    return new ConnectionResponse(ResponseType.WRONG_STATE);
                } else {
                    if (attempts-- == 0) {
                        state = SocketState.NOT_CONNECTED;
                        return new ConnectionResponse(ResponseType.NO_RESPONSE);
                    }
                }
            }
        }

        byte packetType = receiveBuffer[0];

        if (packetType == PacketType.connectionResponseError){
            state = SocketState.NOT_CONNECTED;
            return new ConnectionResponse(ResponseType.REJECTED);
        }
        if (packetType != PacketType.connectionResponseOk){
            state = SocketState.NOT_CONNECTED;
            return new ConnectionResponse(ResponseType.NO_RESPONSE);
        }

        byte[] responseData = new byte[receivePacket.getLength() - 1];
        System.arraycopy(receiveBuffer, 1, responseData, 0, responseData.length);
        udp.setReceiveTimeout(dcTimeDueToInactivity);
        state = SocketState.CONNECTED;
        this.lastTimeReceivedMsg = System.currentTimeMillis();
        lastPingSendTime = lastTimeReceivedMsg;
        currentPing = (lastTimeReceivedMsg - beforeFirstRequest);
        launchReceiveThread();
        return new ConnectionResponse(ResponseType.ACCEPTED, responseData);
    }

    private void launchReceiveThread() {
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] receiveBuffer = ByteSocket.this.receiveBuffer;
                DatagramPacket packet = ByteSocket.this.receivePacket;
                UDPSocket udp = ByteSocket.this.udp;
                boolean keepRunning = true;
                while (keepRunning) {
                    try {
                        udp.receive(packet);
                    } catch (IOException e) {
                        keepRunning = false;
                        if (!udp.isClosed()) {
                            queue.put(new DisconnectionPacket(DisconnectionPacket.TIMED_OUT, DCType.TIME_OUT));
                        }
                    }

                    int length = packet.getLength();
                    if (length > 5) {
                        byte type = receiveBuffer[0];
                        receiveData(receiveBuffer, type, length);
                    }

                }
            }
        });
        receiveThread.start();
    }

    /**
     * @param fullPacket Весь пакет. Длинна == bufferSize.
     * @param type Первый бит
     * @param length Длина информативной части пакета. > 5
     */
    void receiveData(byte[] fullPacket, byte type, int length){
        lastTimeReceivedMsg = System.currentTimeMillis();
        final int seq = PacketType.extractInt(fullPacket, 1);
        switch (type){
            case reliableRequest:
                sendAck(seq);
                byte[] bytes1 = new byte[length - 5];
                System.arraycopy(fullPacket, 5, bytes1, 0, length - 5);
                int expectedSeq1 = lastInsertedSeq + 1;
                if (seq == expectedSeq1){
                    lastInsertedSeq = seq;
                    queue.put(bytes1);
                    updateReceiveOrderQueue();
                } else if (seq > expectedSeq1){
                    addToWaitings(seq, bytes1);
                }
                break;
            case reliableAck:
                removeFromWaitingForAck(seq);
                break;
            case unreliable:
                byte[] bytes2 = new byte[length - 5];
                System.arraycopy(fullPacket, 1, bytes2, 0, length - 1);
                queue.put(bytes2);
                break;
            case batch:
                sendAck(seq);
                int expectedSeq2 = lastInsertedSeq + 1;
                if (seq < expectedSeq2){
                    break;
                }

                try {
                    byte[][] batchPackets;
                    if (seq == expectedSeq2){
                        lastInsertedSeq = seq;
                        batchPackets = PacketType.breakBatchDown(fullPacket);
                        for (byte[] batchPacket : batchPackets) {
                            queue.put(batchPacket);
                        }
                        updateReceiveOrderQueue();
                    } else if (seq > expectedSeq2){
                        batchPackets = PacketType.breakBatchDown(fullPacket);
                        addToWaitings(seq, batchPackets);
                    }
                } catch (Exception ignore){}
                break;
            case pingRequest:
                final long startTime = extractLong(fullPacket, 5);
                sendPingResponse(seq, startTime);

                int expectSeq3 = this.lastInsertedSeq + 1;

                if (expectSeq3 == seq){
                    lastInsertedSeq = seq;
                    updateReceiveOrderQueue();
                } else if (expectSeq3 < seq){
                    addToWaitings(seq, zeroLengthByte);
                }
                break;
            case pingResponse:
                final long startingTime = extractLong(fullPacket, 5);
                boolean removed = removeFromWaitingForAck(seq);
                if (removed){
                    float ping = ((float) (System.nanoTime() - startingTime))/1000000f;
                    this.currentPing = ping;
                    queue.put(ping);
                }
                break;
            case connectionRequest:
                if (!isClientSocket){
                    try {
                        udp.send(serverConnectionResponse);
                    } catch (IOException ignore) {}
                }
                break;
            case connectionResponseOk:
            case connectionResponseError:
                //Ничего.
                break;
            case disconnect:
                String msg = new String(fullPacket, 5, length - 5);
                queue.put(new DisconnectionPacket(DisconnectionPacket.EVENT_RECEIVED, msg));
                break;
            default:
                System.err.println("Unknown message type: " + type);
        }
    }

    /**
     * Отправляем ответ на пинг запрос. Тупо пакет с type, seq, startTime
     */
    private void sendPingResponse(int seq, long startTime) {
        PacketType.putInt(pingResponseBuffer, seq, 1);
        PacketType.putLong(pingResponseBuffer, startTime, 5);
        try {
            udp.send(pingResponsePacket);
        } catch (IOException e) {}
    }


    /**
     * Добавить в очередь на зачисление в queue.
     * @param seq номер
     * @param userData byte[] - пакет, byte[][] - Батч пакет, byte[0] - пинг.
     */
    private void addToWaitings(int seq, Object userData) {
        synchronized (receivingSortQueue) {
            receivingSortQueue.insert(seq, userData);
        }
    }

    private boolean removeFromWaitingForAck(int seq) {
        synchronized (requestList){
            ResendPacket removed = requestList.remove(seq);
            if (removed != null){
                sendPacketPool.free(removed);
                return true;
            }
        }
        return false;
    }

    //***********//
    //* ACTIONS *//
    //***********//

    public void sendUnreliable(byte[] data){
        sendBuffer[0] = unreliable;
        System.arraycopy(data, 0, sendBuffer, 1, data.length);
        sendPacket.setLength(data.length + 5);
        try {
            udp.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(byte[] data){
        int seq = this.seq.getAndIncrement();
        byte[] fullPackage = build5byte(reliableRequest, seq, data);
        saveRequest(seq, fullPackage);
        sendData(fullPackage);
    }

    public void sendBatch(Batch batch){
        int size = batch.size();
        switch (size){
            case 0: return;
            case 1:
                send(batch.get(0));
                return;
        }

        int bufferSize = this.bufferSize;

        int i = 0;
        while (i < size){
            int seq = this.seq.getAndIncrement();
            Object[] tuple = buildSafeBatch(seq, PacketType.batch, batch, i, bufferSize);
            byte[] fullPackage = (byte[]) tuple[0];
            i = ((Integer) tuple[1]);
            saveRequest(seq, fullPackage);
            sendData(fullPackage);
        }
    }

    public void update(SocketProcessor processor) {
        processData(processor);
        checkResendAndPing();
    }


    /**
     * @return True if got interrupted in the middle of the process
     */
    private boolean processData(SocketProcessor processor){
        if (processing){
            throw new ConcurrentModificationException("Can't be processed by 2 threads at the same time");
        }
        if (!isConnected()) return false;

        processing = true;
        interrupted = false;
        try {

            Object poll = queue.poll();
            while (poll != null){
                if (poll instanceof byte[]){
                    processor.process(this, this, (byte[]) poll);
                } else if (poll instanceof Float) {
                    for (PingListener pingListener : pingListeners) {
                        pingListener.onPingChange(this, (Float) poll);
                    }
                } else if (poll instanceof DisconnectionPacket){
                    state = SocketState.CLOSED;
                    interrupted = true;

                    DisconnectionPacket dcPacket = (DisconnectionPacket) poll;
                    this.dcPacket = dcPacket;

                    if (dcPacket.type == DisconnectionPacket.TIMED_OUT){
                        byte[] bytes = build5byte(disconnect, 0, dcPacket.message.getBytes());
                        sendData(bytes);
                    }

                    if (isClientSocket){
                        if (!udp.isClosed()) udp.close();
                    } else {
                        server.removeMe(this);
                    }

                    notifyDcListenersAndRemoveAll(dcPacket.message);
                    break;
                }
                if (interrupted){
                    break;
                }
                poll = queue.poll();
            }

        } catch (Throwable t){
            t.printStackTrace();
        }
        processing = false;
        return interrupted;
    }

    //***********//
    //* GET-SET *//
    //***********//

    public boolean isClosed() {
        return state == SocketState.CLOSED;
    }

    public void close(){
        close(DCType.CLOSED);
    }

    public void close(String msg){
        if (state != SocketState.CLOSED) {
            if (isClientSocket) {
                if (!udp.isClosed()) {
                    this.dcPacket = new DisconnectionPacket(DisconnectionPacket.SELF_CLOSED, msg);
                    sendData(build5byte(disconnect, 0, msg.getBytes()));
                    udp.close();
                    notifyDcListenersAndRemoveAll(msg);
                }
            } else {
                server.removeMe(this);
                notifyDcListenersAndRemoveAll(msg);
            }
            state = SocketState.CLOSED;
        }
    }

    public UDPSocket getUdp() {
        return udp;
    }

    public SocketState getState() {
        return state;
    }

    @Override
    public void stop() {
        interrupted = true;
    }

    @Override
    public boolean isProcessing() {
        return processing;
    }

    public void addPingListener(PingListener pl){
        pingListeners.add(pl);
    }

    public void removePingListener(PingListener pl){
        pingListeners.removeValue(pl, true);
    }

    public void addDcListener(DCListener dl){
        dcListeners.add(dl);
    }

    public void removeDcListener(DCListener dl){
        dcListeners.removeValue(dl, true);
    }

    public boolean isConnected(){
        return state == SocketState.CONNECTED;
    }

    public float getPing() {
        return currentPing;
    }

    public <T> T getUserData() {
        return (T) userData;
    }

    public <T> T setUserData(Object userData) {
        T oldData = (T) this.userData;
        this.userData = userData;
        return oldData;
    }

    //*********//
    //* UTILS *//
    //*********//


    void sendAck(int seq){
        ackBuffer[0] = reliableAck;
        PacketType.putInt(ackBuffer, seq, 1);
        try {
            udp.send(ackPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void notifyDcListenersAndRemoveAll(String msg){
        for (DCListener dcListener : dcListeners) {
            dcListener.socketClosed(this, msg);
        }
        dcListeners.clear();
        pingListeners.clear();
    }

    void checkResendAndPing() {
        if (state == SocketState.CONNECTED){
            final long currTime = System.currentTimeMillis();
            if (currTime - lastPingSendTime > pingCD){
                sendPing();
                lastPingSendTime = currTime;
            }
            long resendCD = this.resendCD;
            synchronized (requestList) {
                for (SortedIntList.Node<ResendPacket> pack : requestList) {
                    ResendPacket packet = pack.value;
                    if (currTime - packet.sendTime > resendCD){
                        sendData(packet.data);
                        packet.sendTime = currTime;
                    }
                }
            }
        }
    }

    private void saveRequest(int seq, byte[] fullPackage) {
        synchronized (requestList) {
            requestList.insert(seq, sendPacketPool.obtain().set(fullPackage));
        }
    }

    /**
     * Проверить объекты которые ждут свою очередь
     */
    private void updateReceiveOrderQueue() {
        int expectedSeq;

        SortedIntList<Object> queue = this.receivingSortQueue;
        synchronized (queue) {
            expectedSeq = lastInsertedSeq + 1;
            Object mayBeData = queue.remove(expectedSeq);

            while (mayBeData != null) {
                this.lastInsertedSeq = expectedSeq;
                if (mayBeData instanceof byte[]){
                    if (((byte[]) mayBeData).length > 0) { // Если в очереди не пинг
                        this.queue.put(mayBeData);
                    }
                } else {
                    byte[][] batch = (byte[][]) mayBeData;
                    for (byte[] bytes : batch) {
                        this.queue.put(bytes);
                    }
                }
                expectedSeq = lastInsertedSeq + 1;
                mayBeData = queue.remove(expectedSeq);
            }
        }
    }

    void sendData(byte[] fullPackage) {
        System.arraycopy(fullPackage, 0, sendBuffer, 0, fullPackage.length);
        try {
            sendPacket.setLength(fullPackage.length);
            udp.send(sendPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPing() {
        int seq = this.seq.getAndIncrement();
        byte[] fullPackage = new byte[13];
        fullPackage[0] = pingRequest;
        PacketType.putInt(fullPackage, seq, 1);
        PacketType.putLong(fullPackage, System.nanoTime(), 5);
        saveRequest(seq, fullPackage);
        sendData(fullPackage);
    }

    private class ResendPacket {
        public long sendTime;
        public byte[] data;

        public ResendPacket set(byte[] data){
            sendTime = System.currentTimeMillis();
            this.data = data;
            return this;
        }
    }

    static class DisconnectionPacket {

        public static final int SELF_CLOSED = 1;
        public static final int EVENT_RECEIVED = 2;
        public static final int TIMED_OUT = 3;

        public int type;
        public String message;

        public DisconnectionPacket(int type, String message) {
            this.type = type;
            this.message = message;
        }
    }
}
