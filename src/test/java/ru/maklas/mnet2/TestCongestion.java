package ru.maklas.mnet2;

import com.badlogic.gdx.math.MathUtils;
import org.junit.Test;
import ru.maklas.mnet2.congestion.CongestionManager;
import ru.maklas.mnet2.congestion.DefaultCongestionManager;

public class TestCongestion {

    private static long currentTime;

    @Test
    public void test() throws Exception {
        currentTime = System.currentTimeMillis();
        CongestionManager cm = new DefaultCongestionManager();

        long currentDelay = 100;

        for (int i = 0; i < 20000; i++) {
            int resends = i % 100 == 0 ? 1 : 0;
            currentDelay = cm.calculateDelay(rp(MathUtils.random(-10, 10f) + 100, resends), currentTime, currentDelay);
        }

        Log.debug(String.valueOf(currentDelay));
    }

    @Test
    public void test2() throws Exception {
        currentTime = System.currentTimeMillis();
        CongestionManager cm = new DefaultCongestionManager();

        long currentDelay = 100;

        for (int i = 0; i < 2000; i++) {
            int resends = 0;
            int ping = 200;
            if (currentDelay < 200){ //Настоящий пинг - 200. Больше 200 означает точно пересыл.
                resends = 1;
                ping = (int) (currentDelay % 200);
            } else {
                resends = i % 100 == 0 ? 1 : 0; //Небольшой дроп пакетов всё равно есть.
                ping = 200 + MathUtils.random(-10, 10);
            }

            currentDelay = calculateDelay(cm, rp(ping, resends), currentDelay);
        }

        Log.debug(String.valueOf(currentDelay));
    }

    @Test
    public void test3() throws Exception {
        currentTime = System.currentTimeMillis();
        CongestionManager cm = new DefaultCongestionManager();

        long currentDelay = 500;

        for (int i = 0; i < 20000; i++) {
            long realPing = MathUtils.random(190, 210);
            long ping = 0;
            long resends = 0;
            if (currentDelay < realPing){
                ping = currentDelay % realPing;
                resends = realPing / currentDelay;
            } else if (Math.random() < 0.2f){ //20% потеряли пакет
                ping = 10;
                resends = 1;
            } else {
                ping = realPing;
            }

            currentDelay = calculateDelay(cm, rp(ping, resends), currentDelay);
        }

        Log.debug(String.valueOf(currentDelay));
    }


    private long calculateDelay(CongestionManager cm, SocketImpl.ResendPacket rp, long currentDelay){
        long newDelay = cm.calculateDelay(rp, currentTime, currentDelay);
        if (currentDelay != newDelay){
            Log.debug(currentDelay + " -> " + newDelay);
        }
        return newDelay;
    }





    private static SocketImpl.ResendPacket rp(double ping){
        return rp(ping, 0);
    }

    private static SocketImpl.ResendPacket rp(double ping, long resends){
        SocketImpl.ResendPacket rp = new SocketImpl.ResendPacket();
        rp.sendTime = (long) (currentTime - ping);
        rp.resends = (int) resends;
        return rp;
    }
}
