package ru.maklas.mnet2;

public interface ServerAuthenticator {

    /**
     * Make a decision if you want to accept new connection here.
     */
    void acceptConnection(Connection conn);

}
