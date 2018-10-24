package ru.maklas.mnet2;

import com.badlogic.gdx.utils.AtomicQueue;

public class Client implements Runnable {

    Socket socket;
    SocketProcessor processor;
    AtomicQueue<Runnable> actions = new AtomicQueue<>(1000);

    public Client(Socket socket, SocketProcessor processor) {
        this.socket = socket;
        this.processor = processor;
    }


    @Override
    public void run() {
        while (!socket.isClosed()){
            update();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void update(){
        if (!socket.isClosed()){
            socket.update(processor);
        }

        Runnable poll = actions.poll();
        while (poll != null){
            poll.run();
            poll = actions.poll();
        }
    }

    public void exec(Runnable r){
        actions.put(r);
    }
}
