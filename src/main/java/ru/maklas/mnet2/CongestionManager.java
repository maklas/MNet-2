package ru.maklas.mnet2;

public interface CongestionManager {

    long calculateDelay(SocketImpl.ResendPacket respondedPacket, long currentTime, long currentDelay);

}
