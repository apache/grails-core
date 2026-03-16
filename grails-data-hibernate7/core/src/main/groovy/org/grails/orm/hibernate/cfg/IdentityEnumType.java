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
package org.grails.orm.hibernate.cfg;

import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.persistence.AttributeConverter;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.type.AbstractStandardBasicType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate Usertype that enum values by their ID.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 * @since 1.1
 */
public class IdentityEnumType implements UserType, ParameterizedType, Serializable {

    @Serial
    private static final long serialVersionUID = -6625622185856547501L;

    private static final Logger LOG = LoggerFactory.getLogger(IdentityEnumType.class);

    private static TypeConfiguration typeConfiguration = new TypeConfiguration();
    public static final String ENUM_ID_ACCESSOR = "getId";

    public static final String PARAM_ENUM_CLASS = "enumClass";

    private static final Map<Class<? extends Enum<?>>, BidiEnumMap> ENUM_MAPPINGS = new HashMap<>();
    protected Class<? extends Enum<?>> enumClass;
    protected BidiEnumMap bidiMap;
    protected AbstractStandardBasicType<?> type;
    protected int[] sqlTypes;

    public static BidiEnumMap getBidiEnumMap(Class<? extends Enum<?>> cls)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BidiEnumMap m = ENUM_MAPPINGS.get(cls);
        if (m == null) {
            synchronized (ENUM_MAPPINGS) {
                if (!ENUM_MAPPINGS.containsKey(cls)) {
                    m = new BidiEnumMap(cls);
                    ENUM_MAPPINGS.put(cls, m);
                } else {
                    m = ENUM_MAPPINGS.get(cls);
                }
            }
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public void setParameterValues(Properties properties) {
        try {
            enumClass = (Class<? extends Enum<?>>)
                    Thread.currentThread().getContextClassLoader().loadClass((String) properties.get(PARAM_ENUM_CLASS));
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Building ID-mapping for Enum Class %s", enumClass.getName()));
            }
            bidiMap = getBidiEnumMap(enumClass);
            type = (AbstractStandardBasicType<?>)
                    typeConfiguration.getBasicTypeRegistry().getRegisteredType(bidiMap.keyType.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Mapped Basic Type is %s", type));
            }
            sqlTypes = type.getSqlTypeCodes(null);
        } catch (Exception e) {
            throw new MappingException("Error mapping Enum Class using IdentifierEnumType", e);
        }
    }

    public int[] getSqlTypes() {
        return sqlTypes;
    }

    @Override
    public int getSqlType() {
        return 0;
    }

    public Class<?> returnedClass() {
        return enumClass;
    }

    @Override
    public boolean equals(Object o1, Object o2) throws HibernateException {
        return java.util.Objects.equals(o1, o2);
    }

    @Override
    public int hashCode(Object o) throws HibernateException {
        return o.hashCode();
    }

    public Object deepCopy(Object o) throws HibernateException {
        return o;
    }

    public boolean isMutable() {
        return false;
    }

    public Serializable disassemble(Object o) throws HibernateException {
        return (Serializable) o;
    }

    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return cached;
    }

    public Object replace(Object orig, Object target, Object owner) throws HibernateException {
        return orig;
    }

    public long getDefaultSqlLength() {
        return UserType.super.getDefaultSqlLength();
    }

    public int getDefaultSqlPrecision() {
        return UserType.super.getDefaultSqlPrecision();
    }

    public int getDefaultSqlScale() {
        return UserType.super.getDefaultSqlScale();
    }

    @Override
    public AttributeConverter getValueConverter() {
        return UserType.super.getValueConverter();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class BidiEnumMap implements Serializable {

        @Serial
        private static final long serialVersionUID = 3325751131102095834L;

        private final Map enumToKey;
        private final Map keytoEnum;
        private final Class keyType;

        private BidiEnumMap(Class<? extends Enum> enumClass)
                throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Building Bidirectional Enum Map...");
            }

            EnumMap enumToKey = new EnumMap(enumClass);
            HashMap keytoEnum = new HashMap();

            Method idAccessor = enumClass.getMethod(ENUM_ID_ACCESSOR);

            keyType = idAccessor.getReturnType();

            Method valuesAccessor = enumClass.getMethod("values");
            Object[] values = (Object[]) valuesAccessor.invoke(enumClass);

            for (Object value : values) {
                Object id = idAccessor.invoke(value);
                enumToKey.put((Enum) value, id);
                if (keytoEnum.containsKey(id)) {
                    LOG.warn(String.format("Duplicate Enum ID '%s' detected for Enum %s!", id, enumClass.getName()));
                }
                keytoEnum.put(id, value);
            }

            this.enumToKey = Collections.unmodifiableMap(enumToKey);
            this.keytoEnum = Collections.unmodifiableMap(keytoEnum);
        }

        public Object getEnumValue(Object id) {
            return keytoEnum.get(id);
        }

        public Object getKey(Object enumValue) {
            return enumToKey.get(enumValue);
        }
    }
}
