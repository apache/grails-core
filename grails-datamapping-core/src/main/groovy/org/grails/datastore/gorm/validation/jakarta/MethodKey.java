package org.grails.datastore.gorm.validation.jakarta;

import java.util.Arrays;

/**
 * A method key used to store information about a method
 *
 * @author Graeme Rocher
 * @since 6.1
 */
class MethodKey {
    private final String name;
    private final Class[] parameterTypes;

    public MethodKey(String name, Class[] parameterTypes) {
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodKey methodKey = (MethodKey) o;

        if (name != null ? !name.equals(methodKey.name) : methodKey.name != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(parameterTypes, methodKey.parameterTypes);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(parameterTypes);
        return result;
    }
}
