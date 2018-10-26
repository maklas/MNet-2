package ru.maklas.mnet2;

import org.junit.Assert;
import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestBroadcast implements BroadcastProcessor {

    public static final int port = 9005;
    public static AtomicBoolean receivedResponse = new AtomicBoolean();

    @Test
    public void testBroadcast() throws Exception {
        BroadcastServlet servlet = new BroadcastServlet(port, 512, "uuid", TestUtils.serializerSupplier.get(), this);
        servlet.enable();

        final BroadcastSocket socket = new BroadcastSocket(TestUtils.udp(0, 100, 50), "255.255.255.255", port, 512, "uuid".getBytes(), TestUtils.serializerSupplier.get());
        ConnectionRequest request = new ConnectionRequest("maklas", "password", 22, true);
        Log.debug("Search started");
        socket.search(request, 5000, 25, new BroadcastReceiver() {
            @Override
            public void receive(BroadcastResponse response) {
                receivedResponse.set(true);
                Log.debug("!!!Client received a response: " + response);
            }

            @Override
            public void finished(boolean interrupted) {
                Log.debug("Finsihed: " + interrupted);
            }
        });
        assertTrue(socket.isSearching());

        for (int i = 0; i < 300; i++) {
            servlet.update();
            Thread.sleep(20);
        }

        assertFalse(socket.isSearching());
        assertFalse(socket.isClosed());
        socket.close();
        assertTrue(socket.isClosed());

    }

    @Override
    public Object process(InetAddress address, int port, Object request) {
        Log.trace("!!!Server received request: " + request);
        ConnectionResponse welcome = new ConnectionResponse("Welcome");
        Log.trace("Responding with: " + welcome);
        return welcome;
    }
}
