package ru.maklas.mnet2.objects;

public class UpdateObject {

    String id;
    float x;
    float y;
    int force;

    public UpdateObject() {

    }

    public UpdateObject(String id, float x, float y, int force) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.force = force;
    }


    public String getId() {
        return id;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public int getForce() {
        return force;
    }

    @Override
    public String toString() {
        return "{" +
                "id='" + id + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", force=" + force +
                '}';
    }
}
