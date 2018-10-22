package ru.maklas.mrudp2;

public interface PingListener {

    void onPingChange(Socket socket, float ping);

}
