package ru.maklas.mnet2;

import org.junit.Assert;
import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;
import ru.maklas.mnet2.objects.UpdateObject;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class TestReliability implements ServerAuthenticator{

    //2. Тест на надежность и последовательность пакетов и батчей в условиях пинга и потерь

    public static final int port = 9001;

    @Test
    public void reliabilityTest() throws Exception {
        SocketImpl.receivingQueueSize = 10000;

        ServerSocket serverSocket = TestUtils.newServerSocket(TestUtils.udp(port, 200, 50), this);
        TestUtils.startUpdating(serverSocket, 16);

        Socket client = new SocketImpl(InetAddress.getLocalHost(), port, TestUtils.serializerSupplier.get());

        Log.trace("Connecting...");
        ServerResponse response = client.connect(new ConnectionRequest("maklas", "123", 22, true), 15000);
        Log.trace(response.toString());


        final AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 200; i++) {
            client.update(new SocketProcessor() {
                @Override
                public void process(Socket sock, Object o) {
                    Assert.assertEquals(counter.getAndIncrement(), ((UpdateObject) o).getForce());
                }
            });
            Thread.sleep(100);
        }

        Assert.assertEquals(10000, counter.get());

        serverSocket.close();
        client.close();
    }


    @Override
    public void acceptConnection(Connection conn) {
        System.out.println("Received connection request: " + conn);

        if ((conn.getRequest() instanceof ConnectionRequest)
                && "123".equals(((ConnectionRequest) conn.getRequest()).getPassword())){
            ConnectionResponse response = new ConnectionResponse("Welcome, " + ((ConnectionRequest) conn.getRequest()).getName() + "!");
            System.out.println("Responding with " + response);
            Socket socket = conn.accept(response);


            for (int i = 0; i < 5000; i++) {
                socket.send(new UpdateObject("Big Random String", 100, 200, i));
            }

            NetBatch batch = new NetBatch();
            for (int i = 5000; i < 10000; i++) {
                batch.add(new UpdateObject("Big Random String", 500, 600, i));
            }
            socket.send(batch);
        }
    }

}
