/* Copyright 2004-2005 the original author or authors.
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
package org.grails.datastore.mapping.model;

import groovy.lang.MetaProperty;
import org.grails.datastore.mapping.config.Entity;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;

import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MappingFactory<R extends Entity,T extends Property> {

    public static final String IDENTITY_PROPERTY = GormProperties.IDENTITY;
    public static final Set<String> SIMPLE_TYPES;

    static {
        SIMPLE_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            boolean.class.getName(),
            long.class.getName(),
            short.class.getName(),
            int.class.getName(),
            byte.class.getName(),
            float.class.getName(),
            double.class.getName(),
            char.class.getName(),
            Boolean.class.getName(),
            Long.class.getName(),
            Short.class.getName(),
            Integer.class.getName(),
            Byte.class.getName(),
            Float.class.getName(),
            Double.class.getName(),
            Character.class.getName(),
            String.class.getName(),
            java.util.Date.class.getName(),
            Time.class.getName(),
            Timestamp.class.getName(),
            java.sql.Date.class.getName(),
            BigDecimal.class.getName(),
            BigInteger.class.getName(),
            Locale.class.getName(),
            Calendar.class.getName(),
            GregorianCalendar.class.getName(),
            java.util.Currency.class.getName(),
            TimeZone.class.getName(),
            Object.class.getName(),
            Class.class.getName(),
            byte[].class.getName(),
            Byte[].class.getName(),
            char[].class.getName(),
            Character[].class.getName(),
            Blob.class.getName(),
            Clob.class.getName(),
            Serializable.class.getName(),
            URI.class.getName(),
            URL.class.getName(),
            UUID.class.getName(),
            "org.bson.types.ObjectId",
            "java.time.Instant",
            "java.time.LocalDateTime",
            "java.time.LocalDate",
            "java.time.LocalTime",
            "java.time.OffsetDateTime",
            "java.time.OffsetTime",
            "java.time.ZonedDateTime")));
    }

    private Map<Class, Collection<CustomTypeMarshaller>> typeConverterMap = new ConcurrentHashMap<>();

    public void registerCustomType(CustomTypeMarshaller marshallerCustom) {
        Collection<CustomTypeMarshaller> marshallers = typeConverterMap.get(marshallerCustom.getTargetType());
        if(marshallers == null) {
            marshallers = new ConcurrentLinkedQueue<>();
            typeConverterMap.put(marshallerCustom.getTargetType(), marshallers);
        }
        marshallers.add(marshallerCustom);
    }

    public boolean isSimpleType(Class propType) {
        if (propType == null) return false;
        if (propType.isEnum()) {
            // Check if prop (any enum) supports custom type marshaller.
            if (isCustomType(propType)) {
                return false;
            }
            return true;
        }
        if (propType.isArray()) {
            return isSimpleType(propType.getComponentType());
        }
        final String typeName = propType.getName();
        return isSimpleType(typeName);
    }

    public static boolean isSimpleType(final String typeName) {
        return SIMPLE_TYPES.contains(typeName);
    }

    /**
     * Creates the mapped form of a persistent entity
     *
     * @param entity The entity
     * @return The mapped form
     */
    public abstract R createMappedForm(PersistentEntity entity);

    /**
     * Creates the mapped form of a PersistentProperty instance
     * @param mpp The PersistentProperty instance
     * @return The mapped form
     */
    public abstract T createMappedForm(PersistentProperty mpp);

    /**
     * Creates an identifier property
     *
     * @param owner The owner
     * @param context The context
     * @param pd The PropertyDescriptor
     * @return An Identity instance
     */
    public Identity<T> createIdentity(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        return new Identity<T>(owner, context, pd) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping<T> getMapping() {
                return propertyMapping;
            }
        };
    }

    /**
     * Creates the tenant identifier property
     *
     * @param owner The owner
     * @param context The context
     * @param pd The PropertyDescriptor
     * @return An Identity instance
     */
    public TenantId<T> createTenantId(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        return new TenantId<T>(owner, context, pd) {
            PropertyMapping<T> propertyMapping = createDerivedPropertyMapping(this, owner);
            public PropertyMapping<T> getMapping() {
                return propertyMapping;
            }
        };
    }

    /**
     * Return whether the given property descriptor is the tenant id
     *
     * @param entity The entity
     * @param context The context
     * @param descriptor The descriptor
     * @return True if it is
     */
    public abstract boolean isTenantId(PersistentEntity entity, MappingContext context, PropertyDescriptor descriptor);

    /**
     * Creates a custom prpoerty type
     *
     * @param owner The owner
     * @param context The context
     * @param pd The PropertyDescriptor
     * @return A custom property type
     */
    public Custom<T> createCustom(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        final Class<?> propertyType = pd.getPropertyType();
        CustomTypeMarshaller customTypeMarshaller = findCustomType(context, propertyType);
        if (customTypeMarshaller == null && propertyType.isEnum()) {
            // If there is no custom type marshaller for current enum, lookup marshaller for enum itself.
            customTypeMarshaller = findCustomType(context, Enum.class);
        }
        if (customTypeMarshaller == null && !allowArbitraryCustomTypes()) {
            throw new IllegalStateException("Cannot create a custom type without a type converter for type " + propertyType);
        }
        return new Custom<T>(owner, context, pd, customTypeMarshaller) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping<T> getMapping() {
                return propertyMapping;
            }
        };
    }

    protected boolean allowArbitraryCustomTypes() {
        return false;
    }

    protected CustomTypeMarshaller findCustomType(MappingContext context, Class<?> propertyType) {
        final Collection<CustomTypeMarshaller> allMarshallers = typeConverterMap.get(propertyType);
        if(allMarshallers != null) {
            for (CustomTypeMarshaller marshaller : allMarshallers) {
                if(marshaller.supports(context)) {
                    return marshaller;
                }
            }
        }
        return null;
    }

    /**
     * Creates a PropertyDescriptor from a MetaBeanProperty
     *
     * @param property The bean property
     * @return The descriptor or null
     */
    public PropertyDescriptor createPropertyDescriptor(Class declaringClass, MetaProperty property) {
        return ClassPropertyFetcher.createPropertyDescriptor(declaringClass, property);
    }

    /**
     * Creates a simple property type used for mapping basic types such as String, long, integer etc.
     *
     * @param owner The owner
     * @param context The MappingContext
     * @param pd The PropertyDescriptor
     * @return A Simple property type
     */
    public Simple<T> createSimple(PersistentEntity owner, MappingContext context, PropertyDescriptor pd) {
        return new Simple<T>(owner, context, pd) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping<T> getMapping() {
                return propertyMapping;
            }
        };
    }

    protected PropertyMapping<T> createPropertyMapping(final PersistentProperty<T> property, final PersistentEntity owner) {
        return new PropertyMapping<T>() {
            private T mappedForm = createMappedForm(property);
            public ClassMapping getClassMapping() {
                return owner.getMapping();
            }
            public T getMappedForm() {
                return mappedForm;
            }
        };
    }

    private PropertyMapping<T> createDerivedPropertyMapping(final PersistentProperty<T> property, final PersistentEntity owner) {
        final T mappedFormObject = createMappedForm(property);
        mappedFormObject.setDerived(true);
        return new PropertyMapping<T>() {
            private T mappedForm = mappedFormObject;
            public ClassMapping getClassMapping() {
                return owner.getMapping();
            }
            public T getMappedForm() {
                return mappedForm;
            }
        };
    }


    /**
     * Creates a one-to-one association type used for mapping a one-to-one association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The ToOne instance
     */
    public ToOne createOneToOne(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        return new OneToOne<T>(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);

            public PropertyMapping getMapping() {
                return propertyMapping;
            }

            @Override
            public String toString() {
                return associationtoString("one-to-one: ", this);
            }
        };
    }

    /**
     * Creates a many-to-one association type used for a mapping a many-to-one association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The ToOne instance
     */
    public ToOne createManyToOne(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        return new ManyToOne<T>(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping getMapping() {
                return propertyMapping;
            }

            @Override
            public String toString() {
                return associationtoString("many-to-one: ", this);
            }

        };

    }

    /**
     * Creates a {@link OneToMany} type used to model a one-to-many association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link OneToMany} instance
     */
    public OneToMany createOneToMany(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        return new OneToMany<T>(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping getMapping() {
                return propertyMapping;
            }

            @Override
            public String toString() {
                return associationtoString("one-to-many: ", this);
            }
        };

    }

    /**
     * Creates a {@link ManyToMany} type used to model a many-to-many association between entities
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link ManyToMany} instance
     */
    public ManyToMany createManyToMany(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        return new ManyToMany<T>(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping getMapping() {
                return propertyMapping;
            }
            @Override
            public String toString() {
                return associationtoString("many-to-many: ", this);
            }
        };
    }

    /**
     * Creates an {@link Embedded} type used to model an embedded association (composition)
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link Embedded} instance
     */
    public Embedded createEmbedded(PersistentEntity entity,
            MappingContext context, PropertyDescriptor property) {
        return new Embedded<T>(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping getMapping() {
                return propertyMapping;
            }
            @Override
            public String toString() {
                return associationtoString("embedded: ", this);
            }
        };
    }

    /**
     * Creates an {@link EmbeddedCollection} type used to model an embedded collection association (composition).
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The {@link Embedded} instance
     */
    public EmbeddedCollection createEmbeddedCollection(PersistentEntity entity,
            MappingContext context, PropertyDescriptor property) {
        return new EmbeddedCollection<T>(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);
            public PropertyMapping getMapping() {
                return propertyMapping;
            }
            @Override
            public String toString() {
                return associationtoString("embedded: ", this);
            }
        };
    }

    /**
     * Creates a {@link Basic} collection type
     *
     * @param entity The entity
     * @param context The context
     * @param property The property
     * @return The Basic collection type
     */
    public Basic createBasicCollection(PersistentEntity entity,
            MappingContext context, PropertyDescriptor property, Class collectionType) {
        Basic basic = new Basic(entity, context, property) {
            PropertyMapping<T> propertyMapping = createPropertyMapping(this, owner);

            public PropertyMapping getMapping() {
                return propertyMapping;
            }
        };

        CustomTypeMarshaller customTypeMarshaller = findCustomType(context, property.getPropertyType());

        // This is to allow using custom marshaller for list of enum.
        // If no custom type marshaller for current enum.
        if(collectionType != null && collectionType.isEnum()) {
            // First look custom marshaller for related type of collection.
            customTypeMarshaller = findCustomType(context, collectionType);
            if(customTypeMarshaller == null) {
                // If null, look for enum class itself.
                customTypeMarshaller = findCustomType(context, Enum.class);
            }
        }

        if(customTypeMarshaller != null) {
            basic.setCustomTypeMarshaller(customTypeMarshaller);
        }

        return basic;
    }

    public Basic createBasicCollection(PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        return createBasicCollection(entity, context, property, null);
    }

    public boolean isCustomType(Class<?> propertyType) {
        if(typeConverterMap.containsKey(propertyType)) {
            return true;
        }
        if(propertyType.isEnum()) {
            // Check if enum itself supports custom type.
            return typeConverterMap.containsKey(Enum.class);
        }
        return false;
    }

    public IdentityMapping createIdentityMapping(final ClassMapping classMapping) {
        return createDefaultIdentityMapping(classMapping);
    }

    public IdentityMapping createDefaultIdentityMapping(final ClassMapping classMapping) {
        return new IdentityMapping() {

            public String[] getIdentifierName() {
                PersistentProperty identity = classMapping.getEntity().getIdentity();
                String propertyName = identity != null ? identity.getMapping().getMappedForm().getName() : null;
                if(propertyName != null) {
                    return new String[] { propertyName };
                }
                else {
                    return new String[] { IDENTITY_PROPERTY };
                }
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

    protected IdentityMapping createDefaultIdentityMapping(final ClassMapping classMapping, final T property) {
        final String targetName = property != null ? property.getName() : null;
        final String generator = property != null ? property.getGenerator() : null;
        return new IdentityMapping() {

            public String[] getIdentifierName() {
                if(targetName != null) {
                    return new String[] { targetName };
                }
                else {
                    return new String[] { IDENTITY_PROPERTY };
                }
            }

            @Override
            public ValueGenerator getGenerator() {
                return generator != null ? ValueGenerator.valueOf(generator) : ValueGenerator.AUTO;
            }

            public ClassMapping getClassMapping() {
                return classMapping;
            }

            public Property getMappedForm() {
                return property;
            }
        };
    }


    public static String associationtoString(String desc, Association a) {
        return desc + a.getOwner().getName() + "-> " + a.getName() + " ->" + a.getAssociatedEntity().getName();
    }


}
