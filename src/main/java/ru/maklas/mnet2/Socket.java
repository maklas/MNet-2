package ru.maklas.mnet2;

import java.io.IOException;
import java.net.InetAddress;

public interface Socket {

    /**
     * <p>Tries to establish connection to the server with custom user request.
     * Blocks thread for specified time until connected or not answered</p>
     *
     * @param timeout Blocking time in milliseconds
     * @param request Custom data (request data). Can be anything. For example login + password for validation.
     *                Serialized maximum size is bufferSize - 9
     * @return Connection response containing connection {@link ServerResponse result} and response data
     * @throws RuntimeException If this socket was created by server. Changing sub-server socket connections is forbidden
     */
    ServerResponse connect(int timeout, Object request) throws IOException;

    /**
     * Calls {@link #connect(int, Object) connect()} from new Thread, making connection asynchronous.
     * {@link Handler#handle(Object) handler.handle()} will be called from that new Thread.
     */
    void connectAsync(int timeout, Object request, Handler<ServerResponse> handler);

    /**
     * <p>Sends data to connected socket if current state == CONNECTED</p>
     * <p>This method provides reliable, and ordered Object sending. Objects will be delivered in the order of sending.
     * So recommended to use this method from the same thread each time.
     * Objects will be resent over and over until socket on the other end received it or disconnection occurs
     * </p>
     *
     * @param o Object to be send. Max serialized size == bufferSize - 5. Can be changed after this method is finished, since creates copy
     */
    void send(Object o);


    /**
     * <p>Sends Object to connected socket if current state == CONNECTED</p>
     * <p>This method sends data as fast as possible to a socket on the other end and <b>does not provide reliability nor ordering</b>.
     * this method uses plain UDP, so Object might not be delivered, or delivered not in the order of sending.
     * </p>
     *
     * @param o Object to be send. Max serialized size == bufferSize - 5. Data will be copied, so can be changed after method returns
     * @return <b>False</b> if socket is not connected.
     */
    void sendUnreliable(Object o);

    /**
     * <p>Sends batch to connected socket if current state == CONNECTED</p>
     * <p>This method provides reliable, and ordered Object sending. Objects inside of the batch will be delivered in the order of sending.
     * So recommended to use this method from the same thread each time.
     * Objects will be resent over and over until socket on the other end received it or disconnection occurs
     * </p>
     *
     * @param batch Batch to be send.
     */
    void sendBatch(NetBatch batch);


    /**
     * <p>Receives any pending data onto the {@link SocketProcessor}.
     * This process <b>must not be used by different threads at the same time</b> as it will throw an Exception.
     * Works better if you implement this method by one of your classes and pass the same instance every time, rather than instantiating
     * </p>
     * <p>If socket on the other end disconnects, this method might receive this Disconnection event and will trigger listeners.
     * </p>
     *
     * @param processor Instance that is going to receive all pending Objects in the order which they were sent.
     *                  Unreliable packets will also be consumed by this SocketProcessor. Receiving of the packets
     *                  can be temporarily stopped with {@link #stop()}
     */
    void update(SocketProcessor processor);

    /**
     * Stops receiving objects
     */
    void stop();

    void addDCListener(DCListener listener);

    void addPingListener(PingListener listener);

    void removeDCListener(DCListener listener);

    void removePingListener(PingListener listener);

    void removeAllListeners();

    /**
     * Sets how often ping should be updated in milliseconds.
     *
     * @param ms Update sleep time in milliseconds.
     */
    void setPingFrequency(int ms);


    /**
     * @return UserData of this socket. Uses auto-cast
     * Useful when you use single instance of SocketProcessor for processing multiple sockets.
     */
    <T> T getUserData();

    /**
     * Sets userData for this socket.
     * Useful when you use single instance of SocketProcessor for processing multiple sockets.
     * Use {@link #getUserData()} to retrieve userData
     *
     * @param userData Any object
     * @return userData object that was replaced (null for the 1st time)
     */
    Object setUserData(Object userData);


    /**
     * Performs disconnect if at that time socket was connected, stops any internal threads associated with this socket and then closes the UDPSocket. After this method,
     * socket becomes unusable and you have to create new instance to establish connection.
     */
    void close();


    /**
     * @return Current state of the socket
     */
    SocketState getState();

    /**
     * @return <b>True</b> if this socket is connected to any remote Socket. Same as getState() == CONNECTED
     */
    boolean isConnected();

    /**
     * @return <b>True</b> if currently {@link #update(SocketProcessor)} method is running.
     */
    boolean isProcessing();

    /**
     * @return Last ping after ping update
     */
    float getPing();

    /**
     * @return Whether this socket is closed and can't be reused
     */
    boolean isClosed();


    /**
     * Same as {@link #send(Object)}.
     * Send already serialized data.
     */
    void sendSerialized(byte[] data);

    /**
     * Don't call this method a lot, since it's synchronized very tightly with internals. Might block for a period of time.
     *
     * @return how many Objects are in resend queue, waiting to be received by connectedSocket.
     * Only reliable sends are stored in here
     */
    int getBufferSize();

    /**
     * Performs disconnect if at that time socket was connected, stops any internal threads associated with this socket and then closes the UDPSocket. After this method,
     * socket becomes unusable and you have to create new instance to establish connection.
     */
    void close(String msg);

    /**
     * @return InetAddress of the connected (or prevoiusly connected) remote socket. Value might be null if socket was never connected.
     */
    InetAddress getRemoteAddress();

    /**
     * @return port this socket is connected (was connected) to. Value might be -1 if socket was never connected.
     */
    int getRemotePort();

    /**
     * @return Local port of UDP socket
     */
    int getLocalPort();
}
