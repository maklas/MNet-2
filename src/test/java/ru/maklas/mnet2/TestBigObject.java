package ru.maklas.mnet2;

import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;
import ru.maklas.mnet2.objects.MySerializer;
import ru.maklas.mnet2.objects.UpdateObject;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestBigObject implements ServerAuthenticator {

    public final int port = 9006;

    public AtomicInteger stringSize = new AtomicInteger();

    @Test
    public void testBig() throws Exception {
        TestUtils.serializerSupplier = new MySerializer(10 * 1024 * 1024);

        ServerSocket serverSocket = TestUtils.newServerSocket(TestUtils.udp(port, 200, 40), this);
        TestUtils.startUpdating(serverSocket, 16);

        Socket client = new SocketImpl(InetAddress.getLocalHost(), port, TestUtils.serializerSupplier.get());

        Log.trace("Connecting...");
        ServerResponse response = client.connect(new ConnectionRequest("maklas", "123", 22, true), 15000);
        Log.trace(response.toString());

        assertEquals(ResponseType.ACCEPTED, response.getType());
        assertNotNull(response.getResponse());
        assertEquals("Welcome, maklas!", ((ConnectionResponse) response.getResponse()).getWelcome());

        final AtomicInteger biggestString = new AtomicInteger(0);
        final AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 200; i++) {
            client.update(new SocketProcessor() {
                @Override
                public void process(Socket sock, Object o) {
                    UpdateObject uo = (UpdateObject) o;
                    if (uo.getId().length() < 5000){
                        Log.trace("Client received " + (counter.getAndIncrement()) + ":  " + o);
                    } else {
                        Log.trace("Client received " + (counter.getAndIncrement()) + ": too big of an object to print");
                    }
                    if (biggestString.get() < uo.getId().length()){
                        biggestString.set(uo.getId().length());
                    }
                }
            });
            Thread.sleep(50);
        }

        Log.trace("Checking result...");
        assertEquals(11, counter.get());
        assertEquals(stringSize.get(), biggestString.get());

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

            for (int i = 0; i < 1; i++) {
                socket.sendBig(new UpdateObject("Very fucking Small Random String", 100, 200, (i * 2) + 1));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random ", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 1", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 11", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 111", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 1111", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 11111", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 111111", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 1111111", 100, 200, i * 2));
                socket.sendBig(new UpdateObject("Very fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small Random StringVery fucking Small randrandrandra Random 11111111", 100, 200, i * 2));
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1024; i++) {
                for (int j = 0; j < 400; j++) {
                    sb.append("abcde ");
                }
                sb.append('\n');
            }
            Log.debug("Huge size: " + (sb.length() / 1024) + " kb");
            UpdateObject huge = new UpdateObject(sb.toString(), 100, 200, 0);
            stringSize.set(huge.getId().length());
            socket.sendBig(huge);
        }
    }
}
