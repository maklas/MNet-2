package ru.maklas.mrudp2;


import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
/** A queue that allows one thread to call {@link #put(Object)} and another thread to call {@link #poll()}. Multiple threads must
 * not call these methods.
 * @author Matthias Mann */
class AtomicQueue<T> {
    private final AtomicInteger writeIndex = new AtomicInteger();
    private final AtomicInteger readIndex = new AtomicInteger();
    private final AtomicReferenceArray<T> queue;
    private final Object puttingMonitor = new Object();

    public AtomicQueue(int capacity) {
        queue = new AtomicReferenceArray(capacity);
    }

    private int next (int idx) {
        return (idx + 1) % queue.length();
    }

    public boolean put (T value) {
        synchronized (puttingMonitor) {
            int write = writeIndex.get();
            int read = readIndex.get();
            int next = next(write);
            if (next == read) return false;
            queue.set(write, value);
            writeIndex.set(next);
            return true;
        }
    }

    public T poll () {
        int read = readIndex.get();
        int write = writeIndex.get();
        if (read == write) return null;
        T value = queue.get(read);
        readIndex.set(next(read));
        return value;
    }

    public void clear() {
        T poll = poll();
        while (poll != null){
            poll = poll();
        }
    }
}
