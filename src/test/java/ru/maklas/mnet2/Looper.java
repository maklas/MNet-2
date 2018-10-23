package ru.maklas.mnet2;


public class Looper implements Runnable{

    private final ByteSocket socket;
    private final ByteSocketProcessor processor;
    private final AtomicQueue<Runnable> actions = new AtomicQueue<Runnable>(1000);

    public Looper(ByteSocket socket, ByteSocketProcessor processor) {
        this.socket = socket;
        this.processor = processor;
    }

    public Looper start(){
        new Thread(this).start();
        return this;
    }

    @Override
    public void run() {
        while (!socket.isClosed()){
            _update();
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {}
        }
    }

    public void update() {
        if (!socket.isClosed()){
            _update();
        }
    }

    private void _update(){
        socket.update(processor);

        Runnable action = actions.poll();
        while (action != null){
            action.run();
            action = actions.poll();
        }
    }

    public void exec(Runnable r){
        actions.put(r);
    }
}
