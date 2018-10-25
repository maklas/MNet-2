package ru.maklas.mnet2;

import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;
import ru.maklas.mnet2.objects.UpdateObject;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class Tests implements ServerAuthenticator {

    public static final int port = 9000;

    @Test
    public void testConnections() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port, this, TestUtils.serializerSupplier);
        TestUtils.startUpdating(serverSocket, 16);


        Socket client = new SocketImpl(InetAddress.getLocalHost(), port, TestUtils.serializerSupplier.get());

        System.out.println(client.connect(new ConnectionRequest("maklas", "123", 22, true), 5000));

        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 100; i++) {
            client.update((sock, o) -> System.out.println("Client received " + (counter.getAndIncrement()) + ":  " + o));
            Thread.sleep(16);
        }

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

            NetBatch batch = new NetBatch();

            for (int i = 0; i < 1000; i++) {
                batch.add(new UpdateObject("asdfasf=rawfssfarfaa", 100, 200, i));
            }

            socket.sendBatch(batch);
        }
    }
}
