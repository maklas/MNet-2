package ru.maklas.mnet2;

import com.esotericsoftware.kryo.KryoException;
import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;
import ru.maklas.mnet2.objects.MySerializer;
import ru.maklas.mnet2.objects.UpdateObject;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;

public class TestBufferOverflows implements ServerAuthenticator {

    //6. Протестировать bufferOverflow

    public static final int port = 9004;

    @Test(expected = KryoException.class)
    public void bufferTest() throws Exception {
        ServerSocket serverSocket = TestUtils.newServerSocket(TestUtils.udp(port, 200, 40), this);
        TestUtils.startUpdating(serverSocket, 16);

        Socket client = new SocketImpl(InetAddress.getLocalHost(), port, new MySerializer(512).get());

        Log.trace("Connecting...");
        ServerResponse response = client.connect(new ConnectionRequest("maklas", "123", 22, true), 15000);
        Log.trace(response.toString());
        assertEquals(ResponseType.ACCEPTED, response.getType());


        client.send(new UpdateObject("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890", 0, 3, 100));


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
