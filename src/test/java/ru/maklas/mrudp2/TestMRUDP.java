package ru.maklas.mrudp2;

import java.net.InetAddress;
import java.util.Arrays;

public class TestMRUDP {

    private static final int port = 9002;

    public static void main(String[] args) throws Exception{

        new ServerLooper(port, new ConnectionProcessor() {
            @Override
            public Response<byte[]> acceptConnection(final Socket socket, byte[] userData) {

                System.out.println("Server accepting auth");

                new Looper(socket, new SocketProcessor() {
                    @Override
                    public void process(Socket sock, SocketIterator si, byte[] data) {
                        System.out.println("SERVER-SUBSOCKET received: " + Arrays.toString(data));
                    }
                });

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        socket.send(new byte[]{7, 7, 7});
                    }
                }).start();

                return Response.accept(new byte[]{1, 2, 3});
            }
        });


        Socket client = new Socket(InetAddress.getLocalHost(), port, 512, 15000);
        new Looper(client, new SocketProcessor() {
            @Override
            public void process(Socket sock, SocketIterator si, byte[] data) {
                System.out.println("Client socket received: " + Arrays.toString(data));
            }
        });


        client.connect(new byte[]{1, 1, 1}, 1000);
        client.addPingListener(new PingListener() {
            @Override
            public void onPingChange(Socket socket, float ping) {
                System.out.println("Ping to server: " + ping);
            }
        });

        Thread.sleep(100);
        client.send(new byte[]{5, 5, 5});

        Thread.sleep(200000);
        System.out.println("FINISHED");
        System.exit(0);
    }


}
