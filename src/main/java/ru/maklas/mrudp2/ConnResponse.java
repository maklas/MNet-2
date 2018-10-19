package ru.maklas.mrudp2;

public class ConnResponse {

    ResponseType type;
    byte[] data;

    public ConnResponse(ResponseType type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public ConnResponse(ResponseType type) {
        this.type = type;
        this.data = new byte[0];
    }

    public ResponseType getType() {
        return type;
    }

    public byte[] getData() {
        return data;
    }

}
