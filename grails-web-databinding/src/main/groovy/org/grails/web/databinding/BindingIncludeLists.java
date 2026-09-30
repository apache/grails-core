/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.databinding;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.util.Environment;
import grails.util.Holders;
import org.grails.config.NavigableMap;

/**
 * The properties data binding binds on a type: the include list Grails generates for a domain class
 * or a command object, and under {@code grails.databinding.denyByDefault} the properties constrained
 * {@code bindable: true}. Everything it is read from belongs to the class, so it is read from the
 * class, without an instance, and kept for each class.
 *
 * <p>Internal to Grails: {@code grails.web.databinding.DataBindingUtils} binds with it, and other
 * Grails modules read it through it.</p>
 *
 * @since 8.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class BindingIncludeLists {

    private static final Logger LOG = LoggerFactory.getLogger(BindingIncludeLists.class);
    private static final List NO_BINDING_INCLUDE_LIST = new NoBindingIncludeList();
    private static final Map<Class, List> CLASS_TO_BINDING_INCLUDE_LIST = new ConcurrentHashMap<>();
    private static final Map<Class, List> CLASS_TO_LEGACY_BINDING_INCLUDE_LIST = new ConcurrentHashMap<>();

    private static final class NoBindingIncludeList extends ArrayList {
    }

    /**
     * An include list that holds what Grails generated, which binding treats as the complete list of
     * what may be bound.
     */
    private static final class GeneratedBindingIncludeList extends ArrayList {
        private GeneratedBindingIncludeList(final Collection values) {
            super(values);
        }
    }

    private BindingIncludeLists() {
    }

    /**
     * The include list data binding binds a type with.
     *
     * @param type the type bound
     * @param denyByDefault whether {@code grails.databinding.denyByDefault} is enabled
     * @return the names, patterns included, of the properties bound, or {@code null} where binding
     * the type is not restricted
     */
    public static List forType(final Class type, final boolean denyByDefault) {
        final Map<Class, List> includeListCache = denyByDefault ?
                CLASS_TO_BINDING_INCLUDE_LIST : CLASS_TO_LEGACY_BINDING_INCLUDE_LIST;
        List includeList = null;
        try {
            if (includeListCache.containsKey(type)) {
                includeList = includeListCache.get(type);
                if (includeList == NO_BINDING_INCLUDE_LIST) {
                    includeList = null;
                }
            } else {
                // Resolve the runtime-derived bindable names only on a cache miss - this walks the
                // target's constraints and would otherwise run on every bind of a cached class.
                final List runtimeBindableNames = denyByDefault ? bindablePropertyNames(type) : null;
                includeList = runtimeBindableNames;
                final Field legacyWhiteListField = getField(type, DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST);
                final Field defaultWhiteListField = denyByDefault ?
                        getPairedField(type, DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST,
                                DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST) :
                        getField(type, DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST);
                if (!denyByDefault) {
                    includeList = getStaticListFieldValue(legacyWhiteListField);
                    if (includeList == null) {
                        includeList = getStaticListFieldValue(defaultWhiteListField);
                    }
                } else if (defaultWhiteListField != null) {
                    final List generatedIncludeList = getStaticListFieldValue(defaultWhiteListField);
                    final Collection combinedIncludeList = new LinkedHashSet();
                    if (generatedIncludeList != null) {
                        combinedIncludeList.addAll(generatedIncludeList);
                    }
                    if (runtimeBindableNames != null) {
                        combinedIncludeList.addAll(runtimeBindableNames);
                    }
                    includeList = new ArrayList(combinedIncludeList);
                }
                if (denyByDefault) {
                    includeList = asGenerated(includeList);
                }
                if (!Environment.getCurrent().isReloadEnabled()) {
                    includeListCache.put(type, includeList == null ? NO_BINDING_INCLUDE_LIST : includeList);
                }
            }
        } catch (Exception e) {
        }
        if (denyByDefault) {
            includeList = asGenerated(includeList);
        }
        return includeList;
    }

    /**
     * The names of the properties data binding binds on a type, as the application is configured
     * to bind, without the patterns binding matches nested properties with.
     *
     * @return the names, or {@code null} where binding the type is not restricted
     */
    public static List<String> propertyNames(final Class type) {
        final List includeList = forType(type, isDenyByDefaultEnabled());
        if (includeList == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (Object name : includeList) {
            if (name != null && !DefaultASTDatabindingHelper.NO_BINDABLE_PROPERTIES.equals(name)) {
                names.add(name.toString());
            }
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * Whether {@code grails.databinding.denyByDefault} is enabled in the running application.
     */
    public static boolean isDenyByDefaultEnabled() {
        GrailsApplication application = Holders.findApplication();
        if (application != null) {
            return resolveDenyByDefault(
                    application.getConfig().getProperty(Settings.DATABINDING_DENY_BY_DEFAULT, Object.class, null));
        }
        return resolveDenyByDefault(Holders.getFlatConfig().get(Settings.DATABINDING_DENY_BY_DEFAULT));
    }

    /**
     * Resolves the configured value of {@code grails.databinding.denyByDefault} against the permissive default.
     * <p>
     * The raw value must be resolved here rather than through a typed {@code Boolean} config lookup:
     * a config value that converts to {@code Boolean.FALSE} is discarded in favour of the supplied
     * default, which would silently ignore an explicit value from
     * any string-valued source such as a properties file, a system property or an environment variable.
     * <p>
     * A navigable config answers an absent key with a placeholder object rather than {@code null}, so
     * only a genuinely absent key may fall back to the permissive default. Any other unrecognised value
     * fails closed, because this switch governs mass-assignment protection.
     *
     * @param value the raw configured value, which may be {@code null} or an absent-key placeholder
     * @return true when secure deny-by-default binding applies
     */
    public static boolean resolveDenyByDefault(final Object value) {
        if (value == null || value instanceof NavigableMap.NullSafeNavigator) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof CharSequence) {
            final String configuredValue = value.toString().trim();
            if ("true".equalsIgnoreCase(configuredValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(configuredValue)) {
                return false;
            }
        }
        LOG.warn("Unrecognised value [{}] for configuration property [{}]; secure data binding will be enabled.",
                value, Settings.DATABINDING_DENY_BY_DEFAULT);
        return true;
    }

    /**
     * Forgets the include lists read, so they are read again, as when the classes are reloaded.
     */
    public static void clearCaches() {
        CLASS_TO_BINDING_INCLUDE_LIST.clear();
        CLASS_TO_LEGACY_BINDING_INCLUDE_LIST.clear();
    }

    public static List asGenerated(final List includeList) {
        if (includeList instanceof GeneratedBindingIncludeList) {
            return includeList;
        }
        final Collection values = includeList == null || includeList.isEmpty() ?
                Collections.singletonList(DefaultASTDatabindingHelper.NO_BINDABLE_PROPERTIES) : includeList;
        return new GeneratedBindingIncludeList(values);
    }

    public static boolean isGenerated(final List includeList) {
        return includeList instanceof GeneratedBindingIncludeList;
    }

    /**
     * The properties a type constrains {@code bindable: true}.
     */
    public static List bindablePropertyNames(final Class type) {
        return propertyNamesWithBindableValue(constrainedProperties(type), Boolean.TRUE);
    }

    public static List propertyNamesWithBindableValue(final Map constrainedProperties, final Boolean bindableValue) {
        if (constrainedProperties == null || constrainedProperties.isEmpty()) {
            return Collections.emptyList();
        }
        final List propertyNames = new ArrayList();
        for (Object entryObject : constrainedProperties.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            if (bindableValue.equals(bindableConstraintValue(entry.getValue()))) {
                String propertyName = String.valueOf(entry.getKey());
                propertyNames.add(propertyName);
                if (Boolean.TRUE.equals(bindableValue) && !isSimpleType(constrainedPropertyType(entry.getValue()))) {
                    propertyNames.add(propertyName + "_*");
                    propertyNames.add(propertyName + ".*");
                }
            }
        }
        return propertyNames;
    }

    /**
     * The constraints a type declares: through the {@code constraintsMap} a validateable type and a
     * domain class have, or else evaluated from the class.
     */
    public static Map constrainedProperties(final Class type) {
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        try {
            Object constrainedProperties = metaClass.invokeStaticMethod(type, "getConstraintsMap", new Object[0]);
            if (constrainedProperties instanceof Map) {
                return (Map) constrainedProperties;
            }
        } catch (Exception ignored) {
        }
        try {
            Object constrainedProperties = metaClass.getProperty(type, "constraints");
            if (constrainedProperties instanceof Map) {
                return (Map) constrainedProperties;
            }
        } catch (Exception ignored) {
        }
        try {
            Map constrainedProperties = evaluateConstrainedProperties(type);
            if (constrainedProperties != null) {
                return constrainedProperties;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    public static Map evaluateConstrainedProperties(final Class type) {
        try {
            Class<?> validationSupport = Class.forName("org.grails.web.plugins.support.ValidationSupport");
            Object constrainedProperties = validationSupport.getMethod("getConstrainedPropertiesForClass", Class.class, boolean.class).invoke(null, type, false);
            if (constrainedProperties instanceof Map) {
                return (Map) constrainedProperties;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }

    public static Object bindableConstraintValue(final Object constrainedProperty) {
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(constrainedProperty.getClass());
        try {
            Object value = metaClass.invokeMethod(constrainedProperty, "getMetaConstraintValue", new Object[] { DefaultASTDatabindingHelper.BINDABLE_CONSTRAINT_NAME });
            if (value != null) {
                return value;
            }
        } catch (Exception ignored) {
        }
        try {
            Object metaConstraints = metaClass.getProperty(constrainedProperty, "metaConstraints");
            if (metaConstraints instanceof Map) {
                return ((Map) metaConstraints).get(DefaultASTDatabindingHelper.BINDABLE_CONSTRAINT_NAME);
            }
        } catch (Exception ignored) {
        }
        try {
            Object delegate = metaClass.getProperty(constrainedProperty, "property");
            if (delegate != null && delegate != constrainedProperty) {
                return bindableConstraintValue(delegate);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Class constrainedPropertyType(final Object constrainedProperty) {
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(constrainedProperty.getClass());
        try {
            Object propertyType = metaClass.invokeMethod(constrainedProperty, "getPropertyType", new Object[0]);
            if (propertyType instanceof Class) {
                return (Class) propertyType;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isSimpleType(final Class propertyType) {
        return propertyType != null && (propertyType.isPrimitive() || String.class.equals(propertyType) ||
                Boolean.class.equals(propertyType) || Character.class.equals(propertyType) || Number.class.isAssignableFrom(propertyType) ||
                BigInteger.class.equals(propertyType) || BigDecimal.class.equals(propertyType) || URL.class.equals(propertyType));
    }

    private static Field getField(final Class objectClass, final String fieldName) {
        Class currentClass = objectClass;
        while (currentClass != null) {
            final Field field = getPublicDeclaredField(currentClass, fieldName);
            if (field != null) {
                return field;
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static Field getPairedField(final Class objectClass, final String fieldName, final String pairedFieldName) {
        Class currentClass = objectClass;
        while (currentClass != null) {
            final Field field = getPublicDeclaredField(currentClass, fieldName);
            final Field pairedField = getPublicDeclaredField(currentClass, pairedFieldName);
            if (field != null && pairedField != null) {
                return field;
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static Field getPublicDeclaredField(final Class objectClass, final String fieldName) {
        try {
            final Field field = objectClass.getDeclaredField(fieldName);
            return Modifier.isPublic(field.getModifiers()) ? field : null;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static List getStaticListFieldValue(final Field field) throws IllegalAccessException {
        if (field != null && (field.getModifiers() & Modifier.STATIC) != 0) {
            final Object value = field.get(null);
            if (value instanceof List) {
                return (List) value;
            }
        }
        return null;
    }
}
