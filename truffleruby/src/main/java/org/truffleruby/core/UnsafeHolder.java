package org.truffleruby.core;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class UnsafeHolder {

    @SuppressWarnings("restriction")
    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public static final Unsafe UNSAFE = getUnsafe();

    public static long getFieldOffset(Class<?> klass, String field) {
        try {
            Field f = klass.getDeclaredField(field);
            return UNSAFE.objectFieldOffset(f);
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
    }

}
