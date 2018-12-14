package ru.maklas.mnet2;

import com.badlogic.gdx.utils.LongArray;

public class DefaultCongestionManager implements CongestionManager {

    private int resendPackets;
    private LongArray noResendsDelays = new LongArray();
    private int counter;

    @Override
    public long calculateDelay(SocketImpl.ResendPacket respondedPacket, long currentTime, long currentDelay) {
        if (respondedPacket.resends == 0) {
            noResendsDelays.add(currentTime - respondedPacket.sendTime);
        } else {
            resendPackets++;
        }

        if (++counter % 100 != 0){ //Вызываем раз в 100
            return currentDelay;
        }

        if (noResendsDelays.size > resendPackets){ //Если по большей части пакеты доставляются с первого раза.
            return (long) Math.ceil(standardDeviationMax());
        } else {
            return (long) (currentDelay * 1.5d);
        }

    }

    //mean + 2xSigma
    //Среднее значение плюс 2 стандартных девиации.
    private double standardDeviationMax(){
        int size = noResendsDelays.size;
        long[] values = noResendsDelays.items;

        double mean = 0;
        for (int i = 0; i < size; i++) {
            mean += values[i];
        }
        mean = mean / size;

        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += (values[i] - mean) * (values[i] - mean);
        }

        return mean + (2 * Math.sqrt(sum / size));
    }

}
