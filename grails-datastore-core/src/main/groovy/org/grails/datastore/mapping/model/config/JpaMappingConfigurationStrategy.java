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

package org.grails.datastore.mapping.model.config;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import groovy.lang.MetaProperty;

import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.engine.internal.MappingUtils;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.MappingFactory;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.ValueGenerator;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.EmbeddedCollection;
import org.grails.datastore.mapping.model.types.Simple;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;

@SuppressWarnings({"rawtypes", "unchecked", "Duplicates"})
public class JpaMappingConfigurationStrategy extends GormMappingConfigurationStrategy {

    public JpaMappingConfigurationStrategy(MappingFactory propertyFactory) {
        super(propertyFactory);
    }

    private boolean isJpaEntity(Class clazz) {
        return clazz.isAnnotationPresent(Entity.class);
    }

    @Override
    public List<PersistentProperty> getPersistentProperties(PersistentEntity entity, MappingContext context, ClassMapping classMapping, boolean includeIdentifiers) {
        Class entityClass = entity.getJavaClass();
        if (!isJpaEntity(entityClass)) {
            return super.getPersistentProperties(entity, context, classMapping, includeIdentifiers);
        }

        final List<PersistentProperty> persistentProperties = new ArrayList<PersistentProperty>();
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(entityClass);

        for (MetaProperty metaProperty : cpf.getMetaProperties()) {
            PropertyDescriptor descriptor = propertyFactory.createPropertyDescriptor(entityClass, metaProperty);

            if (descriptor == null || descriptor.getPropertyType() == null || descriptor.getPropertyType() == Object.class) {
                // indexed property
                continue;
            }
            if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                // non-persistent getter or setter
                continue;
            }

            final String propertyName = descriptor.getName();
            Method readMethod = descriptor.getReadMethod();

            Field field;
            try {
                field = cpf.getDeclaredField(descriptor.getName());
            } catch (Exception e) {
                continue;
            }

            if (hasAnnotation(readMethod, field, Transient.class)) {
                continue;
            }

            if (isExcludedProperty(propertyName, classMapping, new ArrayList<>(), includeIdentifiers)) continue;

            Class<?> propertyType = descriptor.getPropertyType();

            if(hasAnnotation(readMethod, field, Id.class)) {
                persistentProperties.add( propertyFactory.createIdentity(entity, context, descriptor));
            }
            else if(hasAnnotation(readMethod, field, EmbeddedId.class)) {
                persistentProperties.add( propertyFactory.createIdentity(entity, context, descriptor));
            }
            else if (hasAnnotation(readMethod, field, Embedded.class)) {
                if (isCollectionType(propertyType)) {
                    final Association association = establishRelationshipForCollection(descriptor, field, entity, context, true);
                    if (association != null) {
                        persistentProperties.add(association);
                    }
                }
                else {
                    final ToOne association = establishDomainClassRelationship(entity, descriptor, field, context, true);
                    if (association != null) {
                        persistentProperties.add(association);
                    }
                }
            }
            else if (isCollectionType(propertyType)) {
                final Association association = establishRelationshipForCollection(descriptor, field, entity, context, false);
                if (association != null) {
                    configureOwningSide(association);
                    persistentProperties.add(association);
                }
            }
            // otherwise if the type is a domain class establish relationship
            else if (isPersistentEntity(propertyType)) {
                final ToOne association = establishDomainClassRelationship(entity, descriptor, field, context, false);
                if (association != null) {
                    configureOwningSide(association);
                    persistentProperties.add(association);
                }
            }
            else if (propertyFactory.isSimpleType(propertyType)) {
                Simple simpleProperty = propertyFactory.createSimple(entity, context, descriptor);
                if(hasAnnotation(readMethod, field, GeneratedValue.class)) {
                    simpleProperty.getMapping().getMappedForm().setDerived(true);
                }
                persistentProperties.add(simpleProperty);
            }
            else if (supportsCustomType(propertyType)) {
                persistentProperties.add(propertyFactory.createCustom(entity, context, descriptor));
            }
        }
        return persistentProperties;
    }


    protected Association establishRelationshipForCollection(PropertyDescriptor property, Field field, PersistentEntity entity, MappingContext context, boolean embedded) {
        Class javaClass = entity.getJavaClass();
        Class genericClass = MappingUtils.getGenericTypeForProperty(javaClass, property.getName());
        Class relatedClassType = null;
        if (genericClass != null) {
            relatedClassType = genericClass;
        }

        if (relatedClassType == null) {
            return propertyFactory.createBasicCollection(entity, context, property);
        }

        if (embedded) {
            if (propertyFactory.isSimpleType(relatedClassType)) {
                return propertyFactory.createBasicCollection(entity, context, property, relatedClassType);
            }
            else {
                // no point in setting up bidirectional link here, since target isn't an entity.
                EmbeddedCollection association = propertyFactory.createEmbeddedCollection(entity, context, property);
                PersistentEntity associatedEntity = getOrCreateEmbeddedEntity(entity, context, relatedClassType);
                association.setAssociatedEntity(associatedEntity);
                return association;
            }
        }
        else if (!isPersistentEntity(relatedClassType) && !relatedClassType.equals(entity.getJavaClass())) {
            // otherwise set it to not persistent as you can't persist
            // relationships to non-domain classes
            return propertyFactory.createBasicCollection(entity, context, property, relatedClassType);
        }

        // set the referenced type in the property
        ClassPropertyFetcher referencedCpf = ClassPropertyFetcher.forClass(relatedClassType);
        String referencedPropertyName = null;
        Method readMethod = property.getReadMethod();

        Association association = null;

        if (hasAnnotation(readMethod, field, ManyToMany.class)) {
            association = propertyFactory.createManyToMany(entity, context, property);
            ManyToMany manyToMany = getAnnotation(readMethod, field, ManyToMany.class);
            if (!manyToMany.mappedBy().equals("")) {
                ((org.grails.datastore.mapping.model.types.ManyToMany)association).setInversePropertyName(manyToMany.mappedBy());
                referencedPropertyName = manyToMany.mappedBy();
            } else {
                List<PropertyDescriptor> descriptors = referencedCpf.getPropertiesAssignableToType(Collection.class);
                if (descriptors.size() > 0) {
                    for (PropertyDescriptor descriptor : descriptors) {
                        Class relatedGenericClass = MappingUtils.getGenericTypeForProperty(relatedClassType, descriptor.getName());
                        ManyToMany relatedManyToMany = getAnnotation(referencedCpf, descriptor, ManyToMany.class);
                        if (relatedGenericClass == null && relatedManyToMany != null) {
                            relatedGenericClass = relatedManyToMany.targetEntity();
                        }
                        if (relatedGenericClass == javaClass) {
                            if (relatedManyToMany != null && relatedManyToMany.mappedBy().equals(property.getName())) {
                                entity.addOwner(relatedClassType);
                                referencedPropertyName = descriptor.getName();
                                break;
                            }
                        }

                    }
                }
            }
        }
        else if (hasAnnotation(readMethod, field, OneToMany.class)) {
            association = propertyFactory.createOneToMany(entity, context, property);
            OneToMany oneToMany = getAnnotation(readMethod, field, OneToMany.class);
            if (!oneToMany.mappedBy().equals("")) {
                referencedPropertyName = oneToMany.mappedBy();
            }
        }

        if (association != null) {
            PersistentEntity associatedEntity = getOrCreateAssociatedEntity(entity, context, relatedClassType);
            association.setAssociatedEntity(associatedEntity);
            if (referencedPropertyName != null) {
                // bidirectional
                association.setReferencedPropertyName(referencedPropertyName);
            }
        }
        return association;
    }

    private ToOne establishDomainClassRelationship(PersistentEntity entity, PropertyDescriptor property, Field field, MappingContext context, boolean embedded) {
        ToOne association = null;
        Class propType = property.getPropertyType();

        if (embedded) {
            // uni-directional to embedded non-entity
            PersistentEntity associatedEntity = getOrCreateEmbeddedEntity(entity, context, propType);
            association = propertyFactory.createEmbedded(entity, context, property);
            association.setAssociatedEntity(associatedEntity);
            return association;
        }

        ClassPropertyFetcher relatedCpf = ClassPropertyFetcher.forClass(propType);

        // if there is a relationships map use that to find out
        // whether it is mapped to a Set
        String relatedClassPropertyName = null;

        Method readMethod = property.getReadMethod();


        if (getAnnotation(readMethod, field, ManyToOne.class) != null) {
            association = propertyFactory.createManyToOne(entity, context, property);

            List<PropertyDescriptor> descriptors = relatedCpf.getPropertiesAssignableToType(Collection.class);
            association.setForeignKeyInChild(true);
            if (descriptors.size() > 0) {
                for (PropertyDescriptor descriptor : descriptors) {
                    Class genericClass = MappingUtils.getGenericTypeForProperty(propType, descriptor.getName());
                    OneToMany relatedOneToMany = getAnnotation(relatedCpf, descriptor, OneToMany.class);
                    if (genericClass == null && relatedOneToMany != null) {
                        genericClass = relatedOneToMany.targetEntity();
                    }
                    if (genericClass == entity.getJavaClass()) {
                        if (relatedOneToMany != null && relatedOneToMany.mappedBy().equals(property.getName())) {
                            association.setForeignKeyInChild(false);
                            entity.addOwner(propType);
                            relatedClassPropertyName = descriptor.getName();
                            break;
                        }
                    }
                }
            }
        }
        else {
            OneToOne oneToOne = getAnnotation(readMethod, field, OneToOne.class);
            if (oneToOne != null) {
                if (!oneToOne.mappedBy().equals("")) {
                    relatedClassPropertyName = oneToOne.mappedBy();
                }
                association = propertyFactory.createOneToOne(entity, context, property);
                association.setForeignKeyInChild(true);
                List<PropertyDescriptor> descriptors = relatedCpf.getPropertiesOfType(entity.getJavaClass());
                if (descriptors.size() > 0) {
                    for (PropertyDescriptor descriptor : descriptors) {
                        OneToOne relatedOneToOne = getAnnotation(relatedCpf, descriptor, OneToOne.class);
                        if (relatedOneToOne != null && relatedOneToOne.mappedBy().equals(property.getName())) {
                            association.setForeignKeyInChild(false);
                            entity.addOwner(propType);
                            relatedClassPropertyName = descriptor.getName();
                            break;
                        }
                    }
                }
            }
        }

        // bi-directional
        if (association != null) {
            PersistentEntity associatedEntity = getOrCreateAssociatedEntity(entity, context, propType);
            association.setAssociatedEntity(associatedEntity);
            if (relatedClassPropertyName != null ) {
                association.setReferencedPropertyName(relatedClassPropertyName);
            }
        }

        return association;
    }

    @Override
    public IdentityMapping getIdentityMapping(final ClassMapping classMapping) {

        final PersistentEntity entity = classMapping.getEntity();

        if (!isJpaEntity(entity.getJavaClass())) {
            return super.getIdentityMapping(classMapping);
        }

        return new IdentityMapping() {
            String[] idPropertiesArray;

            public String[] getIdentifierName() {
                if(idPropertiesArray != null) {
                    return idPropertiesArray;
                }

                List<String> idProperties = new ArrayList<>();

                ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(entity.getJavaClass());

                for (MetaProperty metaProperty : cpf.getMetaProperties()) {
                    int modifiers = metaProperty.getModifiers();
                    if(Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) {
                        continue;
                    }
                    PropertyDescriptor pd = propertyFactory.createPropertyDescriptor(entity.getJavaClass(), metaProperty);
                    if(pd != null) {

                        if (hasAnnotation(cpf, pd, Id.class)) {
                            idProperties.add(metaProperty.getName());
                        }
                        else if (hasAnnotation(cpf, pd, EmbeddedId.class)) {
                            idProperties.add(metaProperty.getName());
                        }
                    }
                }

                if(idProperties.isEmpty()) {
                    // default to just use 'id'
                    idProperties.add(GormProperties.IDENTITY);
                }
                idPropertiesArray = idProperties.toArray(new String[idProperties.size()]);
                return idPropertiesArray;
            }

            @Override
            public ValueGenerator getGenerator() {
                return ValueGenerator.AUTO;
            }

            public ClassMapping getClassMapping() {
                return classMapping;
            }

            public Property getMappedForm() {
                return classMapping.getEntity().getIdentity().getMapping().getMappedForm();
            }
        };
    }

    <A extends Annotation> boolean hasAnnotation(Method method, Field field, Class<A> type) {
        return (method != null && method.isAnnotationPresent(type)) || (field != null && field.isAnnotationPresent(type));
    }

    <A extends Annotation> A getAnnotation(Method method, Field field, Class<A> type) {
        if (method != null && method.isAnnotationPresent(type)) {
            return method.getAnnotation(type);
        }
        if (field != null && field.isAnnotationPresent(type)) {
            return field.getAnnotation(type);
        }
        return null;
    }

    <A extends Annotation> boolean hasAnnotation(ClassPropertyFetcher cpf, PropertyDescriptor descriptor, Class<A> type) {
        Method readMethod = descriptor.getReadMethod();
        if (readMethod != null && readMethod.isAnnotationPresent(type)) {
            return true;
        }
        Field field = cpf.getDeclaredField(descriptor.getName());
        return field != null && field.isAnnotationPresent(type);
    }

    <A extends Annotation> A getAnnotation(ClassPropertyFetcher cpf, PropertyDescriptor descriptor, Class<A> type) {
        Method readMethod = descriptor.getReadMethod();
        if (readMethod != null && readMethod.isAnnotationPresent(type)) {
            return readMethod.getAnnotation(type);
        }
        Field field = cpf.getDeclaredField(descriptor.getName());
        if (field != null && field.isAnnotationPresent(type)) {
            return field.getAnnotation(type);
        }
        return null;
    }

}
