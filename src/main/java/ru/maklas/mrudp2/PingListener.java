package ru.maklas.mrudp2;

public interface PingListener {

    void onPingChange(ByteSocket socket, float ping);

}
