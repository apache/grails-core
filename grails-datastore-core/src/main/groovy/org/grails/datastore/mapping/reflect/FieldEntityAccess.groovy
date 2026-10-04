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
package org.grails.datastore.mapping.reflect

import java.beans.PropertyDescriptor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.stc.POJO
import org.codehaus.groovy.transform.trait.Traits
import org.springframework.cglib.reflect.FastClass
import org.springframework.core.convert.ConversionException
import org.springframework.core.convert.ConversionService
import org.springframework.util.ReflectionUtils

import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.reflect.EntityReflector.PropertyReader
import org.grails.datastore.mapping.reflect.EntityReflector.PropertyWriter

/**
 * Uses field reflection or CGlib to improve performance
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
@POJO
class FieldEntityAccess implements EntityAccess {

    private static final Map<String, EntityReflector> REFLECTORS = new ConcurrentHashMap<>()

    private final PersistentEntity persistentEntity
    private final Object entity
    private final ConversionService conversionService
    private final EntityReflector reflector

    FieldEntityAccess(PersistentEntity persistentEntity, Object entity, ConversionService conversionService) {
        this.persistentEntity = persistentEntity
        this.entity = entity
        this.conversionService = conversionService
        this.reflector = getOrIntializeReflector(persistentEntity)
    }

    static void clearReflectors() {
        REFLECTORS.clear()
    }

    static EntityReflector getOrIntializeReflector(PersistentEntity persistentEntity) {
        String entityName = persistentEntity.getName()
        EntityReflector entityReflector = REFLECTORS.get(entityName)
        if (entityReflector == null) {
            entityReflector = new FieldEntityReflector(persistentEntity)
            REFLECTORS.put(entityName, entityReflector)
        }
        return entityReflector
    }

    static EntityReflector getReflector(String name) {
        return REFLECTORS.get(name)
    }

    @Override
    Object getEntity() {
        return entity
    }

    @Override
    Object getProperty(String name) {
        Object object = unwrapIfProxy(persistentEntity, entity)
        return reflector.getProperty(object, name)
    }

    @Override
    Object getPropertyValue(String name) {
        return getProperty(name)
    }

    @Override
    Class getPropertyType(String name) {
        PersistentProperty property = persistentEntity.getPropertyByName(name)
        if (property != null) {
            return property.getType()
        }
        return null
    }

    @Override
    void setProperty(String name, Object value) {
        PropertyWriter writer = reflector.getPropertyWriter(name)
        Object converted
        try {
            converted = conversionService.convert(value, writer.propertyType())
        }
        catch (ConversionException e) {
            throw new IllegalArgumentException('Cannot assign value [' + value + '] to property [' + name + '] of type [' + writer.propertyType().getName() + '] of class [' + persistentEntity.getName() + ']. The value could not be converted to the appropriate type: ' + e.getMessage(), e)
        }
        catch (Exception e) {
            throw new IllegalArgumentException('Cannot assign value [' + value + '] to property [' + name + '] of type [' + writer.propertyType().getName() + '] of class [' + persistentEntity.getName() + ']. The value is not an acceptable type: ' + e.getMessage(), e)
        }
        writer.write(entity, converted)
    }

    @Override
    Object getIdentifier() {
        return reflector.getIdentifier(entity)
    }

    @Override
    void setIdentifier(Object id) {
        Object converted
        try {
            converted = conversionService.convert(id, reflector.identifierType())
        }
        catch (ConversionException e) {
            throw new IllegalArgumentException('Cannot assign identifier [' + id + '] to property [' + reflector.getIdentifierName() + '] of type [' + reflector.identifierType().getName() + '] of class [' + persistentEntity.getName() + ']. The value could not be converted to the appropriate type: ' + e.getMessage(), e)
        }
        catch (Exception e) {
            throw new IllegalArgumentException('Cannot assign identifier [' + id + '] to property [' + reflector.getIdentifierName() + '] of type [' + reflector.identifierType().getName() + '] of class [' + persistentEntity.getName() + ']. The identifier is not an compatible type: ' + e.getMessage(), e)
        }
        reflector.setIdentifier(entity, converted)
    }

    @Override
    void setIdentifierNoConversion(Object id) {
        try {
            reflector.setIdentifier(entity, id)
        }
        catch (Exception e) {
            throw new IllegalArgumentException('Cannot assign identifier [' + id + '] to property [' + reflector.getIdentifierName() + '] of type [' + reflector.identifierType().getName() + '] of class [' + persistentEntity.getName() + ']. The identifier is not an compatible type: ' + e.getMessage(), e)
        }
    }

    @Override
    String getIdentifierName() {
        return reflector.getIdentifierName()
    }

    @Override
    PersistentEntity getPersistentEntity() {
        return persistentEntity
    }

    @Override
    void refresh() {
        // no-op
    }

    @Override
    void setPropertyNoConversion(String name, Object value) {
        try {
            reflector.setProperty(entity, name, value)
        }
        catch (Exception e) {
            String valueType = value != null ? value.getClass().getName() : null
            throw new IllegalArgumentException('Cannot assign value [' + value + '] with type [' + valueType + '] to property [' + name + '] of class [' + persistentEntity.getName() + ']. The value is not an acceptable type: ' + e.getMessage(), e)
        }
    }

    @PackageScope
    static class FieldEntityReflector implements EntityReflector {

        private final PersistentEntity entity
        private final PropertyReader[] readers
        private final PropertyWriter[] writers
        private final PropertyReader identifierReader
        private final PropertyWriter identifierWriter
        private final String identifierName
        private final Class identifierType
        private final Map<String, PropertyReader> readerMap = new HashMap<>()
        private final Map<String, PropertyWriter> writerMap = new HashMap<>()
        private final Field dirtyCheckingStateField
        private FastClass fastClass

        FieldEntityReflector(PersistentEntity entity) {
            this.entity = entity
            PersistentProperty identity = entity.getIdentity()
            dirtyCheckingStateField = ReflectionUtils.findField(entity.getJavaClass(), getTraitFieldName(DirtyCheckable, '$changedProperties'))
            if (dirtyCheckingStateField != null) {
                ReflectionUtils.makeAccessible(dirtyCheckingStateField)
            }
            ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(entity.getJavaClass())
            String identityName = null
            Class identityType = null
            PropertyReader identityReader = null
            PropertyWriter identityWriter = null
            if (identity != null) {
                identityName = identity.getName()
                identityType = identity.getType()

                ReaderAndWriterMaker readerAndWriterMaker = new ReaderAndWriterMaker(cpf, identityName).make()
                identityReader = readerAndWriterMaker.getPropertyReader()
                identityWriter = readerAndWriterMaker.getPropertyWriter()

                readerMap.put(identityName, identityReader)
                if (identityWriter != null) {
                    writerMap.put(identityName, identityWriter)
                }
            }
            this.identifierName = identityName
            this.identifierType = identityType
            this.identifierReader = identityReader
            this.identifierWriter = identityWriter

            PersistentProperty[] composite = entity.getCompositeIdentity()
            if (composite != null) {
                for (PersistentProperty property in composite) {
                    String propertyName = property.getName()
                    ReaderAndWriterMaker readerAndWriterMaker = new ReaderAndWriterMaker(cpf, propertyName).make()
                    readerMap.put(propertyName, readerAndWriterMaker.getPropertyReader())
                    writerMap.put(propertyName, readerAndWriterMaker.getPropertyWriter())
                }
            }
            List<PersistentProperty> properties = entity.getPersistentProperties()
            readers = new PropertyReader[properties.size()]
            writers = new PropertyWriter[properties.size()]
            for (int i = 0; i < properties.size(); i++) {
                PersistentProperty property = properties.get(i)

                String propertyName = property.getName()
                ReaderAndWriterMaker readerAndWriterMaker = new ReaderAndWriterMaker(cpf, propertyName).make()
                PropertyReader reader = readerAndWriterMaker.getPropertyReader()
                PropertyWriter writer = readerAndWriterMaker.getPropertyWriter()

                readers[i] = reader
                readerMap.put(propertyName, reader)
                writers[i] = writer
                writerMap.put(propertyName, writer)
            }
        }

        @PackageScope
        static String getTraitFieldName(Traits.TraitBridge traitBridge, String fieldName) {
            Class traitClass = traitBridge.traitClass()
            return getTraitFieldName(traitClass, fieldName)
        }

        @PackageScope
        static String getTraitFieldName(Class traitClass, String fieldName) {
            return traitClass.getName().replace('.', '_') + '__' + fieldName
        }

        @Override
        PersistentEntity getPersitentEntity() {
            return this.entity
        }

        @Override
        Map<String, Object> getDirtyCheckingState(Object entity) {
            if (dirtyCheckingStateField != null) {
                try {
                    return (Map<String, Object>) dirtyCheckingStateField.get(entity)
                }
                catch (Throwable e) {
                    return null
                }
            }
            return null
        }

        @Override
        FastClass fastClass() {
            if (fastClass == null) {
                fastClass = FastClass.create(entity.getJavaClass())
            }
            return fastClass
        }

        @Override
        PropertyReader getPropertyReader(String name) {
            final PropertyReader reader = readerMap.get(name)
            if (reader != null) {
                return reader
            }
            throw new IllegalArgumentException('Property [' + name + '] is not a valid property of ' + entity.getJavaClass())
        }

        @Override
        PropertyWriter getPropertyWriter(String name) {
            final PropertyWriter writer = writerMap.get(name)
            if (writer != null) {
                return writer
            }
            else {
                throw new IllegalArgumentException('Property [' + name + '] is not a valid property of ' + entity.getJavaClass())
            }
        }

        @Override
        Object getProperty(Object object, String name) {
            Object target = unwrapIfProxy(getPersitentEntity(), object)
            return getPropertyReader(name).read(target)
        }

        @Override
        void setProperty(Object object, String name, Object value) {
            getPropertyWriter(name).write(object, value)
        }

        @Override
        Class identifierType() {
            return identifierType
        }

        @Override
        Serializable getIdentifier(Object object) {
            if (identifierReader != null && object != null) {
                return (Serializable) identifierReader.read(object)
            }
            return null
        }

        @Override
        void setIdentifier(Object object, Object value) {
            if (identifierWriter != null) {
                identifierWriter.write(object, value)
            }
        }

        @Override
        String getIdentifierName() {
            return identifierName
        }

        @Override
        Iterable<String> getPropertyNames() {
            return readerMap.keySet()
        }

        @Override
        Object getProperty(Object object, int index) {
            return readers[index].read(object)
        }

        @Override
        void setProperty(Object object, int index, Object value) {
            writers[index].write(object, value)
        }

        @PackageScope
        static class ReflectMethodReader implements PropertyReader {

            private final Method method

            ReflectMethodReader(Method method) {
                this.method = method
                ReflectionUtils.makeAccessible(method)
            }

            @Override
            Field field() {
                return null
            }

            @Override
            Method getter() {
                return method
            }

            @Override
            Class propertyType() {
                return method.getReturnType()
            }

            @Override
            Object read(Object object) {
                return ReflectionUtils.invokeMethod(method, object)
            }

        }

        @PackageScope
        static class ReflectionMethodWriter implements PropertyWriter {

            private final Method method
            private final Class propertyType

            ReflectionMethodWriter(Method method, Class propertyType) {
                this.method = method
                ReflectionUtils.makeAccessible(method)
                this.propertyType = propertyType
            }

            @Override
            Field field() {
                return null
            }

            @Override
            Method setter() {
                return method
            }

            @Override
            Class propertyType() {
                return propertyType
            }

            @Override
            void write(Object object, Object value) {
                ReflectionUtils.invokeMethod(method, object, value)
            }

        }

        @PackageScope
        static class FieldReader implements PropertyReader {

            private final Field field
            private final Method getter

            FieldReader(Field field, Method getter) {
                this.field = field
                this.getter = getter
                ReflectionUtils.makeAccessible(field)
            }

            @Override
            Field field() {
                return field
            }

            @Override
            Method getter() {
                return getter
            }

            @Override
            Class propertyType() {
                return field.getType()
            }

            @Override
            Object read(Object object) {
                Object target = object
                try {
                    target = unwrapIfProxy(null, target)
                    return field.get(target)
                }
                catch (Throwable e) {
                    throw new IllegalArgumentException('Cannot read field [' + field + '] from object [' + target + '] of type [' + target.getClass() + ']', e)
                }
            }

        }

        @PackageScope
        static class FieldWriter implements PropertyWriter {

            private final Field field
            private final Method setter

            FieldWriter(Field field, Method setter) {
                this.field = field
                this.setter = setter
                ReflectionUtils.makeAccessible(field)
            }

            @Override
            Field field() {
                return field
            }

            @Override
            Method setter() {
                return setter
            }

            @Override
            Class propertyType() {
                return field.getType()
            }

            @Override
            void write(Object object, Object value) {
                try {
                    field.set(object, value)
                }
                catch (Throwable e) {
                    throw new IllegalArgumentException('Cannot set field [' + field.getName() + '] of object [' + object + '] for value [' + value + '] of type [' + value.getClass().getName() + ']', e)
                }
            }

        }

        private static class ReaderAndWriterMaker {

            private final ClassPropertyFetcher cpf
            private final String propertyName
            private PropertyReader propertyReader
            private PropertyWriter propertyWriter

            ReaderAndWriterMaker(ClassPropertyFetcher cpf, String propertyName) {
                this.cpf = cpf
                this.propertyName = propertyName
            }

            PropertyReader getPropertyReader() {
                return propertyReader
            }

            PropertyWriter getPropertyWriter() {
                return propertyWriter
            }

            ReaderAndWriterMaker make() {
                Class javaClass = cpf.getJavaClass()
                Field field = ReflectionUtils.findField(javaClass, propertyName)
                if (field != null) {
                    ReflectionUtils.makeAccessible(field)
                    propertyReader = new FieldReader(field, ReflectionUtils.findMethod(javaClass, NameUtils.getGetterName(propertyName)))
                    propertyWriter = new FieldWriter(field, ReflectionUtils.findMethod(javaClass, NameUtils.getSetterName(propertyName), field.getType()))
                }
                else {
                    PropertyDescriptor descriptor = cpf.getPropertyDescriptor(propertyName)
                    Method readMethod = descriptor.getReadMethod()

                    Traits.TraitBridge traitBridge = readMethod.getAnnotation(Traits.TraitBridge)
                    String traitFieldName
                    if (traitBridge != null) {
                        traitFieldName = FieldEntityReflector.getTraitFieldName(traitBridge, propertyName)
                    }
                    else {
                        Traits.Implemented traitImplemented = readMethod.getAnnotation(Traits.Implemented)
                        if (traitImplemented != null) {
                            traitFieldName = FieldEntityReflector.getTraitFieldName(readMethod.getDeclaringClass(), propertyName)
                        }
                        else {
                            traitFieldName = null
                        }
                    }
                    if (traitFieldName != null) {
                        field = ReflectionUtils.findField(javaClass, traitFieldName)
                        if (field != null) {
                            ReflectionUtils.makeAccessible(field)
                            propertyReader = new FieldReader(field, readMethod)
                            propertyWriter = new FieldWriter(field, descriptor.getWriteMethod())
                        }
                        else {
                            Method writeMethod = descriptor.getWriteMethod()
                            propertyReader = new ReflectMethodReader(readMethod)
                            propertyWriter = new ReflectionMethodWriter(writeMethod, descriptor.getPropertyType())
                        }
                    }
                    else {
                        propertyReader = new ReflectMethodReader(readMethod)
                        Method writeMethod = descriptor.getWriteMethod()
                        if (writeMethod != null) {
                            propertyWriter = new ReflectionMethodWriter(writeMethod, descriptor.getPropertyType())
                        }
                    }
                }
                return this
            }

        }

    }

    @PackageScope
    static Object unwrapIfProxy(PersistentEntity entity, Object object) {
        if (entity != null) {
            final ProxyHandler proxyHandler = entity.getMappingContext().getProxyHandler()
            return proxyHandler.unwrap(object)
        }
        else {
            return object
        }
    }

}
