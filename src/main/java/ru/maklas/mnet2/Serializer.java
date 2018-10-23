package ru.maklas.mnet2;

public interface Serializer {

    /**
     * This serialization is used when {@link #useOffsetSerialization()} returns <b>false</b>.
     * New byte[] will be created from return value to be sent over socket
     * @param o object to serialize
     * @return byte[] representing serialized object. It should also contain data about Type (class) of this object
     * so it can be successfully deserialized on the other end
     */
    byte[] serialize(Object o);

    /**
     * This serialization is used when {@link #useOffsetSerialization()} returns <b>true</b>.
     * The first 5 bytes of this array must be empty or filled with useless data as it will be overwritten by MRUDP.
     * Please also note that this byte[] will not be copied and will be used to send data over socket so it must not be changed at any time after!
     * This method might be a better choice as it provides 2 times less byte[] allocation in special circumstances.
     * @param o object to serialize
     * @return byte[] representing serialized object. first 5 bytes of this array must be empty or filled with useless data.
     * It should also contain data about Type (class) of this object so it can be successfully deserialized on the other end
     */
    byte[] serializeOff5(Object o);

    /**
     * @return Type of serialization to use. Should just return false/true. Better not to change return value at runtime.
     */
    boolean useOffsetSerialization();

    /**
     * Deserialize object from byte[]. Throw any unchecked exception to notify that this byte[] can't be serialized.
     * It will print stack trace on System.err
     * @param bytes serialzied object
     * @return deserialized instance
     */
    Object deserialize(byte[] bytes);

}
