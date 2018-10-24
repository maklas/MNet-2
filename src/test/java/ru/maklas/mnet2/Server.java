package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.AtomicQueue;

public class Server implements Runnable{

    ServerSocket serverSocket;
    Array<Client> subsockets = new Array<>();
    AtomicQueue<Runnable> actions = new AtomicQueue<>(1000);

    public Server(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        new Thread(this).start();
    }

    public void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        while (serverSocket != null && !serverSocket.isClosed()){
            serverSocket.update();
            for (Client subsocket : subsockets) {
                subsocket.update();
            }

            Runnable poll = actions.poll();
            while (poll != null){
                poll.run();
                poll = actions.poll();
            }

            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void addSubsocket(Client client){
        actions.put(() -> subsockets.add(client));
    }

    public void exec(Runnable r){
        actions.put(r);
    }
}
