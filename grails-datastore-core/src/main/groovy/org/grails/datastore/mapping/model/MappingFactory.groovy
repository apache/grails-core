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
package org.grails.datastore.mapping.model

import java.beans.PropertyDescriptor
import java.sql.Blob
import java.sql.Clob
import java.sql.Time
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.Identity
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.Simple
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.model.types.mapping.BasicWithMapping
import org.grails.datastore.mapping.model.types.mapping.CustomWithMapping
import org.grails.datastore.mapping.model.types.mapping.EmbeddedCollectionWithMapping
import org.grails.datastore.mapping.model.types.mapping.EmbeddedWithMapping
import org.grails.datastore.mapping.model.types.mapping.IdentityWithMapping
import org.grails.datastore.mapping.model.types.mapping.ManyToManyWithMapping
import org.grails.datastore.mapping.model.types.mapping.ManyToOneWithMapping
import org.grails.datastore.mapping.model.types.mapping.OneToManyWithMapping
import org.grails.datastore.mapping.model.types.mapping.OneToOneWithMapping
import org.grails.datastore.mapping.model.types.mapping.SimpleWithMapping
import org.grails.datastore.mapping.model.types.mapping.TenantIdWithMapping
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * <p>An abstract factory for creating persistent property instances.</p>
 *
 * <p>Subclasses should implement the createMappedForm method in order to
 * provide a mechanisms for representing the property in a form appropriate
 * for mapping to the underlying datastore. Example:</p>
 *
 * <pre>
 *  <code>
 *      class RelationalPropertyFactory&lt;Column&gt; extends PropertyFactory {
 *            public Column createMappedForm(PersistentProperty mpp) {
 *                return new Column(mpp)
 *            }
 *      }
 *  </code>
 * </pre>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings(['rawtypes', 'unchecked'])
abstract class MappingFactory<R extends Entity, T extends Property> {

    public static final String IDENTITY_PROPERTY = GormProperties.IDENTITY
    public static final Set<String> SIMPLE_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            boolean.getName(),
            long.getName(),
            short.getName(),
            int.getName(),
            byte.getName(),
            float.getName(),
            double.getName(),
            char.getName(),
            Boolean.getName(),
            Long.getName(),
            Short.getName(),
            Integer.getName(),
            Byte.getName(),
            Float.getName(),
            Double.getName(),
            Character.getName(),
            String.getName(),
            java.util.Date.getName(),
            Time.getName(),
            Timestamp.getName(),
            java.sql.Date.getName(),
            BigDecimal.getName(),
            BigInteger.getName(),
            Locale.getName(),
            Calendar.getName(),
            GregorianCalendar.getName(),
            Currency.getName(),
            TimeZone.getName(),
            Object.getName(),
            Class.getName(),
            byte[].class.getName(),
            Byte[].class.getName(),
            char[].class.getName(),
            Character[].class.getName(),
            Blob.getName(),
            Clob.getName(),
            Serializable.getName(),
            URI.getName(),
            URL.getName(),
            UUID.getName(),
            'org.bson.types.ObjectId',
            'java.time.Instant',
            'java.time.LocalDateTime',
            'java.time.LocalDate',
            'java.time.LocalTime',
            'java.time.OffsetDateTime',
            'java.time.OffsetTime',
            'java.time.ZonedDateTime')))

    private final Map<Class, Collection<CustomTypeMarshaller>> typeConverterMap = new ConcurrentHashMap<>()

    void registerCustomType(CustomTypeMarshaller marshallerCustom) {
        Function<Class, Collection<CustomTypeMarshaller>> newQueue = { Class k -> new ConcurrentLinkedQueue<CustomTypeMarshaller>() } as Function<Class, Collection<CustomTypeMarshaller>>
        typeConverterMap.computeIfAbsent(marshallerCustom.getTargetType(), newQueue).add(marshallerCustom)
    }

    boolean isSimpleType(Class propType) {
        if (propType == null) {
            return false
        }
        if (propType.isEnum()) {
            // Check if prop (any enum) supports custom type marshaller.
            if (isCustomType(propType)) {
                return false
            }
            return true
        }
        if (propType.isArray()) {
            return isSimpleType(propType.getComponentType())
        }
        final String typeName = propType.getName()
        return isSimpleType(typeName)
    }

    static boolean isSimpleType(final String typeName) {
        return SIMPLE_TYPES.contains(typeName)
    }

    /**
     * Creates the mapped form of a persistent entity
     *
     * @param entity The entity
     * @return The mapped form
     */
    abstract R createMappedForm(PersistentEntity entity)

    /**
     * Creates the mapped form of a PersistentProperty instance
     * @param mpp The PersistentProperty instance
     * @return The mapped form
     */
    abstract T createMappedForm(PersistentProperty mpp)

    /**
     * Creates an identifier property
     *
     * @param owner The owner
     * @param context The context
     * @param pd The PropertyDescriptor
     * @return An Identity instance
     */
    Identity<T> createIdentity(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        IdentityWithMapping<T> identity = new IdentityWithMapping<>(owner, context, pd)
        identity.setMapping(createPropertyMapping(identity, owner))
        return identity
    }

    /**
     * Creates the tenant identifier property
     *
     * @param owner The owner
     * @param context The context
     * @param pd The PropertyDescriptor
     * @return An Identity instance
     */
    TenantId<T> createTenantId(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        TenantIdWithMapping<T> tenantId = new TenantIdWithMapping<>(owner, context, pd)
        tenantId.setMapping(createDerivedPropertyMapping(tenantId, owner))
        return tenantId
    }

    /**
     * Return whether the given property descriptor is the tenant id
     *
     * @param entity The entity
     * @param context The context
     * @param descriptor The descriptor
     * @return True if it is
     */
    abstract boolean isTenantId(PersistentEntity entity, MappingContext context, PropertyDescriptor descriptor)

    /**
     * Creates a custom prpoerty type
     *
     * @param owner The owner
     * @param context The context
     * @param pd The PropertyDescriptor
     * @return A custom property type
     */
    Custom<T> createCustom(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        final Class<?> propertyType = pd.getPropertyType()
        CustomTypeMarshaller customTypeMarshaller = findCustomType(context, propertyType)
        if (customTypeMarshaller == null && propertyType.isEnum()) {
            // If there is no custom type marshaller for current enum, lookup marshaller for enum itself.
            customTypeMarshaller = findCustomType(context, Enum)
        }
        if (customTypeMarshaller == null && !allowArbitraryCustomTypes()) {
            throw new IllegalStateException('Cannot create a custom type without a type converter for type ' + propertyType)
        }
        CustomWithMapping<T> custom = new CustomWithMapping<>(owner, context, pd, customTypeMarshaller)
        custom.setMapping(createPropertyMapping(custom, owner))
        return custom
    }

    protected boolean allowArbitraryCustomTypes() {
        return false
    }

    protected CustomTypeMarshaller findCustomType(MappingContext context, Class<?> propertyType) {
        final Collection<CustomTypeMarshaller> allMarshallers = typeConverterMap.get(propertyType)
        if (allMarshallers != null) {
            for (CustomTypeMarshaller marshaller in allMarshallers) {
                if (marshaller.supports(context)) {
                    return marshaller
                }
            }
        }
        return null
    }

    /**
     * Creates a PropertyDescriptor from a MetaBeanProperty
     *
     * @param property The bean property
     * @return The descriptor or null
     */
    PropertyDescriptor createPropertyDescriptor(Class declaringClass, MetaProperty property) {
        return ClassPropertyFetcher.createPropertyDescriptor(declaringClass, property)
    }

    /**
     * Creates a simple property type used for mapping basic types such as String, long, integer etc.
     *
     * @param owner The owner
     * @param context The MappingContext
     * @param pd The PropertyDescriptor
     * @return A Simple property type
     */
    Simple<T> createSimple(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        SimpleWithMapping<T> simple = new SimpleWithMapping<>(owner, context, pd)
        simple.setMapping(createPropertyMapping(simple, owner))
        return simple
    }

    protected PropertyMapping<T> createPropertyMapping(final PersistentProperty<T> property, final PersistentEntity owner) {
        return new DefaultPropertyMapping<>(owner.getMapping(), createMappedForm(property))
    }

    protected PropertyMapping<T> createDerivedPropertyMapping(final PersistentProperty<T> property, final PersistentEntity owner) {
        final T mappedFormObject = createMappedForm(property)
        mappedFormObject.setDerived(true)
        return new DefaultPropertyMapping<>(owner.getMapping(), mappedFormObject)
    }

    /**
     * Creates a one-to-one association type used for mapping a one-to-one association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The ToOne instance
     */
    ToOne createOneToOne(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        OneToOneWithMapping<T> oneToOne = new OneToOneWithMapping<>(entity, context, property)
        oneToOne.setMapping(createPropertyMapping(oneToOne, entity))
        return oneToOne
    }

    /**
     * Creates a many-to-one association type used for a mapping a many-to-one association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The ToOne instance
     */
    ToOne createManyToOne(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        ManyToOneWithMapping<T> manyToOne = new ManyToOneWithMapping<>(entity, context, property)
        manyToOne.setMapping(createPropertyMapping(manyToOne, entity))
        return manyToOne
    }

    /**
     * Creates a {@link OneToMany} type used to model a one-to-many association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link OneToMany} instance
     */
    OneToMany createOneToMany(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        OneToManyWithMapping<T> oneToMany = new OneToManyWithMapping<>(entity, context, property)
        oneToMany.setMapping(createPropertyMapping(oneToMany, entity))
        return oneToMany
    }

    /**
     * Creates a {@link ManyToMany} type used to model a many-to-many association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link ManyToMany} instance
     */
    ManyToMany createManyToMany(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        ManyToManyWithMapping<T> manyToMany = new ManyToManyWithMapping<>(entity, context, property)
        manyToMany.setMapping(createPropertyMapping(manyToMany, entity))
        return manyToMany
    }

    /**
     * Creates an {@link Embedded} type used to model an embedded association (composition)
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link Embedded} instance
     */
    Embedded createEmbedded(PersistentEntity entity,
                            MappingContext context, PropertyDescriptor property) {
        EmbeddedWithMapping<T> embedded = new EmbeddedWithMapping<>(entity, context, property)
        embedded.setMapping(createPropertyMapping(embedded, entity))
        return embedded
    }

    /**
     * Creates an {@link EmbeddedCollection} type used to model an embedded collection association (composition).
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link Embedded} instance
     */
    EmbeddedCollection createEmbeddedCollection(PersistentEntity entity,
                                                MappingContext context, PropertyDescriptor property) {
        EmbeddedCollectionWithMapping<T> embedded = new EmbeddedCollectionWithMapping<>(entity, context, property)
        embedded.setMapping(createPropertyMapping(embedded, entity))
        return embedded
    }

    /**
     * Creates a {@link Basic} collection type
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The Basic collection type
     */
    Basic createBasicCollection(PersistentEntity entity,
                                MappingContext context, PropertyDescriptor property, Class collectionType) {
        BasicWithMapping<T> basic = new BasicWithMapping<>(entity, context, property)
        basic.setMapping(createPropertyMapping(basic, entity))

        CustomTypeMarshaller customTypeMarshaller = findCustomType(context, property.getPropertyType())

        // This is to allow using custom marshaller for list of enum.
        // If no custom type marshaller for current enum.
        if (collectionType != null && collectionType.isEnum()) {
            // First look custom marshaller for related type of collection.
            customTypeMarshaller = findCustomType(context, collectionType)
            if (customTypeMarshaller == null) {
                // If null, look for enum class itself.
                customTypeMarshaller = findCustomType(context, Enum)
            }
        }

        if (customTypeMarshaller != null) {
            basic.setCustomTypeMarshaller(customTypeMarshaller)
        }

        return basic
    }

    Basic createBasicCollection(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        return createBasicCollection(entity, context, property, null)
    }

    boolean isCustomType(Class<?> propertyType) {
        if (typeConverterMap.containsKey(propertyType)) {
            return true
        }
        if (propertyType.isEnum()) {
            // Check if enum itself supports custom type.
            return typeConverterMap.containsKey(Enum)
        }
        return false
    }

    IdentityMapping createIdentityMapping(final ClassMapping classMapping) {
        return createDefaultIdentityMapping(classMapping)
    }

    IdentityMapping createDefaultIdentityMapping(final ClassMapping classMapping) {
        return new DefaultIdentityMapping(classMapping)
    }

    protected IdentityMapping createDefaultIdentityMapping(final ClassMapping classMapping, final T property) {
        String targetName = property != null ? property.getName() : null
        String[] identifierNames = targetName != null ? [targetName] as String[] : [IDENTITY_PROPERTY] as String[]
        String generatorName = property != null ? property.getGenerator() : null
        ValueGenerator generator = generatorName != null ? ValueGenerator.valueOf(generatorName.toUpperCase(Locale.ENGLISH)) : ValueGenerator.AUTO
        return new DefaultIdentityMapping<>(classMapping, property, identifierNames, generator)
    }

    static String associationtoString(String desc, Association a) {
        return desc + a.getOwner().getName() + '-> ' + a.getName() + ' ->' + a.getAssociatedEntity().getName()
    }

}
