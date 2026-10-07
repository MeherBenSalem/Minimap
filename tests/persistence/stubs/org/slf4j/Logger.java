package org.slf4j;
public interface Logger { default void error(String message, Object... values) {} default void warn(String message, Object... values) {} }
