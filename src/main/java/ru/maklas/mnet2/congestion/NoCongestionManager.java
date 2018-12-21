package ru.maklas.mnet2.congestion;

import ru.maklas.mnet2.SocketImpl;

public class NoCongestionManager implements CongestionManager{

    @Override
    public long calculateDelay(SocketImpl.ResendPacket respondedPacket, long currentTime, long currentDelay) {
        return currentDelay;
    }
}
