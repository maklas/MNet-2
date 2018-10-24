package ru.maklas.mnet2;

public interface ServerAuth {

    Response acceptConnection(Server server, Socket socket, Object userRequest);

}
