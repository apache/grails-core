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
package org.grails.orm.hibernate.access;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.codehaus.groovy.transform.trait.Traits;

import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import org.hibernate.MappingException;
import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.GetterFieldImpl;
import org.hibernate.property.access.spi.GetterMethodImpl;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.property.access.spi.Setter;
import org.hibernate.property.access.spi.SetterFieldImpl;
import org.hibernate.property.access.spi.SetterMethodImpl;

import org.springframework.util.ReflectionUtils;

import org.grails.datastore.mapping.reflect.NameUtils;

/**
 * Support reading and writing trait fields with Hibernate 5+
 *
 * @author Graeme Rocher
 * @since 6.1.3
 */
@SuppressWarnings({"rawtypes", "PMD.DataflowAnomalyAnalysis"})
public class TraitPropertyAccessStrategy implements PropertyAccessStrategy {

    public PropertyAccess buildPropertyAccess(Class containerJavaType, String propertyName) {
        return buildPropertyAccess(containerJavaType, propertyName, true);
    }

    protected String getTraitFieldName(Class traitClass, String fieldName) {
        return traitClass.getName().replace('.', '_') + "__" + fieldName;
    }

    @java.lang.Override
    public @UnknownKeyFor @NonNull @Initialized PropertyAccess buildPropertyAccess(
            java.lang.@UnknownKeyFor @NonNull @Initialized Class<@UnknownKeyFor @NonNull @Initialized ?>
                    containerJavaType,
            java.lang.@UnknownKeyFor @NonNull @Initialized String propertyName,
            @UnknownKeyFor @Initialized boolean setterRequired) {
        Method readMethod = ReflectionUtils.findMethod(containerJavaType, NameUtils.getGetterName(propertyName));
        if (readMethod == null) {
            // See https://issues.apache.org/jira/browse/GROOVY-11512
            Method booleanReadMethod =
                    ReflectionUtils.findMethod(containerJavaType, NameUtils.getGetterName(propertyName, true));
            if (booleanReadMethod != null
                    && (booleanReadMethod.getReturnType() == Boolean.class
                            || booleanReadMethod.getReturnType() == boolean.class)) {
                readMethod = booleanReadMethod;
            }
        }

        if (readMethod == null) {
            throw new IllegalStateException("TraitPropertyAccessStrategy used on property [" + propertyName
                    + "] of class ["
                    + containerJavaType.getName()
                    + "] that is not provided by a trait!");
        }

        Traits.Implemented traitImplemented = readMethod.getAnnotation(Traits.Implemented.class);
        final String traitFieldName;
        if (traitImplemented == null) {
            Traits.TraitBridge traitBridge = readMethod.getAnnotation(Traits.TraitBridge.class);
            if (traitBridge != null) {
                traitFieldName = getTraitFieldName(traitBridge.traitClass(), propertyName);
            } else {
                throw new IllegalStateException("TraitPropertyAccessStrategy used on property [" + propertyName
                        + "] of class ["
                        + containerJavaType.getName()
                        + "] that is not provided by a trait!");
            }
        } else {
            traitFieldName = getTraitFieldName(readMethod.getDeclaringClass(), propertyName);
        }

        Field field = ReflectionUtils.findField(containerJavaType, traitFieldName);
        final Getter getter;
        final Setter setter;
        if (field == null) {
            getter = new GetterMethodImpl(containerJavaType, propertyName, readMethod);
            Method writeMethod = ReflectionUtils.findMethod(
                    containerJavaType, NameUtils.getSetterName(propertyName), readMethod.getReturnType());
            if (writeMethod == null) {
                if (setterRequired) {
                    throw new MappingException("TraitPropertyAccessStrategy used on property [" + propertyName
                            + "] of class ["
                            + containerJavaType.getName()
                            + "] that has no setter!");
                }
                setter = null;
            } else {
                setter = new SetterMethodImpl(containerJavaType, propertyName, writeMethod);
            }
        } else {

            getter = new GetterFieldImpl(containerJavaType, propertyName, field);
            setter = new SetterFieldImpl(containerJavaType, propertyName, field);
        }

        return new PropertyAccess() {
            @Override
            public PropertyAccessStrategy getPropertyAccessStrategy() {
                return TraitPropertyAccessStrategy.this;
            }

            @Override
            public Getter getGetter() {
                return getter;
            }

            @Override
            public Setter getSetter() {
                return setter;
            }
        };
    }
}
