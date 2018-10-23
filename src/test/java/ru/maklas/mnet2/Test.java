package ru.maklas.mnet2;

public class Test {

    public static void main(String[] args) {
        byte[] bytes = {-13, 120, -35, -25};
        System.out.println(PacketType.extractInt(bytes, 0));

    }

}
