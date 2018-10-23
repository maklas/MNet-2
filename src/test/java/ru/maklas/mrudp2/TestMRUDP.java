package ru.maklas.mrudp2;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class TestMRUDP {

    private static final int port = 9002;
    private static AtomicInteger curr = new AtomicInteger();
    private static ServerLooper serverLooper;
    private static Looper clientLooper;

    public static void main(String[] args) throws Exception{

        serverLooper = new ServerLooper(port, new ConnectionProcessor() {
            @Override
            public Response<byte[]> acceptConnection(final ByteSocket socket, byte[] userData) {

                Log.trace("Server accepting auth");

                serverLooper.addLooper(
                        new Looper(socket, new SocketProcessor() {
                            @Override
                            public void process(ByteSocket sock, byte[] data) {
                                //int val = PacketType.extractInt(data, 0);
                                //System.out.println(val);
                                //byte[] response = generateResponse(data);
                                //sock.send(response);

                                System.out.println("Server received: " + Arrays.toString(data));
                            }
                        })
                );
                socket.addPingListener(new PingListener() {
                    @Override
                    public void onPingChange(ByteSocket socket, float ping) {
                        System.out.println("Ping to client: " + ping);
                    }
                });
                socket.addDcListener(new DCListener() {
                    @Override
                    public void socketClosed(ByteSocket socket, String msg) {
                        System.out.println("DC listener of server. Disconnected as " + msg);
                        serverLooper.serverSocket.close();
                    }
                });

                return Response.accept(new byte[]{1, 2, 3});
            }
        });


        final ByteSocket client = new ByteSocket(InetAddress.getLocalHost(), port, 512, 15000, 2500, 100);
        clientLooper = new Looper(client, new SocketProcessor() {

            @Override
            public void process(ByteSocket sock, byte[] data) {
                int val = PacketType.extractInt(data, 0);
                curr.set(val);
                System.out.println(val);
                byte[] response = generateResponse(data);
                sock.send(response);
            }
        }).start();


        Log.trace("Connecting to server...");
        ConnectionResponse response = client.connect(new byte[]{1, 1, 1}, 10000);
        Log.trace("Response: " + response);

        client.addPingListener(new PingListener() {
            @Override
            public void onPingChange(ByteSocket socket, float ping) {
                Log.trace("Ping to server: " + ping);
            }
        });

        if (response.getType() != ResponseType.ACCEPTED) {
            return;
        }

        Thread.sleep(100);
        Log.trace("Sending first request...");

        client.send(new byte[]{1, 1, 1, 1, 1});
        client.addDcListener(new DCListener() {
            @Override
            public void socketClosed(ByteSocket socket, String msg) {
                System.out.println("DC listener of client. Disconnected as " + msg);
            }
        });

        Thread.sleep(50000);

        serverLooper.serverSocket.close();

        Thread.sleep(1000);
        Log.trace("FINISHED");
    }

    private static byte[] generateResponse(byte[] data){
        byte[] response = new byte[4];
        try {
            System.arraycopy(data, 0, response, response.length - data.length, data.length);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(Arrays.toString(data));
            System.exit(0);
        }
        int i = PacketType.extractInt(response, 0);
        PacketType.putInt(response, i+1, 0);
        return response;
    }

    //0   - 790
    //20% - 380
    //40% - 195
    //60% - 125
    //80% - 50
    //90% - 12


}
