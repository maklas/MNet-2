package ru.maklas.mrudp2;


public class Looper implements Runnable{

    private final Socket socket;
    private final SocketProcessor processor;

    public Looper(Socket socket, SocketProcessor processor) {
        this.socket = socket;
        this.processor = processor;
        new Thread(this).start();
    }

    @Override
    public void run() {
        while (!socket.isClosed()){
            socket.update(processor);
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {}
        }
    }
}
