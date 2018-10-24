package ru.maklas.mnet2.objects;

public class ConnectionRequest {

    String name;
    String password;
    int age;
    boolean remember;

    public ConnectionRequest() {

    }

    public ConnectionRequest(String name, String password, int age, boolean remember) {
        this.name = name;
        this.password = password;
        this.age = age;
        this.remember = remember;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public int getAge() {
        return age;
    }

    public boolean isRemember() {
        return remember;
    }


    @Override
    public String toString() {
        return "{" +
                "name='" + name + '\'' +
                ", password='" + password + '\'' +
                ", age=" + age +
                ", remember=" + remember +
                '}';
    }
}
