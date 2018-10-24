package ru.maklas.mnet2.objects;

public class ConnectionResponse {

    String welcome;

    public ConnectionResponse() {

    }

    public ConnectionResponse(String welcome) {
        this.welcome = welcome;
    }

    public String getWelcome() {
        return welcome;
    }

    @Override
    public String toString() {
        return "{" +
                "welcome='" + welcome + '\'' +
                '}';
    }
}
