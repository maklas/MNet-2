package ru.maklas.mnet2;

import com.badlogic.gdx.utils.Array;

/** A pool of objects that can be reused to avoid allocation.
 * @author Nathan Sweet */
abstract class FastPool<T> {
    /** The maximum number of objects that will be pooled. */
    public final int max;
    /** The highest number of free objects. Can be reset any time. */
    public int peak;

    private final Array<T> freeObjects;

    /** Creates a pool with an initial capacity of 16 and no maximum. */
    public FastPool() {
        this(16, Integer.MAX_VALUE);
    }

    /** Creates a pool with the specified initial capacity and no maximum. */
    public FastPool(int initialCapacity) {
        this(initialCapacity, Integer.MAX_VALUE);
    }

    /** @param max The maximum number of free objects to store in this pool. */
    public FastPool(int initialCapacity, int max) {
        freeObjects = new Array(false, initialCapacity);
        this.max = max;
    }

    abstract protected T newObject ();

    /** Returns an object from this pool. The object may be new (from {@link #newObject()}) or reused (previously
     * {@link #free(Object) freed}). */
    public T obtain () {
        return freeObjects.size == 0 ? newObject() : freeObjects.pop();
    }

    /** Puts the specified object in the pool, making it eligible to be returned by {@link #obtain()}. If the pool already contains
     * {@link #max} free objects, the specified object is reset but not added to the pool. */
    public void free (T object) {
        if (object == null) throw new IllegalArgumentException("object cannot be null.");
        if (freeObjects.size < max) {
            freeObjects.add(object);
            peak = Math.max(peak, freeObjects.size);
        }
    }

    /** Puts the specified objects in the pool. Null objects within the array are silently ignored.
     * @see #free(Object) */
    public void freeAll (Array<T> objects) {
        if (objects == null) throw new IllegalArgumentException("objects cannot be null.");
        Array<T> freeObjects = this.freeObjects;
        int max = this.max;
        for (int i = 0; i < objects.size; i++) {
            T object = objects.get(i);
            if (object == null) continue;
            if (freeObjects.size < max) freeObjects.add(object);
        }
        peak = Math.max(peak, freeObjects.size);
    }

    /** Removes all free objects from this pool. */
    public void clear () {
        freeObjects.clear();
    }

    /** The number of objects available to be obtained. */
    public int getFree () {
        return freeObjects.size;
    }
}
