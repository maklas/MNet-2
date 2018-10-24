package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Supplier;
import ru.maklas.mnet2.objects.MySerializer;
import ru.maklas.mnet2.serialization.Serializer;

public class TestUtils {

    public static Supplier<Serializer> serializerSupplier = new MySerializer();


    public static void startUpdating(ServerSocket serverSocket, int freq){
        new Thread(() -> {
            while (!serverSocket.isClosed()){
                serverSocket.update();
                try {
                    Thread.sleep(freq);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }



}
