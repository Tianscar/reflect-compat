package com.tianscar.reflect;

import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.reflect.Modifier.isStatic;

/**
 * Compatible layer for <code>java.lang.reflect</code> API which could run on Java 6+ (which meant fully support Android) and bypass the strong encapsulation in Java 16+.
 */
public final class Reflects {

    private Reflects() {
        throw new AssertionError("No " + Reflects.class.getName() + " instances for you!");
    }

    private static final Unsafe unsafe;

    // Java 6 doesn't have java.lang.invoke.*, so we use them via reflection.
    private static final Object lookup;
    private static final Method unreflectMethod;
    private static final Method unreflectConstructorMethod;
    private static final Method bindToMethod;
    private static final Method invokeWithArgumentsMethod;

    static {
        Unsafe _unsafe;
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            _unsafe = (Unsafe) field.get(null); // Java 6+
        } catch (NoSuchFieldException e) {
            _unsafe = null; // Unexpected
        } catch (IllegalAccessException e) {
            _unsafe = null; // Unexpected
        }
        unsafe = _unsafe;

        Method _unreflectMethod;
        Method _unreflectConstructorMethod;
        Class<?> lookupClazz;
        try {
            // Java 7+
            lookupClazz = Class.forName("java.lang.invoke.MethodHandles$Lookup");
            _unreflectMethod = lookupClazz.getDeclaredMethod("unreflect", Method.class);
            _unreflectConstructorMethod = lookupClazz.getDeclaredMethod("unreflectConstructor", Constructor.class);
        } catch (ClassNotFoundException e) {
            // Java 6
            lookupClazz = null;
            _unreflectMethod = null;
            _unreflectConstructorMethod = null;
        }
        catch (NoSuchMethodException e) {
            // Unexpected
            lookupClazz = null;
            _unreflectMethod = null;
            _unreflectConstructorMethod = null;
        }
        unreflectMethod = _unreflectMethod;
        unreflectConstructorMethod = _unreflectConstructorMethod;
        Object _lookup;
        if (lookupClazz == null) _lookup = null; // Java 6
        else {
            try {
                Field field = lookupClazz.getDeclaredField("IMPL_LOOKUP");
                if (trySetAccessible(field)) _lookup = field.get(null); // Java 7-15
                else if (unsafe != null) _lookup = unsafe.getObject(lookupClazz, unsafe.staticFieldOffset(field)); // Java 16+
                else _lookup = null; // Unexpected
            } catch (NoSuchFieldException e) {
                _lookup = null; // Unexpected
            } catch (IllegalAccessException e) {
                _lookup = null; // Unexpected
            }
        }
        lookup = _lookup;
        Method _bindToMethod;
        Method _invokeWithArgumentsMethod;
        try {
            // Java 7+
            Class<?> methodHandleClazz = Class.forName("java.lang.invoke.MethodHandle");
            _bindToMethod = methodHandleClazz.getDeclaredMethod("bindTo", Object.class);
            _invokeWithArgumentsMethod = methodHandleClazz.getDeclaredMethod("invokeWithArguments", Object[].class);
        } catch (ClassNotFoundException e) {
            // Java 6
            _bindToMethod = null;
            _invokeWithArgumentsMethod = null;
        } catch (NoSuchMethodException e) {
            // Unexpected
            _bindToMethod = null;
            _invokeWithArgumentsMethod = null;
        }
        bindToMethod = _bindToMethod;
        invokeWithArgumentsMethod = _invokeWithArgumentsMethod;
    }

    /**
     * Set the {@code accessible} flag for this reflected object to {@code true}
     * if possible. This method sets the {@code accessible} flag, as if by
     * invoking {@link AccessibleObject#setAccessible(boolean) setAccessible(true)}, and returns
     * the possibly-updated value for the {@code accessible} flag. If access
     * cannot be enabled, i.e. the checks or Java language access control cannot
     * be suppressed, this method returns {@code false} (as opposed to {@code
     * setAccessible(true)} throwing {@code InaccessibleObjectException} when
     * it fails).
     *
     * <p> This method is a no-op if the {@code accessible} flag for
     * this reflected object is {@code true}.
     *
     * <p> For example, a caller can invoke {@code trySetAccessible}
     * on a {@code Method} object for a private instance method
     * {@code p.T::privateMethod} to suppress the checks for Java language access
     * control when the {@code Method} is invoked.
     * If {@code p.T} class is in a different module to the caller and
     * package {@code p} is open to at least the caller's module,
     * the code below successfully sets the {@code accessible} flag
     * to {@code true}.
     *
     * <pre>
     * {@code
     *     p.T obj = ....;  // instance of p.T
     *     :
     *     Method m = p.T.class.getDeclaredMethod("privateMethod");
     *     if (m.trySetAccessible()) {
     *         m.invoke(obj);
     *     } else {
     *         // package p is not opened to the caller to access private member of T
     *         ...
     *     }
     * }</pre>
     *
     * <p> If there is a security manager, its {@code checkPermission} method
     * is first called with a {@code ReflectPermission("suppressAccessChecks")}
     * permission. </p>
     *
     * @return {@code true} if the {@code accessible} flag is set to {@code true};
     *         {@code false} if access cannot be enabled.
     * @throws SecurityException if the request is denied by the security manager
     * @throws NullPointerException if the specified accessible object is null
     *
     */
    public static boolean trySetAccessible(AccessibleObject accessible) throws SecurityException, NullPointerException {
        try {
            return (Boolean) AccessibleObject.class.getDeclaredMethod("trySetAccessible").invoke(accessible); // Java 9+
        } catch (NoSuchMethodException e) {
            accessible.setAccessible(true); // Java 6-8
            return true;
        } catch (IllegalAccessException e) {
            return false; // Unexpected
        } catch (InvocationTargetException e) {
            return false; // Unexpected
        }
    }

    /**
     * Allocates an instance but does not run any constructor.
     * Initializes the class if it has not yet been.
     *
     * @throws InstantiationException    if the class that declares the
     *           underlying constructor represents an abstract class.
     * @throws NullPointerException    if the specified class is null
     */
    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance(Class<T> clazz) throws InstantiationException, NullPointerException {
        return (T) unsafe.allocateInstance(clazz); // Java 6+
    }

    /**
     * Uses the constructor represented by this {@code Constructor} object to
     * create and initialize a new instance of the constructor's
     * declaring class, with the specified initialization parameters.
     * Individual parameters are automatically unwrapped to match
     * primitive formal parameters, and both primitive and reference
     * parameters are subject to method invocation conversions as necessary.
     *
     * <p>If the number of formal parameters required by the underlying constructor
     * is 0, the supplied {@code initargs} array may be of length 0 or null.
     *
     * <p>If the constructor's declaring class is an inner class in a
     * non-static context, the first argument to the constructor needs
     * to be the enclosing instance; see section 15.9.3 of
     * <cite>The Java Language Specification</cite>.
     *
     * <p>If the required access and argument checks succeed and the
     * instantiation will proceed, the constructor's declaring class
     * is initialized if it has not already been initialized.
     *
     * <p>If the constructor completes normally, returns the newly
     * created and initialized instance.
     *
     * @param args array of objects to be passed as arguments to
     * the constructor call; values of primitive types are wrapped in
     * a wrapper object of the appropriate type (e.g. a {@code float}
     * in a {@link Float Float})
     *
     * @return a new object created by calling the constructor
     * this object represents
     *
     * @throws    IllegalArgumentException  if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion; if
     *              this constructor pertains to an enum class.
     * @throws    NullPointerException if the specified constructor is null
     * @throws    InstantiationException    if the class that declares the
     *              underlying constructor represents an abstract class.
     * @throws    InvocationTargetException if the underlying constructor
     *              throws an exception.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Constructor<T> constructor, Object... args) throws InstantiationException, InvocationTargetException,
            NullPointerException, IllegalArgumentException, ExceptionInInitializerError {
        try {
            if (trySetAccessible(constructor)) return constructor.newInstance(args); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        try {
            return (T) invokeWithArgumentsMethod.invoke(unreflectConstructorMethod.invoke(lookup, constructor), (Object) args); // Java 16+
        } catch (IllegalAccessException e) {
            return null; // Unexpected
        }
    }

    private static Object checkObject(Object object, Field field) {
        // NOTE: will throw NullPointerException, as specified, if object is null
        if (!field.getDeclaringClass().isAssignableFrom(object.getClass())) {
            StringBuilder builder = new StringBuilder("Can not set ");
            builder.append(field.getType().getName())
                    .append(" field ")
                    .append(field.getDeclaringClass().getName()).append(".").append(field.getName())
                    .append(" to ");
            String attemptedType = object.getClass().getName();
            if (attemptedType.length() > 0) builder.append(attemptedType);
            else builder.append("null value");
            throw new IllegalArgumentException(builder.toString());
        }
        return object;
    }

    /**
     * Gets the value of a static or instance non-primitive field.
     *
     * @param object the object to extract the non-primitive value
     * from
     * @return the value of the non-primitive field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value is primitive.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static Object getObjectField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType().isPrimitive()) throw new IllegalArgumentException("Illegal field type; expected non-primitive");
        try {
            if (trySetAccessible(field)) return field.get(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getObject(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getObject(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code boolean} field.
     *
     * @param object the object to extract the {@code boolean} value
     * from
     * @return the value of the {@code boolean} field
     * 
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code boolean} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static boolean getBooleanField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != boolean.class) throw new IllegalArgumentException("Illegal field type; expected boolean");
        try {
            if (trySetAccessible(field)) return field.getBoolean(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getBoolean(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getBoolean(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code byte} field.
     *
     * @param object the object to extract the {@code byte} value
     * from
     * @return the value of the {@code byte} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code byte} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static byte getByteField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != byte.class) throw new IllegalArgumentException("Illegal field type; expected byte");
        try {
            if (trySetAccessible(field)) return field.getByte(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getByte(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getByte(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code char} field.
     *
     * @param object the object to extract the {@code char} value
     * from
     * @return the value of the {@code char} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code char} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static char getCharField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != char.class) throw new IllegalArgumentException("Illegal field type; expected char");
        try {
            if (trySetAccessible(field)) return field.getChar(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getChar(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getChar(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code short} field.
     *
     * @param object the object to extract the {@code short} value
     * from
     * @return the value of the {@code short} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code short} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static short getShortField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != short.class) throw new IllegalArgumentException("Illegal field type; expected short");
        try {
            if (trySetAccessible(field)) return field.getShort(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getShort(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getShort(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code int} field.
     *
     * @param object the object to extract the {@code int} value
     * from
     * @return the value of the {@code int} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code int} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static int getIntField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != int.class) throw new IllegalArgumentException("Illegal field type; expected int");
        try {
            if (trySetAccessible(field)) return field.getInt(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getInt(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getInt(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code long} field.
     *
     * @param object the object to extract the {@code long} value
     * from
     * @return the value of the {@code long} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code long} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static long getLongField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != long.class) throw new IllegalArgumentException("Illegal field type; expected long");
        try {
            if (trySetAccessible(field)) return field.getLong(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getLong(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getLong(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code float} field.
     *
     * @param object the object to extract the {@code float} value
     * from
     * @return the value of the {@code float} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code float} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static float getFloatField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != float.class) throw new IllegalArgumentException("Illegal field type; expected float");
        try {
            if (trySetAccessible(field)) return field.getFloat(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getFloat(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getFloat(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Gets the value of a static or instance {@code double} field.
     *
     * @param object the object to extract the {@code double} value
     * from
     * @return the value of the {@code double} field
     *
     * @throws    IllegalArgumentException  if the specified object is not
     *              an instance of the class or interface declaring the
     *              underlying field (or a subclass or implementor
     *              thereof), or if the field value cannot be
     *              converted to the type {@code double} by a
     *              widening conversion.
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #getField(Object, Field)
     */
    public static double getDoubleField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != double.class) throw new IllegalArgumentException("Illegal field type; expected double");
        try {
            if (trySetAccessible(field)) return field.getDouble(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        return isStatic(field.getModifiers()) ?
                unsafe.getDouble(field.getDeclaringClass(), unsafe.staticFieldOffset(field)) :
                unsafe.getDouble(checkObject(object, field), unsafe.objectFieldOffset(field));
    }

    /**
     * Returns the value of the field represented by this {@code Field}, on
     * the specified object. The value is automatically wrapped in an
     * object if it has a primitive type.
     *
     * <p>The underlying field's value is obtained as follows:
     *
     * <p>If the underlying field is a static field, the {@code object} argument
     * is ignored; it may be null.
     *
     * <p>Otherwise, the underlying field is an instance field.  If the
     * specified {@code object} argument is null, the method throws a
     * {@code NullPointerException}. If the specified object is not an
     * instance of the class or interface declaring the underlying
     * field, the method throws an {@code IllegalArgumentException}.
     *
     * <p>If this {@code Field} object is enforcing Java language access control, and
     * the underlying field is inaccessible, the method throws an
     * {@code IllegalAccessException}.
     * If the underlying field is static, the class that declared the
     * field is initialized if it has not already been initialized.
     *
     * <p>Otherwise, the value is retrieved from the underlying instance
     * or static field.  If the field has a primitive type, the value
     * is wrapped in an object before being returned, otherwise it is
     * returned as is.
     *
     * <p>If the field is hidden in the type of {@code object},
     * the field's value is obtained according to the preceding rules.
     *
     * @param object object from which the represented field's value is
     * to be extracted
     * @return the value of the represented field in object
     * {@code object}; primitive values are wrapped in an appropriate
     * object before being returned
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof).
     * @throws    NullPointerException      if the specified field is null, or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     */
    public static Object getField(Object object, Field field) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        try {
            if (trySetAccessible(field)) return field.get(object); // Java 6-15
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        Class<?> fieldType = field.getType();
        if (isStatic(field.getModifiers())) {
            if (fieldType == boolean.class) return unsafe.getBoolean(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == byte.class) return unsafe.getByte(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == char.class) return unsafe.getChar(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == short.class) return unsafe.getShort(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == int.class) return unsafe.getInt(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == long.class) return unsafe.getLong(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == float.class) return unsafe.getFloat(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else if (fieldType == double.class) return unsafe.getDouble(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
            else return unsafe.getObject(field.getDeclaringClass(), unsafe.staticFieldOffset(field));
        }
        else {
            if (fieldType == boolean.class) return unsafe.getBoolean(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == byte.class) return unsafe.getByte(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == char.class) return unsafe.getChar(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == short.class) return unsafe.getShort(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == int.class) return unsafe.getInt(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == long.class) return unsafe.getLong(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == float.class) return unsafe.getFloat(checkObject(object, field), unsafe.objectFieldOffset(field));
            else if (fieldType == double.class) return unsafe.getDouble(checkObject(object, field), unsafe.objectFieldOffset(field));
            else return unsafe.getObject(checkObject(object, field), unsafe.objectFieldOffset(field));
        }
    }

    private static String getTypeName(Class<?> clazz) {
        if (clazz.isArray()) {
            try {
                StringBuilder builder = new StringBuilder();
                Class<?> cl = clazz;
                do {
                    builder.append("[]");
                    cl = cl.getComponentType();
                } while (cl.isArray());
                return clazz.getName() + builder;
            } catch (Throwable ignored) {
            }
        }
        return clazz.getName();
    }

    /**
     * Sets the value of a field as an {@code Object} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setObjectField(Object object, Field field, Object value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (!field.getType().isAssignableFrom(value.getClass()))
            throw new IllegalArgumentException("Illegal field type; expected " + getTypeName(field.getType()));
        try {
            if (trySetAccessible(field)) {
                field.set(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putObject(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putObject(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code boolean} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setBooleanField(Object object, Field field, boolean value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != boolean.class) throw new IllegalArgumentException("Illegal field type; expected boolean");
        try {
            if (trySetAccessible(field)) {
                field.setBoolean(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putBoolean(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putBoolean(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code byte} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setByteField(Object object, Field field, byte value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != byte.class) throw new IllegalArgumentException("Illegal field type; expected byte");
        try {
            if (trySetAccessible(field)) {
                field.setByte(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putByte(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putByte(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code char} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setCharField(Object object, Field field, char value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != char.class) throw new IllegalArgumentException("Illegal field type; expected char");
        try {
            if (trySetAccessible(field)) {
                field.setChar(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putChar(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putChar(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code short} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setShortField(Object object, Field field, short value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != short.class) throw new IllegalArgumentException("Illegal field type; expected short");
        try {
            if (trySetAccessible(field)) {
                field.setShort(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putShort(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putShort(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code int} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setIntField(Object object, Field field, int value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != int.class) throw new IllegalArgumentException("Illegal field type; expected int");
        try {
            if (trySetAccessible(field)) {
                field.setInt(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putInt(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putInt(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code long} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setLongField(Object object, Field field, long value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != long.class) throw new IllegalArgumentException("Illegal field type; expected long");
        try {
            if (trySetAccessible(field)) {
                field.setLong(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putLong(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putLong(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code float} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setFloatField(Object object, Field field, float value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != float.class) throw new IllegalArgumentException("Illegal field type; expected float");
        try {
            if (trySetAccessible(field)) {
                field.setFloat(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putFloat(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putFloat(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the value of a field as a {@code double} on the specified object.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     *
     * @see #setField(Object, Field, Object)
     */
    public static void setDoubleField(Object object, Field field, double value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (field.getType() != double.class) throw new IllegalArgumentException("Illegal field type; expected double");
        try {
            if (trySetAccessible(field)) {
                field.setDouble(object, value); // Java 6-15
                return;
            }
        } catch (IllegalAccessException ignored) {
        }
        // Java 16+
        if (isStatic(field.getModifiers())) unsafe.putDouble(field.getDeclaringClass(), unsafe.staticFieldOffset(field), value);
        else unsafe.putDouble(checkObject(object, field), unsafe.objectFieldOffset(field), value);
    }

    /**
     * Sets the field represented by this {@code Field} object on the
     * specified object argument to the specified new value. The new
     * value is automatically unwrapped if the underlying field has a
     * primitive type.
     *
     * <p>The operation proceeds as follows:
     *
     * <p>If the underlying field is static, the {@code obj} argument is
     * ignored; it may be null.
     *
     * <p>Otherwise the underlying field is an instance field.  If the
     * specified object argument is null, the method throws a
     * {@code NullPointerException}.  If the specified object argument is not
     * an instance of the class or interface declaring the underlying
     * field, the method throws an {@code IllegalArgumentException}.
     *
     * <p> Setting a final field in this way
     * is meaningful only during deserialization or reconstruction of
     * instances of classes with blank final fields, before they are
     * made available for access by other parts of a program. Use in
     * any other context may have unpredictable effects, including cases
     * in which other parts of a program continue to use the original
     * value of this field.
     *
     * <p>If the underlying field is of a primitive type, an unwrapping
     * conversion is attempted to convert the new value to a value of
     * a primitive type.  If this attempt fails, the method throws an
     * {@code IllegalArgumentException}.
     *
     * <p>If, after possible unwrapping, the new value cannot be
     * converted to the type of the underlying field by an identity or
     * widening conversion, the method throws an
     * {@code IllegalArgumentException}.
     *
     * <p>If the underlying field is static, the class that declared the
     * field is initialized if it has not already been initialized.
     *
     * <p>The field is set to the possibly unwrapped and widened new value.
     *
     * <p>If the field is hidden in the type of {@code object},
     * the field's value is set according to the preceding rules.
     *
     * @param object the object whose field should be modified
     * @param value the new value for the field of {@code object}
     * being modified
     *
     * @throws    IllegalArgumentException  if the specified object is not an
     *              instance of the class or interface declaring the underlying
     *              field (or a subclass or implementor thereof),
     *              or if an unwrapping conversion fails.
     * @throws    NullPointerException      if the specified field is null or the specified object is null
     *              and the field is an instance field.
     * @throws    ExceptionInInitializerError if the initialization provoked
     *              by this method fails.
     */
    public static void setField(Object object, Field field, Object value) throws IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        Class<?> fieldType = field.getType();
        try {
            if (fieldType == boolean.class) setBooleanField(object, field, (Boolean) value);
            else if (fieldType == byte.class) setByteField(object, field, (Byte) value);
            else if (fieldType == char.class) setCharField(object, field, (Character) value);
            else if (fieldType == short.class) setShortField(object, field, (Short) value);
            else if (fieldType == int.class) setIntField(object, field, (Integer) value);
            else if (fieldType == long.class) setLongField(object, field, (Long) value);
            else if (fieldType == float.class) setFloatField(object, field, (Float) value);
            else if (fieldType == double.class) setDoubleField(object, field, (Double) value);
            else setObjectField(object, field, value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the method had a return value.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static void invokeVoidMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != void.class) throw new IllegalArgumentException("Illegal return type; expected void");
        else invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value is primitive.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static Object invokeObjectMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType().isPrimitive()) throw new IllegalArgumentException("Illegal return type; expected non-primitive");
        else return invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code boolean} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static boolean invokeBooleanMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != boolean.class) throw new IllegalArgumentException("Illegal return type; expected boolean");
        else return (Boolean) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code byte} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static byte invokeByteMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != byte.class) throw new IllegalArgumentException("Illegal return type; expected byte");
        else return (Byte) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code char} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static char invokeCharMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != char.class) throw new IllegalArgumentException("Illegal return type; expected char");
        else return (Character) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code short} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static short invokeShortMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != short.class) throw new IllegalArgumentException("Illegal return type; expected short");
        else return (Short) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code int} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static int invokeIntMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != int.class) throw new IllegalArgumentException("Illegal return type; expected int");
        else return (Integer) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code long} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static long invokeLongMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != long.class) throw new IllegalArgumentException("Illegal return type; expected long");
        else return (Long) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code float} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static float invokeFloatMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != float.class) throw new IllegalArgumentException("Illegal return type; expected float");
        else return (Float) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion;
     *              or if the returned value cannot be converted to
     *              the type {@code double} by a widening conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static double invokeDoubleMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        if (method.getReturnType() != double.class) throw new IllegalArgumentException("Illegal return type; expected double");
        else return (Double) invokeMethod(object, method, args);
    }

    /**
     * Invokes the underlying method represented by this {@code Method}
     * object, on the specified object with the specified parameters.
     * Individual parameters are automatically unwrapped to match
     * primitive formal parameters, and both primitive and reference
     * parameters are subject to method invocation conversions as
     * necessary.
     *
     * <p>If the underlying method is static, then the specified {@code object}
     * argument is ignored. It may be null.
     *
     * <p>If the number of formal parameters required by the underlying method is
     * 0, the supplied {@code args} array may be of length 0 or null.
     *
     * <p>If the underlying method is an instance method, it is invoked
     * using dynamic method lookup as documented in The Java Language
     * Specification, section 15.12.4.4; in particular,
     * overriding based on the runtime type of the target object may occur.
     *
     * <p>If the underlying method is static, the class that declared
     * the method is initialized if it has not already been initialized.
     *
     * <p>If the method completes normally, the value it returns is
     * returned to the caller of invoke; if the value has a primitive
     * type, it is first appropriately wrapped in an object. However,
     * if the value has the type of array of a primitive type, the
     * elements of the array are <i>not</i> wrapped in objects; in
     * other words, an array of primitive type is returned.  If the
     * underlying method return type is void, the invocation returns
     * null.
     *
     * @param object  the object the underlying method is invoked from
     * @param args the arguments used for the method call
     *
     * @throws    IllegalArgumentException  if the method is an
     *              instance method and the specified object argument
     *              is not an instance of the class or interface
     *              declaring the underlying method (or of a subclass
     *              or implementor thereof); if the number of actual
     *              and formal parameters differ; if an unwrapping
     *              conversion for primitive arguments fails; or if,
     *              after possible unwrapping, a parameter value
     *              cannot be converted to the corresponding formal
     *              parameter type by a method invocation conversion.
     * @throws    InvocationTargetException if the underlying method
     *              throws an exception.
     * @throws    NullPointerException      if the specified method is null,
     *              or specified object is null and the method is an instance method.
     * @throws    ExceptionInInitializerError if the initialization
     * provoked by this method fails.
     */
    public static Object invokeMethod(Object object, Method method, Object... args)
            throws InvocationTargetException, IllegalArgumentException, NullPointerException, ExceptionInInitializerError {
        try {
            if (trySetAccessible(method)) return method.invoke(object, args); // Java 6-15
            else {
                // Java 16+
                Object methodHandle = unreflectMethod.invoke(lookup, method);
                if (!isStatic(method.getModifiers())) methodHandle = bindToMethod.invoke(methodHandle, object);
                return invokeWithArgumentsMethod.invoke(methodHandle, (Object) args);
            }
        } catch (IllegalAccessException e) {
            return null; // Unexpected
        }
    }

}
