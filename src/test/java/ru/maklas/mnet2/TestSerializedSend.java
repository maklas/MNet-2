package ru.maklas.mnet2;

import org.junit.Assert;
import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;
import ru.maklas.mnet2.objects.UpdateObject;
import ru.maklas.mnet2.serialization.Serializer;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestSerializedSend implements ServerAuthenticator{


    //3. Тест sendSerUnrel() и sendSerialized()

    public static final int port = 9002;

    @Test
    public void testSendSerialized() throws Exception {
        final AtomicInteger received = new AtomicInteger();

        ServerSocket serverSocket = TestUtils.newServerSocket(TestUtils.udp(port, 200, 0), this);
        TestUtils.startUpdating(serverSocket, 16, new SocketProcessor() {
            @Override
            public void process(Socket s, Object o) {
                received.getAndIncrement();
                System.out.println(o);
            }
        });

        Socket client = new SocketImpl(InetAddress.getLocalHost(), port, TestUtils.serializerSupplier.get());

        Log.trace("Connecting...");
        ServerResponse response = client.connect(new ConnectionRequest("maklas", "123", 22, true), 15000);
        Log.trace(response.toString());
        assertEquals(ResponseType.ACCEPTED, response.getType());
        assertNotNull(response.getResponse());

        Serializer serializer = client.getSerializer();

        UpdateObject uo = new UpdateObject("String", 0, 1, 100);
        byte[] serialized = serializer.serialize(uo);

        client.sendSerialized(serialized);
        client.sendSerialized(serialized);
        client.sendSerUnrel(serialized);


        Thread.sleep(1500);

        Assert.assertEquals(3, received.get());

        serverSocket.close();
        client.close();
    }



    @Test
    public void testUnreliableBatch() {
        ConnectionRequest cr = new ConnectionRequest("My Name", "My Password", 22, true);
        ConnectionRequest cr2 = new ConnectionRequest("My Name", "My Password", 22, false);

        NetBatch netBatch = new NetBatch(2);
        netBatch.add(cr);
        netBatch.add(cr2);
        byte[] bytes = (byte[]) PacketType.buildSafeBatchUnreliable(PacketType.batchUnreliable, netBatch.convertAndGet(TestUtils.serializerSupplier.get()), 0, 512)[0];

        Object[] objects = PacketType.breakBatchDownUnreliable(bytes, TestUtils.serializerSupplier.get());

        for (Object object : objects) {
            System.out.println(object);
        }
    }

    @Override
    public void acceptConnection(Connection conn) {
        System.out.println("Received connection request: " + conn);

        if ((conn.getRequest() instanceof ConnectionRequest)
                && "123".equals(((ConnectionRequest) conn.getRequest()).getPassword())){
            ConnectionResponse response = new ConnectionResponse("Welcome, " + ((ConnectionRequest) conn.getRequest()).getName() + "!");
            System.out.println("Responding with " + response);
            Socket socket = conn.accept(response);
        }
    }

}
