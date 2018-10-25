package ru.maklas.mnet2;

import org.junit.Test;
import ru.maklas.mnet2.objects.ConnectionRequest;
import ru.maklas.mnet2.objects.ConnectionResponse;

import java.net.InetAddress;

public class TestBroadcast implements BroadcastProcessor {

    public static final int port = 9005;

    @Test
    public void testBroadcast() throws Exception {
        BroadcastServlet servlet = new BroadcastServlet(port, 512, "uuid", TestUtils.serializerSupplier.get(), this);
        servlet.enable();

        final Locator locator = new Locator(port, 512, "uuid", TestUtils.serializerSupplier.get());

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.trace("Starting discovery...");
                locator.startDiscovery(1000, 3, new ConnectionRequest("maklas", "password", 22, true), new BroadcastReceiver() {
                    @Override
                    public void receive(BroadcastResponse response) {
                        Log.trace("Received: " + response);
                    }

                    @Override
                    public void finished(boolean interrupted) {
                        Log.trace("Finished");
                    }
                });
            }
        }).start();


        for (int i = 0; i < 200; i++) {
            servlet.update();
            Thread.sleep(20);
        }

    }

    @Override
    public Object process(InetAddress address, int port, Object request) {
        Log.trace("Server received request: " + request);
        ConnectionResponse welcome = new ConnectionResponse("Welcome");
        Log.trace("Responding with: " + welcome);
        return welcome;
    }
}
