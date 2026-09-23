package com.harbourmaster;

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

public final class ApiStub {
    private ApiStub() {}

    public static <T> T of(Class<T> type, BiFunction<String, Object[], Object> methods) {
        return type.cast(
                Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == arguments[0];
                            case "toString":
                                return "Stub " + type.getSimpleName();
                            default:
                                throw new AssertionError(method.getName());
                        }
                    }
                    return methods.apply(method.getName(), arguments);
                }));
    }
}
