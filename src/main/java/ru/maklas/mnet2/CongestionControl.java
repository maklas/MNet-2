package ru.maklas.mnet2;

import com.badlogic.gdx.utils.FloatArray;

public class CongestionControl {

    public int resendPackets;
    FloatArray noResendsDelays = new FloatArray();

    public long calculateAdjustment(long currentDelay){
        if (resendPackets + noResendsDelays.size <= 10) return currentDelay; //Слишком мало данных

        if (noResendsDelays.size > resendPackets){ //Если по большей части пакеты доставляются с первого раза.
            return (long) Math.ceil(standardDeviationMax()) + 1;
        } else {
            return (long) (currentDelay * 1.5d);
        }
    }

    public void clear(){
        resendPackets = 0;
        noResendsDelays.clear();
    }

    public int size(){
        return resendPackets + noResendsDelays.size;
    }

    private double standardDeviationMax(){
        int size = noResendsDelays.size;
        float[] values = noResendsDelays.items;

        float mean = 0;
        for (int i = 0; i < size; i++) {
            mean += values[i];
        }
        mean = mean / size;

        float sum = 0;
        for (int i = 0; i < size; i++) {
            sum += (values[i] - mean) * (values[i] - mean);
        }

        return mean + Math.sqrt(sum / size);
    }


}
