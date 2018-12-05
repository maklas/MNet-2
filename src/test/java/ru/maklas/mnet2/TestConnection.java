package ru.maklas.mnet2;

import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;
import ru.maklas.mnet2.objects.UpdateObject;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestConnection implements ServerAuthenticator {

    public static final int port = 9000;

    @Test
    public void testConnections() throws Exception {

        ServerSocket serverSocket = TestUtils.newServerSocket(TestUtils.udp(port, 250, 50), this);
        TestUtils.startUpdating(serverSocket, 16);

        Socket client = new SocketImpl(InetAddress.getLocalHost(), port, TestUtils.serializerSupplier.get());

        Log.trace("Connecting...");
        ServerResponse response = client.connect(new ConnectionRequest("maklas", "123", 22, true), 15000);
        Log.trace(response.toString());

        assertEquals(ResponseType.ACCEPTED, response.getType());
        assertNotNull(response.getResponse());
        assertEquals("Welcome, maklas!", ((ConnectionResponse) response.getResponse()).getWelcome());

        final AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 200; i++) {
            client.update(new SocketProcessor() {
                @Override
                public void process(Socket sock, Object o) {
                    Log.trace("Client received " + (counter.getAndIncrement()) + ":  " + o);
                }
            });
            Thread.sleep(50);
        }


        Log.trace("Checking result...");
        assertEquals(1000, counter.get());

        if (client.isConnected()){
            client.close();
        }

        serverSocket.close();
    }

    @Override
    public void acceptConnection(Connection conn) {
        System.out.println("Received connection request: " + conn);

        if ((conn.getRequest() instanceof ConnectionRequest)
            && "123".equals(((ConnectionRequest) conn.getRequest()).getPassword())){
            ConnectionResponse response = new ConnectionResponse("Welcome, " + ((ConnectionRequest) conn.getRequest()).getName() + "!");
            System.out.println("Responding with " + response);
            Socket socket = conn.accept(response);

            for (int i = 0; i < 1000; i++) {
                socket.send(new UpdateObject("Big Random String", 100, 200, i));
            }
        }
    }


    //1. Тест на коннект с пингом и потерей + отправить запрос сразу при подключении.
}
