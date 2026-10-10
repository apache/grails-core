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
package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import org.hibernate.HibernateException
import org.hibernate.MappingException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.descriptor.jdbc.BigIntJdbcType
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType
import org.hibernate.type.descriptor.jdbc.JdbcType
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType
import org.hibernate.type.spi.TypeConfiguration
import org.hibernate.usertype.ParameterizedType
import org.hibernate.usertype.UserType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

/**
 * Hibernate 7 UserType to map Enums to their "id" value.
 */
@CompileStatic
class IdentityEnumType implements UserType<Object>, ParameterizedType, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityEnumType)
    private static final TypeConfiguration typeConfiguration = new TypeConfiguration()

    static final String ENUM_ID_ACCESSOR = 'getId'
    static final String PARAM_ENUM_CLASS = 'enumClass'

    private static final Map<Class<? extends Enum<?>>, BidiEnumMap> ENUM_MAPPINGS = new HashMap<>()

    protected Class<? extends Enum<?>> enumClass
    protected BidiEnumMap bidiMap
    protected JavaType<Object> javaType
    protected JdbcType jdbcType
    protected int sqlType

    static BidiEnumMap getBidiEnumMap(Class<? extends Enum<?>> cls) {
        BidiEnumMap m = ENUM_MAPPINGS.get(cls)
        if (m == null) {
            synchronized (ENUM_MAPPINGS) {
                m = ENUM_MAPPINGS.get(cls)
                if (m == null) {
                    try {
                        m = new BidiEnumMap(cls)
                        ENUM_MAPPINGS.put(cls, m)
                    }
                    catch (Exception e) {
                        throw new HibernateException("Error building BidiEnumMap for ${cls.name}", e)
                    }
                }
            }
        }
        return m
    }

    @Override
    void setParameterValues(Properties parameters) {
        String enumClassName = parameters.getProperty(PARAM_ENUM_CLASS)
        if (enumClassName != null) {
            try {
                enumClass = (Class<? extends Enum<?>>) Thread.currentThread().contextClassLoader.loadClass(enumClassName)
            }
            catch (ClassNotFoundException e) {
                throw new MappingException("Enum class not found: ${enumClassName}", e)
            }
        }

        if (enumClass == null) {
            // Fallback for some Grails versions
            Object enumClassAttr = parameters.get(PARAM_ENUM_CLASS)
            if (enumClassAttr instanceof Class) {
                enumClass = (Class<? extends Enum<?>>) enumClassAttr
            }
        }

        if (enumClass == null) {
            throw new MappingException('IdentityEnumType: enumClass parameter is required')
        }

        bidiMap = getBidiEnumMap(enumClass)
        javaType = (JavaType<Object>) typeConfiguration.javaTypeRegistry.getDescriptor(bidiMap.keyType)

        // Safely determine JdbcType without triggering dialect resolution if possible
        if (bidiMap.keyType == String) {
            jdbcType = VarcharJdbcType.INSTANCE
            sqlType = Types.VARCHAR
        }
        else if (bidiMap.keyType == Integer || bidiMap.keyType == int.class) {
            jdbcType = IntegerJdbcType.INSTANCE
            sqlType = Types.INTEGER
        }
        else if (bidiMap.keyType == Long || bidiMap.keyType == long.class) {
            jdbcType = BigIntJdbcType.INSTANCE
            sqlType = Types.BIGINT
        }
        else {
            jdbcType = VarcharJdbcType.INSTANCE
            sqlType = Types.VARCHAR
        }
    }

    @Override
    int getSqlType() {
        return sqlType
    }

    @Override
    Class<Object> returnedClass() {
        return (Class) enumClass
    }

    @Override
    boolean equals(Object x, Object y) throws HibernateException {
        return x == y || (x != null && y != null && x.equals(y))
    }

    @Override
    int hashCode(Object x) throws HibernateException {
        return x == null ? 0 : x.hashCode()
    }

    @Override
    Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return nullSafeGet(rs, position, (WrapperOptions) session)
    }

    @Override
    Object nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        Object id = jdbcType.getExtractor(javaType).extract(rs, position, options)
        return id == null ? null : bidiMap.getEnumValue(id)
    }

    @Override
    void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {
        nullSafeSet(st, value, index, (WrapperOptions) session)
    }

    @Override
    void nullSafeSet(PreparedStatement st, Object value, int index, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(index, sqlType)
        }
        else {
            Object id = (value instanceof Enum) ? bidiMap.getKey(value) : value
            jdbcType.getBinder(javaType).bind(st, id, index, options)
        }
    }

    @Override
    Object deepCopy(Object value) throws HibernateException {
        return value
    }

    @Override
    boolean isMutable() {
        return false
    }

    @Override
    Serializable disassemble(Object value) throws HibernateException {
        return (Serializable) value
    }

    @Override
    Object assemble(Serializable cached, Object owner) throws HibernateException {
        return cached
    }

    @Override
    Object replace(Object original, Object target, Object owner) throws HibernateException {
        return original
    }

    static class BidiEnumMap implements Serializable {

        private final Map enumToKey
        private final Map keyToEnum
        private final Class keyType

        BidiEnumMap(Class<? extends Enum<?>> enumClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            EnumMap enumToKey = new EnumMap(enumClass)
            HashMap keyToEnum = new HashMap()

            Method idAccessor = enumClass.getMethod(ENUM_ID_ACCESSOR)
            keyType = idAccessor.returnType

            Method valuesAccessor = enumClass.getMethod('values')
            Object[] values = (Object[]) valuesAccessor.invoke(enumClass)

            for (Object value : values) {
                Object id = idAccessor.invoke(value)
                enumToKey.put((Enum) value, id)
                if (keyToEnum.containsKey(id)) {
                    LOG.warn("Duplicate Enum ID '{}' detected for Enum {}!", id, enumClass.name)
                }
                keyToEnum.put(id, value)
            }

            this.enumToKey = Collections.unmodifiableMap(enumToKey)
            this.keyToEnum = Collections.unmodifiableMap(keyToEnum)
        }

        Object getEnumValue(Object id) {
            return keyToEnum.get(id)
        }

        Object getKey(Object enumValue) {
            return enumToKey.get(enumValue)
        }

    }

}
