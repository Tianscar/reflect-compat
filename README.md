# Java Reflection Compatible Layer
Compatible layer for `java.lang.reflect` API which could run on Java 6+ (which meant fully support Android) and bypass the [strong encapsulation](https://dev.java/learn/modules/strong-encapsulation/) in Java 16+.

## Implementation details
### Field access
The strong encapsulation has no effect for `sun.misc.Unsafe`, I use this API to access fields, it works perfectly.  
### Method invocation
Since Java 7+, `java.lang.invoke` API has been added to the JRE, which could be a replacement for `java.lang.reflect` API.  
It has an internal field: `java.lang.invoke.MethodHandles$Lookup.IMPL_LOOKUP`, which marked as "trusted", that could invoke any method without accessibility check.   
After obtain this field using `sun.misc.Unsafe`, I use this field to invoke methods, it also works perfectly.

## Comparison
[Narcissus](https://github.com/toolfactory/narcissus) is also an open-source, MIT-licensed library to bypass the strong encapsulation, supports Java 7+, depends on JNI.  
Compare to Narcissus, this library is pure Java, means you don't need to compile and load the JNI libraries for a new platform, and supports any Java 6+ compatible runtime environment.

## Usage
Just copy the [source code](/src/main/java/com/tianscar/util/reflect/Reflects.java) to your project and use the API.

[JavaDoc](https://docs.tianscar.com/reflect-compat)  
[Examples](/src/test/java/com/tianscar/util/reflect/test/ReflectsTest.java)

## License
[MIT](/LICENSE)
