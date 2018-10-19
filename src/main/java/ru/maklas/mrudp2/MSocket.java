package ru.maklas.mrudp2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;

import static ru.maklas.mrudp2.PacketType.*;

public class MSocket {

    /**
     * Size of receiving packet queue. If it overflows, data might be lost.
     */
    public static int receivingQueueSize = 5000;
    public static int defaultBufferSize = 512;
    public static int defaultDcToInactivityMS = 15000;


    private final InetAddress address;
    private final int port;
    private final UDPSocket udp;
    private final DatagramPacket sendPacket;
    private final DatagramPacket ackPacket;
    private final DatagramPacket receivePacket;
    private final DatagramPacket pingResponsePacket;
    private final byte[] sendBuffer;
    private final byte[] receiveBuffer;
    private final byte[] ackBuffer;
    private final byte[] pingResponseBuffer;
    private volatile SocketState state;
    private boolean isClientSocket;
    private int dcTimeDueToInactivity;
    private Thread receiveThread;
    //byte[] - Данные, String - Сообщение дисконнекта, Float - пинг.
    private final MAtomicQueue<Object> queue = new MAtomicQueue<Object>(receivingQueueSize);
    private volatile int lastInsertedSeq = 0;
    private static final byte[] zeroLengthByte = new byte[0];
    private float currentPing = 0;

    private byte[] serverConnectionResponse;

    public MSocket(InetAddress address, int port) throws SocketException {
        this.address = address;
        this.port = port;
        this.isClientSocket = true;
        this.sendBuffer = new byte[defaultBufferSize];
        this.receiveBuffer = new byte[defaultBufferSize];
        this.ackBuffer = new byte[defaultBufferSize];
        this.pingResponseBuffer = new byte[13];
        this.pingResponseBuffer[0] = pingResponse;
        this.sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length);
        this.receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        this.ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        this.pingResponsePacket = new DatagramPacket(pingResponseBuffer, pingResponseBuffer.length);
        this.udp = new JavaUDPSocket();
        this.state = SocketState.NOT_CONNECTED;
        this.dcTimeDueToInactivity = defaultDcToInactivityMS;
    }

    public ConnResponse connect(byte[] data, int timeout) throws IOException {
        if (!isClientSocket || state != SocketState.NOT_CONNECTED)
            return new ConnResponse(ResponseType.WRONG_STATE);
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
                    return new ConnResponse(ResponseType.WRONG_STATE);
                } else {
                    if (attempts-- == 0) {
                        state = SocketState.NOT_CONNECTED;
                        return new ConnResponse(ResponseType.NO_RESPONSE);
                    }
                }
            }
        }

        byte packetType = receiveBuffer[0];

        if (packetType == PacketType.connectionResponseError){
            state = SocketState.NOT_CONNECTED;
            return new ConnResponse(ResponseType.REJECTED);
        }
        if (packetType != PacketType.connectionResponseOk){
            state = SocketState.NOT_CONNECTED;
            return new ConnResponse(ResponseType.NO_RESPONSE);
        }

        byte[] responseData = new byte[receivePacket.getLength() - 1];
        System.arraycopy(receiveBuffer, 1, responseData, 0, responseData.length);
        udp.setReceiveTimeout(dcTimeDueToInactivity);
        state = SocketState.CONNECTED;
        launchReceiveThread();
        return new ConnResponse(ResponseType.ACCEPTED, responseData);
    }

    private void launchReceiveThread() {
        receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] receiveBuffer = MSocket.this.receiveBuffer;
                DatagramPacket packet = MSocket.this.receivePacket;
                UDPSocket udp = MSocket.this.udp;
                boolean keepRunning = true;
                while (keepRunning) {
                    try {
                        udp.receive(packet);
                    } catch (IOException e) {
                        if (udp.isClosed()) {
                            keepRunning = false;
                        } else {
                            //TODO Timeout.
                        }
                    }

                    int length = packet.getLength();
                    if (length > 5) {
                        byte type = receiveBuffer[0];
                        receive(receiveBuffer, type, length);
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
    private void receive(byte[] fullPacket, byte type, int length){
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
                    checkForWaitingDatas();
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
            case batch:
                sendAck(seq);
                int expectedSeq2 = lastInsertedSeq + 1;

                try {
                    byte[][] batchPackets = PacketType.breakBatchDown(fullPacket);

                    if (expectedSeq2 == seq){
                        lastInsertedSeq = seq;

                        for (byte[] batchPacket : batchPackets) {
                            queue.put(batchPacket);
                        }
                        checkForWaitingDatas();
                    } else if (seq > expectedSeq2){
                        addToWaitings(seq, batchPackets);
                    }
                } catch (Exception ignore){}
                //Разобрать, принять, ответить.
                break;
            case pingRequest:
                final long startTime = extractLong(fullPacket, 5);
                sendPingResponse(seq, startTime);

                int expectSeq3 = this.lastInsertedSeq + 1;

                if (expectSeq3 == seq){
                    lastInsertedSeq = seq;
                    checkForWaitingDatas();
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
                    //TODO Ответить serverConnectionResponse.
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

    private void sendPingResponse(int seq, long startTime) {
        PacketType.putInt(pingResponseBuffer, seq, 1);
        PacketType.putLong(pingResponseBuffer, startTime, 5);
        try {
            udp.send(pingResponsePacket);
        } catch (IOException e) {}
    }


    /**
     * Сортированные данные которые могут быть как byte[] - чистый пакет или byte[][] - batch пакет
     */
    private final SortedIntList<Object> waitings = new SortedIntList<Object>();

    /**
     * Добавить в очередь на зачисление в queue.
     * @param seq номер
     * @param userData byte[] - пакет, byte[][] - Батч, byte[0] - пинг.
     */
    private void addToWaitings(int seq, Object userData) {
        synchronized (waitings) {
            waitings.insert(seq, userData);
        }
    }

    /**
     * Проверить объекты которые ждут свою очередь
     */
    private void checkForWaitingDatas() {
        int expectedSeq;

        synchronized (waitings) {
            expectedSeq = lastInsertedSeq + 1;
            Object mayBeData = waitings.remove(expectedSeq);

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
                mayBeData = waitings.remove(expectedSeq);
            }
        }
    }

    //////////
    // SEND //
    //////////

    public void sendUnreliable(byte[] data){
        sendBuffer[0] = unreliable;
        System.arraycopy(data, 0, sendBuffer, 5, data.length);
        sendPacket.setLength(data.length + 5);
        try {
            udp.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendAck(int seq){
        ackBuffer[0] = reliableAck;
        PacketType.putInt(ackBuffer, seq, 1);
        try {
            udp.send(ackPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
