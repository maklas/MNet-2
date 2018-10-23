package ru.maklas.mnet2;

public interface PingListener {

    void notify(Socket socket, float ping);

}
