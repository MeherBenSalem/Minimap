package dev.nightbeam.odysseymap.config;

import java.util.function.Consumer;

public class ConfigValue<T> {
    private final T defaultValue;
    private T value;
    private final String key;
    private final String comment;
    private final Consumer<T> validator;

    public ConfigValue(String key, String comment, T defaultValue) {
        this(key, comment, defaultValue, null);
    }

    public ConfigValue(String key, String comment, T defaultValue, Consumer<T> validator) {
        this.key = key;
        this.comment = comment;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.validator = validator;
    }

    public T get() {
        return value;
    }

    public void set(T val) {
        if (validator != null) {
            validator.accept(val);
        }
        this.value = val;
    }

    public T getDefault() {
        return defaultValue;
    }

    public String getKey() {
        return key;
    }

    public String getComment() {
        return comment;
    }

    @SuppressWarnings("unchecked")
    public void setFromObject(Object obj) {
        if (obj == null) {
            this.value = defaultValue;
            return;
        }
        if (value instanceof Boolean) {
            this.value = (T) Boolean.valueOf(obj.toString());
        } else if (value instanceof Integer) {
            this.value = (T) Integer.valueOf(Double.valueOf(obj.toString()).intValue());
        } else if (value instanceof Double) {
            this.value = (T) Double.valueOf(obj.toString());
        } else if (value instanceof String) {
            this.value = (T) obj.toString();
        } else if (value instanceof Enum && obj instanceof String s) {
            Class<? extends Enum> clazz = ((Enum<?>) value).getDeclaringClass();
            for (var e : clazz.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(s)) {
                    this.value = (T) e;
                    return;
                }
            }
            this.value = defaultValue;
        }
    }
}
