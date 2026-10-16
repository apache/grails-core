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
package org.grails.datastore.mapping.engine

import java.beans.PropertyDescriptor
import java.lang.reflect.Method

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import org.springframework.beans.BeanWrapper
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.core.convert.ConversionService
import org.springframework.util.ReflectionUtils

import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * Class used to access properties of an entity. Also responsible for
 * any conversion from source to target types.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@POJO
@SuppressWarnings(['rawtypes', 'unchecked'])
class BeanEntityAccess implements EntityAccess {

    private static final Set EXCLUDED_PROPERTIES = ClassPropertyFetcher.EXCLUDED_PROPERTIES

    protected Object entity
    protected BeanWrapper beanWrapper
    protected PersistentEntity persistentEntity

    BeanEntityAccess(PersistentEntity persistentEntity, Object entity) {
        this.entity = entity
        this.persistentEntity = persistentEntity
        beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity)
    }

    @Override
    Object getEntity() {
        return entity
    }

    void setConversionService(ConversionService conversionService) {
        beanWrapper.setConversionService(conversionService)
    }

    @Override
    Object getProperty(String name) {
        return beanWrapper.getPropertyValue(name)
    }

    @Override
    Object getPropertyValue(String name) {
        return beanWrapper.getPropertyValue(name)
    }

    @Override
    Class getPropertyType(String name) {
        return beanWrapper.getPropertyType(name)
    }

    @Override
    void setProperty(String name, Object value) {
        Class type = getPropertyType(name)
        if (value == null) {
            if (!type.isPrimitive()) {
                beanWrapper.setPropertyValue(name, value)
            }
        }
        else {
            beanWrapper.setPropertyValue(name, value)
        }
    }

    @Override
    Object getIdentifier() {
        String idName = getIdentifierName(persistentEntity.getMapping())
        if (idName != null) {
            return getProperty(idName)
        }
        PersistentProperty identity = persistentEntity.getIdentity()
        if (identity != null) {
            return getProperty(identity.getName())
        }
        return null
    }

    @Override
    void setIdentifier(Object id) {
        String idName = getIdentifierName(persistentEntity.getMapping())
        setProperty(idName, id)
    }

    @Override
    void setIdentifierNoConversion(Object id) {
        String idName = getIdentifierName(persistentEntity.getMapping())
        setPropertyNoConversion(idName, id)
    }

    protected String getIdentifierName(ClassMapping cm) {
        final IdentityMapping identifier = cm.getIdentifier()
        if (identifier != null && identifier.getIdentifierName() != null) {
            String[] identifierName = identifier.getIdentifierName()
            if (identifierName.length > 0) {
                return identifierName[0]
            }
        }
        return null
    }

    @Override
    String getIdentifierName() {
        return getIdentifierName(persistentEntity.getMapping())
    }

    @Override
    PersistentEntity getPersistentEntity() {
        return persistentEntity
    }

    void setPropertyNoConversion(String name, Object value) {
        final PropertyDescriptor pd = beanWrapper.getPropertyDescriptor(name)
        if (pd == null) {
            return
        }
        final Method writeMethod = pd.getWriteMethod()
        if (writeMethod != null) {
            ReflectionUtils.invokeMethod(writeMethod, beanWrapper.getWrappedInstance(), value)
        }
    }

    /**
     * Refreshes the object from entity state.
     */
    @Override
    void refresh() {
        final PropertyDescriptor[] descriptors = beanWrapper.getPropertyDescriptors()
        for (PropertyDescriptor descriptor in descriptors) {
            final String name = descriptor.getName()
            if (EXCLUDED_PROPERTIES.contains(name)) {
                continue
            }

            if (!beanWrapper.isReadableProperty(name) || !beanWrapper.isWritableProperty(name)) {
                continue
            }

            Object newValue = getProperty(name)
            setProperty(name, newValue)
        }
    }

}
