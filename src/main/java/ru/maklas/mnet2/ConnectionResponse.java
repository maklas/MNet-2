package ru.maklas.mnet2;

import java.util.Arrays;

public class ConnectionResponse {

    ResponseType type;
    byte[] data;

    public ConnectionResponse(ResponseType type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public ConnectionResponse(ResponseType type) {
        this.type = type;
        this.data = new byte[0];
    }

    /**
     * Type of response
     */
    public ResponseType getType() {
        return type;
    }

    /**
     * Server response data. Valuable only if type == (ACCEPTED || REJECTED)
     */
    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "{" +
                "type=" + type +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
