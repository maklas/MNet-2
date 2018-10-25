package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Array;
import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TestSocketStates implements ServerAuthenticator{


    //4. Тест на состояние сокета
    //5. Тест на дисконнект

    public static final int port = 9003;

    @Test
    public void testStates() throws Exception {

        final AtomicBoolean dcSubCalled = new AtomicBoolean();
        final AtomicBoolean dcClientCalled = new AtomicBoolean();

        final Socket client = new SocketImpl(InetAddress.getLocalHost(), port, TestUtils.serializerSupplier.get());
        assertEquals(SocketState.NOT_CONNECTED, client.getState());



        UDPSocket silentSocket = new JavaUDPSocket(port);
        new Thread(new Runnable() {
            @Override
            public void run() {
                TestUtils.sleep(500);
                assertEquals(SocketState.CONNECTING, client.getState());
            }
        });
        client.connect(new ConnectionRequest(), 1000);
        silentSocket.close();
        Thread.sleep(1000);


        ServerSocket serverSocket = TestUtils.newServerSocket(TestUtils.udp(port, 0, 0), this);
        TestUtils.startUpdating(serverSocket, 16, new SocketProcessor() {
            @Override
            public void process(Socket s, Object o) {
                System.out.println(o);
            }
        });



        Log.trace("Connecting...");
        ServerResponse response = client.connect(new ConnectionRequest("maklas", "123", 22, true), 15000);
        Log.trace(response.toString());
        assertEquals(ResponseType.ACCEPTED, response.getType());
        assertNotNull(response.getResponse());
        assertEquals(SocketState.CONNECTED, client.getState());
        client.addDcListener(new DCListener() {
            @Override
            public void socketClosed(Socket s, String m) {
                dcClientCalled.set(true);
            }
        });

        Array<Socket> sockets = serverSocket.getSockets();
        assertEquals(1, sockets.size);
        Socket subSocket = sockets.get(0);
        subSocket.addDcListener(new DCListener() {
            @Override
            public void socketClosed(Socket s, String m) {
                dcSubCalled.set(true);
            }
        });
        assertEquals(SocketState.CONNECTED, subSocket.getState());
        assertTrue(client.isConnected());
        assertFalse(client.isClosed());
        assertTrue(subSocket.isConnected());
        assertFalse(subSocket.isClosed());

        client.close();
        assertEquals(SocketState.CLOSED, client.getState());

        Thread.sleep(1000);
        assertEquals(SocketState.CLOSED, subSocket.getState());
        assertTrue(subSocket.isClosed());
        assertFalse(subSocket.isConnected());
        assertTrue(client.isClosed());
        assertFalse(client.isConnected());
        assertTrue(dcClientCalled.get());
        assertTrue(dcSubCalled.get());
        serverSocket.close();
    }




    @Override
    public void acceptConnection(Connection conn) {
        System.out.println("Received connection request: " + conn);

        if ((conn.getRequest() instanceof ConnectionRequest)
                && "123".equals(((ConnectionRequest) conn.getRequest()).getPassword())){
            ConnectionResponse response = new ConnectionResponse("Welcome, " + ((ConnectionRequest) conn.getRequest()).getName() + "!");
            System.out.println("Responding with " + response);


            assertEquals(SocketState.NOT_CONNECTED, conn.socket.getState());
            Socket socket = conn.accept(response);
            assertEquals(SocketState.CONNECTED, conn.socket.getState());

        }
    }

}
