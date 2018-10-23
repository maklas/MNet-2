package ru.maklas.mrudp2;

class Utils {


    public static String toString(byte[] a) {
        return toString(a, a.length);
    }

    public static String toString(byte[] a, int len) {
        if (a == null)
            return "null";
        int iMax = len - 1;
        if (iMax == -1)
            return "[]";

        StringBuilder b = new StringBuilder();
        b.append('[');
        b.append(PacketType.toString(a[0])).append(", ");
        for (int i = 1; ; i++) {
            b.append(a[i]);
            if (i == iMax)
                return b.append(']').toString();
            b.append(", ");
        }
    }

}
