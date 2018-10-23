package ru.maklas.mnet2;

/**
 * Used to provide an instance for specified Class
 */
public interface Provider<T> {

    T provide();

}
