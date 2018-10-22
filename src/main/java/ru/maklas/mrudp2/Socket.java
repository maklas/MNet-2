package ru.maklas.mrudp2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;

import static ru.maklas.mrudp2.PacketType.*;

public class Socket implements SocketIterator {

    /**
     * Size of receiving packet queue. If it overflows, data might be lost.
     */
    public static int receivingQueueSize = 5000;
    private static final byte[] zeroLengthByte = new byte[0];


    //Main useful stuff
    private final InetAddress address;
    private final int port;
    private final UDPSocket udp;
    private volatile SocketState state;
    private Thread receiveThread;
    private int dcTimeDueToInactivity;
    private boolean isClientSocket;
    private volatile int lastInsertedSeq = 0;
    volatile long lastTimeReceivedMsg = 0;
    private final AtomicInteger seq = new AtomicInteger();
    private Pool<SendPacket> sendPacketPool = new Pool<SendPacket>() {
        @Override
        protected SendPacket newObject() {
            return new SendPacket();
        }
    };
    private long resendCD = 100;
    //Отправленные надёжные пакеты, требущие подтверждения или пересылаются.
    private final SortedIntList<SendPacket> requestList = new SortedIntList<SendPacket>();
    //Очередь с принятными отсортированными сообщениями.
    //byte[] - Пакет, String - Сообщение дисконнекта, Float - пинг.
    private final AtomicQueue<Object> queue = new AtomicQueue<Object>(receivingQueueSize);
    //Очередь в которой полученные пакеты сортируются, если они были получены в неправильной последовательности
    //byte[] - пакет или пинг если длинна == 0, byte[][] - batch пакет
    private final SortedIntList<Object> receivingSortQueue = new SortedIntList<Object>();

    //Ping
    private float currentPing;
    private long lastPingSendTime;
    private long pingCD = 500;

    //Utils
    private DatagramPacket sendPacket;
    private DatagramPacket ackPacket;
    private DatagramPacket receivePacket;
    private DatagramPacket pingResponsePacket;
    private ServerSocket server;
    private DatagramPacket serverConnectionResponse; //Ответ сервера на connectionRequest.
    private byte[] sendBuffer;
    private byte[] receiveBuffer;
    private byte[] ackBuffer;
    private byte[] pingResponseBuffer;
    private boolean processing = false; //true if processing right now
    private boolean interrupted = false; //true if interrupted processing.
    private int bufferSize;
    private Array<PingListener> pingListeners = new Array<PingListener>();

    /**
     * DEFAULT CONSTRCUTOR FOR SOCKET
     */
    public Socket(InetAddress address, int port, int bufferSize, int dcTimeDueToInactivity) throws SocketException {
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
    Socket(UDPSocket udp, InetAddress address, int port, int bufferSize, int dcTimeDueToInactivity) {
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
    void init(ServerSocket serverSocket, byte[] fullResponseData) {
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
        } else if (timeout < 5000){
            wait = 400;
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
        log("Ping on connect: " + currentPing);
        launchReceiveThread();
        return new ConnectionResponse(ResponseType.ACCEPTED, responseData);
    }

    private void launchReceiveThread() {
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] receiveBuffer = Socket.this.receiveBuffer;
                DatagramPacket packet = Socket.this.receivePacket;
                UDPSocket udp = Socket.this.udp;
                boolean keepRunning = true;
                while (keepRunning) {
                    try {
                        udp.receive(packet);
                    } catch (IOException e) {
                        if (udp.isClosed()) {
                            keepRunning = false;
                        } else {
                            System.out.println("TIMEOUT of client socket");
                            //TODO Timeout.
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
                System.arraycopy(fullPacket, 1, bytes2, 0, length - 5);
                queue.put(bytes2);
                break;
            /*case batch:
                sendAck(seq);
                int expectedSeq2 = lastInsertedSeq + 1;
                if (seq < expectedSeq2){
                    break;
                }

                try {
                    byte[][] batchPackets = PacketType.breakBatchDown(fullPacket);
                    if (seq == expectedSeq2){
                        lastInsertedSeq = seq;
                        for (byte[] batchPacket : batchPackets) {
                            queue.put(batchPacket);
                        }
                        updateReceiveOrderQueue();
                    } else if (seq > expectedSeq2){
                        addToWaitings(seq, batchPackets);
                    }
                } catch (Exception ignore){}
                break;*/
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
            case connectionAcknowledgment:
                //Ничего.
                break;
            case disconnect:
                //Достаём месседж, дисконнектим
                break;
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
            SendPacket removed = requestList.remove(seq);
            if (removed != null){
                sendPacketPool.free(removed);
                log("request removed (" + seq + "). Size: " + requestList.size);
                return true;
            }
        }
        return false;
    }

    /**
     * Проверить объекты которые ждут свою очередь
     */
    private void updateReceiveOrderQueue() {
        int expectedSeq;

        synchronized (receivingSortQueue) {
            expectedSeq = lastInsertedSeq + 1;
            Object mayBeData = receivingSortQueue.remove(expectedSeq);

            while (mayBeData != null) {
                this.lastInsertedSeq = expectedSeq;
                if (mayBeData instanceof byte[]){
                    if (((byte[]) mayBeData).length > 0) { // Если в очереди не пинг
                        queue.put(mayBeData);
                    }
                } else {
                    byte[][] batch = (byte[][]) mayBeData;
                    for (byte[] bytes : batch) {
                        queue.put(bytes);
                    }
                }
                expectedSeq = lastInsertedSeq + 1;
                mayBeData = receivingSortQueue.remove(expectedSeq);
            }
        }
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
                } else if (poll instanceof String){
                    if (!isClientSocket){
                        //receivedDCByServerOrTimeOut((String) poll);
                    } else {
                        //receivedDCByClientOrTimeOut((String) poll);
                    }
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
        return udp.isClosed();
    }

    public void close(){
        udp.close();
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

    void checkResendAndPing() {
        if (state == SocketState.CONNECTED){
            final long currTime = System.currentTimeMillis();
            if (currTime - lastPingSendTime > pingCD){
                sendPing();
                lastPingSendTime = currTime;
            }
            long resendCD = this.resendCD;
            synchronized (requestList) {
                for (SortedIntList.Node<SendPacket> pack : requestList) {
                    SendPacket packet = pack.value;
                    if (currTime - packet.sendTime > resendCD){
                        sendData(packet.data);
                        log("Resending data " + "last attempt was " + ((int) packet.sendTime) + " now is " + ((int) currTime), packet.data);
                        packet.sendTime = currTime;
                    }
                }
            }
        }
    }

    private void saveRequest(int seq, byte[] fullPackage) {
        synchronized (requestList) {
            log("request saved (" + seq + ")");
            requestList.insert(seq, sendPacketPool.obtain().set(fullPackage));
        }
    }

    private void sendData(byte[] fullPackage) {
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

    private void log(String msg, byte[] data){
        System.out.println((isClientSocket ? "Client:: " : "Server:: ") + msg + ". Data: " + toString(data));
    }

    private static String toString(byte[] a) {
        if (a == null)
            return "null";
        int iMax = a.length - 1;
        if (iMax == -1)
            return "[]";

        StringBuilder b = new StringBuilder();
        b.append('[');
        b.append(PacketType.toString(a[0])).append(", ");
        for (int i = 1; ; i++) {
            b.append(a[i]);
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

    private void log(String msg){
        System.out.println((isClientSocket ? "Client:: " : "Server:: ") + msg);
    }

    private class SendPacket {
        public long sendTime;
        public byte[] data;

        public SendPacket set(byte[] data){
            sendTime = System.currentTimeMillis();
            this.data = data;
            return this;
        }

    }
}
