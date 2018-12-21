package ru.maklas.mnet2.congestion;

import ru.maklas.mnet2.SocketImpl;

public interface CongestionManager {

    long calculateDelay(SocketImpl.ResendPacket respondedPacket, long currentTime, long currentDelay);

}
