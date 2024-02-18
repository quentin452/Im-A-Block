package de.labystudio.game.libraries.trove.array;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicLong;

import sun.misc.Unsafe;

/**
 * Direct memory allocated byte buffer.
 * <p>
 * Uses {@link sun.misc.Unsafe} directly to avoid byte
 * swaps due to endian mis-matches that can result when using
 * {@link ByteBuffer.allocateDirect()}
 */
public abstract class AbstractOffheapArray {

    protected static final Unsafe UNSAFE;

    private static final CleanerCreateMethod createMethod = initializeCreateMethod();
    private static final Method cleanMethod = initializeCleanMethod();

    private static class Deallocator implements Runnable {

        private final AtomicLong boxedAddress;

        private Deallocator(AtomicLong boxedAddress) {
            if (boxedAddress.get() == 0) {
                throw new IllegalArgumentException("Cannot allocate with address 0");
            }
            this.boxedAddress = boxedAddress;
        }

        @Override
        public void run() {
            UNSAFE.freeMemory(boxedAddress.getAndSet(0));
        }
    }

    /**
     * A cleaner is basically a fancy finalizer with nicer semantics. This cleaner will
     * deallocate the manually allocated memory used by this offheap array after the
     * offheap array becomes unreachable.
     * <p>
     * http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/sun/misc/Cleaner.java
     *
     * Need to keep it as a generic Object so that it can be assigned a type at runtime using reflection.
     * Unfortunately, starting with Java 9, sun.misc.Cleaner moved to java.lang.ref.Cleaner, so in order
     * for this library to be usable for both Java 8 and Java 9+, it must be able to decide at runtime.
     */
    private final Object cleaner;
    private final AtomicLong boxedAddress;
    protected long address;
    protected long capacity;

    public AbstractOffheapArray(long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Cannot allocate with capacity=" + capacity);
        }
        address = UNSAFE.allocateMemory(capacity);
        boxedAddress = new AtomicLong(address);
        cleaner = createCleaner(this, new Deallocator(boxedAddress));
        UNSAFE.setMemory(address, capacity, (byte) 0);
        this.capacity = capacity;
    }

    protected void resize(long newCapacity) {
        long currAddress = address;
        if (newCapacity < 0) {
            throw new IllegalArgumentException("Cannot reallocate with capacity=" + newCapacity);
        }
        if (currAddress == 0 || boxedAddress.get() != currAddress) {
            throw new IllegalStateException("Cannot reallocate after freeing");
        }
        long newAddress = UNSAFE.reallocateMemory(currAddress, newCapacity);
        if (!boxedAddress.compareAndSet(currAddress, newAddress)) {
            UNSAFE.freeMemory(newAddress);
            throw new IllegalStateException("Race condition while resizing");
        }
        address = boxedAddress.get();
        if (newCapacity > capacity) {
            UNSAFE.setMemory(address + capacity, newCapacity - capacity, (byte) 0);
        }
        this.capacity = newCapacity;
    }

    public void clear() {
        if (capacity > 0) {
            UNSAFE.setMemory(address, capacity, (byte) 0);
        }
    }

    public void free() {
        address = 0;
        capacity = 0;

        // Cleaner is guaranteed to run its runnable at most once, so its fine to
        // call free multiple times, and nothing additional will happen when this
        // array becomes unreachable.
        clean();
    }

    public abstract long capacity();

    protected void check(long index) {
        if (index < 0 || index >= capacity()) {
            throw new IndexOutOfBoundsException("index=" + index + ", capacity=" + capacity());
        }
    }

    protected void check(long ourIndex, int arrayIndex, int arrayLength, int length) {
        if (ourIndex < 0 || length < 0
            || ourIndex + length > capacity()
            || arrayIndex < 0
            || arrayIndex + length > arrayLength) {
            throw new IndexOutOfBoundsException(
                "ourIndex=" + ourIndex
                    + ", ourLength="
                    + capacity()
                    + ", arrayIndex="
                    + arrayIndex
                    + ", arrayLength="
                    + arrayLength
                    + ", length="
                    + length);
        }
    }

    static {
        Field f = getPrivateDeclaredField(Unsafe.class, "theUnsafe");
        try {
            UNSAFE = (Unsafe) f.get(null);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Gets a private field from a class.
     */
    private static Field getPrivateDeclaredField(final Class<?> clazz, final String name) {
        // N.B., the setAccessible(true) call should happen within a doPrivileged() block.
        return AccessController.doPrivileged(new PrivilegedAction<Field>() {

            @Override
            public Field run() {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (SecurityException e) {
                    throw new IllegalStateException(e);
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static Object createCleaner(Object obj, Runnable cleanUpRunnable) {
        if (createMethod == null) {
            throw new IllegalStateException("Was not able to find Cleaner#create method to use.");
        }

        try {
            return createMethod.method.invoke(createMethod.instance, obj, cleanUpRunnable);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create a new cleaner.", e);
        }
    }

    private void clean() {
        if (cleanMethod == null) {
            throw new IllegalStateException("Was not able to find Cleaner#clean to use.");
        }

        try {
            cleanMethod.invoke(cleaner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not invoke clean method.", e);
        }
    }

    private static CleanerCreateMethod initializeCreateMethod() {
        // Java 8
        try {
            Class<?> cleaner = Class.forName("sun.misc.Cleaner");
            return new CleanerCreateMethod(cleaner.getMethod("create", Object.class, Runnable.class), null);
        } catch (ReflectiveOperationException e) {
            // means we are on java 9+, try different class
        }

        // Java 9+
        try {
            Class<?> cleaner = Class.forName("java.lang.ref.Cleaner");
            Method create = cleaner.getMethod("create");
            Object cleanerInstance = create.invoke(null);
            return new CleanerCreateMethod(
                cleaner.getMethod("register", Object.class, Runnable.class),
                cleanerInstance);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method initializeCleanMethod() {
        // Java 8
        try {
            Class<?> cleanerClass = Class.forName("sun.misc.Cleaner");
            return cleanerClass.getMethod("clean");
        } catch (ReflectiveOperationException e) {
            // means we are on java 9+, try different class
        }

        // Java 9+
        try {
            Class<?> cleanerClass = Class.forName("java.lang.ref.Cleaner$Cleanable");
            return cleanerClass.getMethod("clean");
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * In Java 9+ the thing we actually want to create is an instance of a
     * Cleaner.Cleanable, not a Cleaner itself. As such, we actually need
     * an instance of the Cleaner, so we create this stub class to hold both
     * pieces of data.
     */
    private static class CleanerCreateMethod {

        final Method method;
        final Object instance;

        private CleanerCreateMethod(Method method, Object instance) {
            this.method = method;
            this.instance = instance;
        }
    }
}
