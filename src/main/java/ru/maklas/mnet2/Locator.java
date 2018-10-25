package ru.maklas.mnet2;

import com.esotericsoftware.kryo.util.ObjectMap;
import ru.maklas.mnet2.serialization.Serializer;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class Locator {
    private static int locatorCounter = 0;

    private final ExecutorService executor;
    private final DatagramPacket sendingPacket;
    private final DatagramPacket receivingPacket;
    private final DatagramSocket socket;
    private final byte[] uuid;
    private final InetAddress address;
    private final Receiver receiver;
    private volatile int port;
    private final AtomicInteger seqCounter = new AtomicInteger(0);
    private final AtomicBoolean discovering = new AtomicBoolean(false);
    private volatile boolean isSleeping = false;
    private volatile Thread sleepingThread;
    private final Object sleepingMonitor = new Object();
    private final Serializer serializer;

    /**
     * Creates a new Locator instance which is able to send request
     * to the specified broadcast address and receive responses.
     * @param port A port {@link BroadcastServlet} must listen on to receive your request.
     * @param bufferSize max size of requests and responses. Make sure It's above any byte[] you're trying to send
 * @throws Exception if address can't be parsed.
     */
    public Locator(int port, int bufferSize, String uuid, Serializer serializer) throws Exception {
        this("255.255.255.255", port, bufferSize, uuid.getBytes(), serializer);
    }

    /**
     * Creates a new Locator instance which is able to send request
     * to the specified broadcast address and receive responses.
     * @param uuid Unique id for application. So that no other apps that use this library could see your request.
     *             {@link BroadcastServlet} must have the same UUID in oder to receive requests!
     * @param address Broadcast address. Use 255.255.255.255 if you can't know for sure subnet broadcast address.
     *                But that's not recommended since routers can sometimes block udp packets on 255.255.255.255
     * @param port A port {@link BroadcastServlet} must listen on to receive your request.
     * @param bufferSize max size of requests and responses. Make sure It's above any byte[] you're trying to send
     * @throws Exception if address can't be parsed.
     */
    public Locator(String address, int port, int bufferSize, byte[] uuid, Serializer serializer) throws Exception{
        this.uuid = Arrays.copyOf(uuid, 16);
        this.address = InetAddress.getByName(address);
        this.port = port;
        this.serializer = serializer;
        executor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Locator #" + locatorCounter++);
                t.setDaemon(true);
                return t;
            }
        });
        sendingPacket = new DatagramPacket(new byte[bufferSize], bufferSize);
        receivingPacket = new DatagramPacket(new byte[bufferSize], bufferSize);
        socket = new DatagramSocket();
        socket.setBroadcast(true);
        receiver = new Receiver(receivingPacket, socket, this.uuid, serializer);
        executor.submit(receiver);
    }

    /**
     * Starts discovery for specified amount of time. Only 1 discovery can be done at a time
     * @param discoveryTimeMS How many millisecond discovery will last.
     *                        This method will <b>blocks calling thread for this time until discovery is finished!</b>
     * @param resends How many re-sends should be done during discovery. Please note that UDP is unreliable protocol
     *                and it states that packet can be lost during data transmission. setting this value to 1 won't be
     *                a safe choice. 3-5 is usually enough. Also, don't make this value too high, flooding the router.
     * @param requestData Your request data
     * @param responseNotifier Response listener. It will be active until discovery ends, receiving responses.
     *                         Triggered by Thread that started the discovery
     * @return False if discovery can't start. Usual reason is that discovery is already in process.
     */
    public boolean startDiscovery(final int discoveryTimeMS, final int resends, final Object requestData, BroadcastReceiver bReceiver) {
        boolean canStart = discovering.compareAndSet(false, true);
        final byte[] serializedReq = serializer.serialize(requestData);
        if (!canStart){
            return false;
        }

        final int seq = seqCounter.getAndIncrement();
        this.receiver.activate(seq, bReceiver);
        executor.submit(new Runnable() {
            @Override
            public void run() {
                byte[] fullPackage = LocatorUtils.createRequest(uuid, seq, serializedReq);
                try {
                    final int msToWaitEachIteration = discoveryTimeMS/resends;
                    for (int i = 0; i < resends; i++) {
                        sendData(fullPackage);
                        Thread.sleep(msToWaitEachIteration);
                    }
                } catch (InterruptedException e) {}
            }
        });

        synchronized (sleepingMonitor) {
            sleepingThread = Thread.currentThread();
            isSleeping = true;
        }

        boolean interrupted = false;
        try {
            Thread.sleep(discoveryTimeMS);
        } catch (InterruptedException e) {
            interrupted = true;
        }

        synchronized (sleepingMonitor) {
            isSleeping = false;
            sleepingThread = null;
        }

        receiver.stop();
        discovering.set(false);
        bReceiver.finished(interrupted);
        return true;
    }

    /**
     * @return Whether this Locator is discovering right now.
     */
    public boolean isDiscovering(){
        return discovering.get();
    }

    /**
     * Interrupts current discovery. When this method is finished, doesn't guarantee that {@link #startDiscovery(int, int, byte[], Notifier)} returns.
     */
    public void interruptDiscovering(){
        synchronized (sleepingMonitor){
            Thread sleepingThread = this.sleepingThread;
            if (isSleeping && sleepingThread != null){
                sleepingThread.interrupt();
            }
        }


    }

    private void sendData(byte[] data){
        sendingPacket.setData(data);
        sendingPacket.setAddress(address);
        sendingPacket.setPort(port);
        try {
            socket.send(sendingPacket);
        } catch (IOException e) {}
    }

    /**
     * Closes UDP socket and inner threads
     */
    public void close(){
        socket.close();
        executor.shutdown();
    }

    public boolean isClosed(){
        return socket.isClosed();
    }

    private static class Receiver implements Runnable{

        private final DatagramPacket receivingPacket;
        private final DatagramSocket socket;
        private final byte[] uuid;
        private final Serializer serializer;
        private int seq;
        private BroadcastReceiver broadcastReceiver;
        private final ObjectMap<InetSocketAddress, Boolean> addressObjectMap;
        private volatile boolean stop = false;

        public Receiver(DatagramPacket receivingPacket, DatagramSocket socket, byte[] uuid, Serializer serializer) {
            this.receivingPacket = receivingPacket;
            this.socket = socket;
            this.uuid = uuid;
            this.serializer = serializer;
            this.addressObjectMap = new ObjectMap<InetSocketAddress, Boolean>();
        }

        void activate(int seq, BroadcastReceiver responseNotifier){
            this.seq = seq;
            this.broadcastReceiver = responseNotifier;
            this.stop = false;
            addressObjectMap.clear();
        }

        @Override
        public void run() {

            DatagramPacket receivingPacket = this.receivingPacket;
            byte[] receivingBuffer = receivingPacket.getData();
            DatagramSocket socket = this.socket;

            while (!Thread.interrupted()){


                try {
                    socket.receive(receivingPacket);
                } catch (IOException e) {
                    if (socket.isClosed())
                        break;
                    else
                        continue;
                }
                if (stop){
                    continue;
                }

                int length = receivingPacket.getLength();
                if (length < LocatorUtils.minMsgLength){
                    continue;
                }

                boolean startsWithUUID = LocatorUtils.startsWithUUID(receivingBuffer, uuid);
                if (!startsWithUUID
                        || !LocatorUtils.isResponse(receivingBuffer)
                        || LocatorUtils.getSeq(receivingBuffer) != seq){
                    continue;
                }

                InetAddress address = receivingPacket.getAddress();
                int port = receivingPacket.getPort();
                InetSocketAddress sockAddr = new InetSocketAddress(address, port);

                Boolean alreadyReceived = addressObjectMap.get(sockAddr);
                if (alreadyReceived == null){
                    alreadyReceived = false;
                }

                if (!alreadyReceived){
                    addressObjectMap.put(sockAddr, true);
                    try {
                        Object userResponse = serializer.deserialize(receivingBuffer, 21, length - 21);
                        broadcastReceiver.receive(new BroadcastResponse(address, port, userResponse));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }


            }
        }


        public void stop() {
            stop = true;
        }
    }

}
