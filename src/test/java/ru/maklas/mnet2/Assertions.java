package ru.maklas.mnet2;

public class Assertions {

    public static void assertTrue(boolean condition){
        if (!condition){
            throw new RuntimeException("Assert True failed");
        }
    }

    public static void assertFalse(boolean condition){
        if (condition){
            throw new RuntimeException("Assert False failed");
        }
    }

    public static void assertEquials(Object a, Object b){
        if (a != b){
            throw new RuntimeException(a + " != " + b);
        }
    }

    public static void never(){
        throw new RuntimeException("Never");
    }




}
