package com.tianscar.reflect.test;

import com.tianscar.reflect.Reflects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// Tested with Eclipse Temurin 8, 11, 17
public class ReflectsTest {

    private static final String TEST_STRING = "TEST STRING";
    private static final int TEST_INT = 0;

    @Test
    public void testGetInternalObjectField() throws NoSuchFieldException {
        final Field field = String.class.getDeclaredField("value");
        Assertions.assertThrows(IllegalAccessException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                field.get(TEST_STRING);
            }
        });
        Object value = Reflects.getField(TEST_STRING, field);
        if (value.getClass() == byte[].class) Assertions.assertEquals(new String((byte[]) value), TEST_STRING);
        else if (value.getClass() == char[].class) Assertions.assertEquals(new String((char[]) value), TEST_STRING);
    }

    @Test
    public void testModifyFinalStaticField() throws NoSuchFieldException {
        final Field field = ReflectsTest.class.getDeclaredField("TEST_INT");
        Assertions.assertThrows(IllegalAccessException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                field.setInt(null, 1);
            }
        });
        Reflects.setField(null, field, 1);
        Assertions.assertEquals(1, Reflects.getField(null, field));
    }

    @Test
    public void testInvokeInternalMethod() throws NoSuchMethodException, InvocationTargetException {
        try {
            final Method method = String.class.getDeclaredMethod("rangeCheck", char[].class, int.class, int.class);
            final char[] rangeCheckChars = "RANGE CHECK".toCharArray();
            Assertions.assertThrows(IllegalAccessException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    method.invoke(null, rangeCheckChars, 0, rangeCheckChars.length);
                }
            });
            Reflects.invokeMethod(null, method, rangeCheckChars, 0, rangeCheckChars.length);
        }
        catch (NoSuchMethodException e) {
            final Method method = String.class.getDeclaredMethod("checkBounds", byte[].class, int.class, int.class);
            final byte[] checkBoundsBytes = "RANGE CHECK".getBytes();
            Assertions.assertThrows(IllegalAccessException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    method.invoke(null, checkBoundsBytes, 0, checkBoundsBytes.length);
                }
            });
            Reflects.invokeMethod(null, method, checkBoundsBytes, 0, checkBoundsBytes.length);
        }
    }

    @Test
    public void testInvokeInternalConstructor() throws NoSuchMethodException, InvocationTargetException, InstantiationException {
        try {
            final Constructor<String> constructor = String.class.getDeclaredConstructor(char[].class, int.class, int.class, Void.class);
            final char[] chars = "UTF16LE STRING".toCharArray();
            Assertions.assertThrows(IllegalAccessException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    Assertions.assertEquals("UTF16LE STRING", constructor.newInstance(chars, 0, chars.length, null));
                }
            });
            Assertions.assertEquals("UTF16LE STRING", Reflects.newInstance(constructor, chars, 0, chars.length, null));
        }
        catch (NoSuchMethodException e) {
            final Constructor<String> constructor = String.class.getDeclaredConstructor(char[].class, boolean.class);
            final char[] chars = "UTF16LE STRING".toCharArray();
            Assertions.assertThrows(IllegalAccessException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    Assertions.assertEquals("UTF16LE STRING", constructor.newInstance(chars, true));
                }
            });
            Assertions.assertEquals("UTF16LE STRING", Reflects.newInstance(constructor, chars, true));
        }
    }

}
