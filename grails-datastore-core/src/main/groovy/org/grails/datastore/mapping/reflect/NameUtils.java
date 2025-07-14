/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.reflect;

import org.codehaus.groovy.runtime.MetaClassHelper;
import org.grails.datastore.mapping.model.config.GormProperties;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class NameUtils {

    public static final String DOLLAR_SEPARATOR = "$";
    private static final String PROPERTY_SET_PREFIX = "set";
    private static final String PROPERTY_GET_PREFIX = "get";
    private static final String PROPERTY_IS_PREFIX = "is";
    private static final Set<String> CONFIGURATIONAL_PROPERTIES;

    static {
        Set<String> configurational = new HashSet<String>(Arrays.asList(
                GormProperties.META_CLASS,
                GormProperties.CLASS,
                GormProperties.TRANSIENT,
                GormProperties.ATTACHED,
                GormProperties.DIRTY,
                GormProperties.DIRTY_PROPERTY_NAMES,
                GormProperties.HAS_MANY,
                GormProperties.CONSTRAINTS,
                GormProperties.MAPPING_STRATEGY,
                GormProperties.MAPPED_BY,
                GormProperties.BELONGS_TO,
                GormProperties.ERRORS,
                "transactionManager",
                "dataSource",
                "sessionFactory",
                "messageSource",
                "applicationContext",
                "properties"));
        CONFIGURATIONAL_PROPERTIES = Collections.unmodifiableSet(configurational);
    }

    public static boolean isConfigurational(String name) {
        return CONFIGURATIONAL_PROPERTIES.contains(name);
    }

    public static boolean isNotConfigurational(String name) {
        return !isConfigurational(name);
    }

    /**
     * Retrieves the name of a setter for the specified property name
     *
     * @param propertyName The property name
     * @return The setter equivalent
     */
    public static String getSetterName(String propertyName) {
        return PROPERTY_SET_PREFIX + capitalize(propertyName);
    }

    /**
     * Retrieves the name of a setter for the specified property name
     *
     * @param propertyName The property name
     * @return The getter equivalent
     */
    public static String getGetterName(String propertyName) {
        return getGetterName(propertyName, false);
    }

    /**
     * Retrieves the name of a setter for the specified property name
     *
     * @param propertyName     The property name
     * @param useBooleanPrefix true if property is type of boolean
     * @return The getter equivalent
     */
    public static String getGetterName(String propertyName, boolean useBooleanPrefix) {
        String prefix = useBooleanPrefix ? PROPERTY_IS_PREFIX : PROPERTY_GET_PREFIX;
        return prefix + capitalize(propertyName);
    }

    /**
     * Get the class name, taking into account proxies
     *
     * @param clazz The class
     * @return The class name
     */
    public static String getClassName(Class clazz) {
        final String sn = clazz.getSimpleName();
        if (sn.contains(DOLLAR_SEPARATOR)) {
            return clazz.getSuperclass().getName();
        }
        return clazz.getName();
    }

    /**
     * Returns the property name for a getter or setter
     *
     * @param getterOrSetterName The getter or setter name
     * @return The property name
     */
    public static String getPropertyNameForGetterOrSetter(String getterOrSetterName) {
        if (getterOrSetterName == null || getterOrSetterName.length() == 0) {
            return null;
        }

        if (getterOrSetterName.startsWith(PROPERTY_GET_PREFIX) || getterOrSetterName.startsWith(PROPERTY_SET_PREFIX)) {
            return decapitalize(getterOrSetterName.substring(3));
        } else if (getterOrSetterName.startsWith(PROPERTY_IS_PREFIX)) {
            return decapitalize(getterOrSetterName.substring(2));
        }
        return null;
    }

    /**
     * Converts class name to property name using JavaBean decapitalization
     *
     * @param name The class name
     * @return The decapitalized name
     */
    public static String decapitalize(String name) {
        return Introspector.decapitalize(name);
    }

    /**
     * Transforms the first character of a string into a lowercase letter
     *
     * @param name String to be transformed
     * @return Original string with the first char as a lowercase letter
     */
    public static String decapitalizeFirstChar(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Converts a property name to class name according to the JavaBean convention
     *
     * @param name The property name
     * @return The class name
     */
    public static String capitalize(String name) {
        return MetaClassHelper.capitalize(name);
    }
}
