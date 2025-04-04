/* Copyright (C) 2014 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.mongo.simple

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.bson.Document
import org.grails.datastore.bson.query.BsonQuery
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.types.AbstractMappingAwareCustomTypeMarshaller
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Query.Between
import org.grails.datastore.mapping.query.Query.Equals
import org.grails.datastore.mapping.query.Query.In
import org.grails.datastore.mapping.query.Query.NotEquals

import jakarta.persistence.EnumType as JEnumType
import java.lang.reflect.Array
/**
 * A custom type for persisting Enum which have an id field in domain classes.
 * For example: To save identity instead of string in database for field <b>type</b>.
 * 
 * <pre>
 *      class User {
 *          UserType type
 *          String name
 *      }
 *
 *      enum UserType {
 *          A(1), B(2)
 *          
 *          final int id
 *          UserType(int id) {
 *              this.id = id
 *          }
 *      }
 * </pre>
 * 
 * @author Shashank Agrawal
 * @author Causecode Technologies
 * 
 * @since 3.1.3
 *
 */
@CompileStatic
class EnumType extends AbstractMappingAwareCustomTypeMarshaller<Object, Document, Document> {

    @Override
    boolean supports(MappingContext context) {
        return context instanceof MongoMappingContext;
    }

    @Override
    boolean supports(Datastore datastore) {
        return datastore instanceof MongoDatastore;
    }

    /**
     * Get type of collection by looking at <code>hasMany</code> static field in
     * the domain class.
     * @Example: Will return String class for list of String mapped by hasMany.
     */
    private static Class getCollectionType(PersistentProperty property) {
        if(property instanceof Basic) {
            return ((Basic)property).componentType;
        }
        else if(property instanceof Association) {
            return ((Association)property).associatedEntity.javaClass
        }
        return null
    }

    private static Object getEnumValueForOrdinal(Number value, Class type) {
        try {
            Object values = type.getMethod("values").invoke(type);
            return Array.get(values, value.intValue());
        } catch (Exception e) {
            // ignore
        }
        return value;
    }

    private static boolean isEnumTypeCollection(PersistentProperty property) {
        if (!(property instanceof Basic)) {
            return false
        }
        else {
            Basic basic = (Basic)property;
            return basic.componentType.isEnum()
        }
    }

    private static boolean isOrdinalTypeEnum(PersistentProperty property) {
        final Property mappedProperty = property.getMapping().getMappedForm()

        property.getType().isEnum() && mappedProperty?.getEnumTypeObject() == JEnumType.ORDINAL
    }

    /*
     * Get the value of enum for write or query operation i.e. 
     * if enum is marked for ordinal mapping, return its ordinal value,
     * if enum has id, returns the id,
     * otherwise return the name of enum itself.
     */
    private static Object enumValue(PersistentProperty property, Object value, Class enumType = null) {
        if (value == null) {
            return null
        }

        enumType = enumType ?: property.getType()

        if (value.toString().isNumber()) {
            return value
        }

        if (value instanceof String) {
            value = Enum.valueOf(enumType, value)
        }

        if (value instanceof Enum) {
            if (isOrdinalTypeEnum(property)) {
                value = ((Enum) value).ordinal()
            } else if (value.hasProperty(GormProperties.IDENTITY)) {
                value = getId((Enum)value)
            } else {
                value = ((Enum)value).name()
            }
        }

        value
    }

    @CompileDynamic
    protected static Object getId(Enum value) {
        value.id
    }

    @CompileDynamic
    EnumType() {
        super(Enum)
    }

    /**
     * For custom user types, GORM return an empty map where our custom value needs
     * to be inserted.
     * For example: If our code is something like this:
     * <pre>
     *      <code>
     *          User.withCriteria {
     *              or {
     *                  eq("name", "admin")
     *                  eq("status", UserStatus.ACTIVE)
     *              }
     *              eq("foo", "bar")
     *          }
     *      </code>
     * </pre>
     *
     * Then the query we receive will be like:
     * <code>
     *      <pre>
     *          [$and: [[$or: [["name": "admin"], [:]]], ["foo": "bar"]]
     *      </pre>
     * </code>
     *
     * Now we have to place value of status to the blank field.
     * This method searches that empty place and put the value to the right place.
     */
    private void putValueToProperPlace(PersistentProperty property, String queryKey, Query.PropertyCriterion criterion, Document nativeQuery) {
        if (!nativeQuery || nativeQuery.isEmpty()) {     // If criteria empty, means we got the place to insert.
            Document criteriaObject = new Document()

            if (criterion instanceof Equals) {
                nativeQuery.put(queryKey, enumValue(property, criterion.value))
            } else if (criterion instanceof NotEquals) {
                nativeQuery.put(queryKey, [(BsonQuery.NE_OPERATOR): enumValue(property, criterion.value)])
            } else if (criterion instanceof Between) {
                criteriaObject.put(BsonQuery.GTE_OPERATOR, enumValue(property, ((Between) criterion).getFrom()))
                criteriaObject.put(BsonQuery.LTE_OPERATOR, enumValue(property, ((Between) criterion).getTo()))

                nativeQuery.put(queryKey, criteriaObject)
            } else if (criterion instanceof In) {
                List criteriaValues = []
                ((In) criterion).getValues().each { crtieriaValue ->
                    criteriaValues << enumValue(property, crtieriaValue)
                }

                criteriaObject.put(BsonQuery.IN_OPERATOR, criteriaValues)
                nativeQuery.put(queryKey, criteriaObject)
            }

            return
        }

        // Iterate each field in the query deeply to get the blank field
        for(String key in nativeQuery.keySet()) {
            def value = nativeQuery.get(key)
            if (value instanceof Collection) {
                ((Collection<Document>) value).each { Document queryObject ->
                    if (queryObject.isEmpty()) {
                        // Recursive call the same method.
                        putValueToProperPlace(property, queryKey, criterion, queryObject)
                    }
                }
            }
        }
    }

    @Override
    protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion criterion, Document nativeQuery) {
        putValueToProperPlace(property, key, criterion, nativeQuery)
    }

    @Override
    @CompileDynamic
    protected Object readInternal(PersistentProperty property, String key, Document nativeSource) {
        final def value = nativeSource.get(key)
        if (value == null) {
            return null
        }

        Class propertyType = property.getType()

        if (isOrdinalTypeEnum(property)) {
            return getEnumValueForOrdinal((Number)value, propertyType)
        }

        def finalValue

        // If property is a collection of Enum.
        if (isEnumTypeCollection(property)) {
            finalValue = []
            propertyType = getCollectionType(property)

            // Then value will be a list like: ["ACTIVE", "INACTIVE"]
            value.each { persistedValue ->
                // If value is a number, then Enum type has id field.
                if (persistedValue.toString().isNumber()) {
                    finalValue << propertyType.values().find { it.id == persistedValue.toInteger() }
                } else {
                    // Backward support for the enums that does not have id.
                    finalValue << Enum.valueOf(propertyType, persistedValue)
                }
            }

            return finalValue
        } else if (value.toString().isNumber()) {
            // If value is a number, then Enum type will either has id field.
            return propertyType.values().find { it.id == value.toInteger() }
        }

        // Backward support for the enums that does not have id.
        return Enum.valueOf(propertyType, value)
    }

    @Override
    protected Object writeInternal(PersistentProperty property, String key, Object value, Document nativeTarget) {
        if (!value) {
            nativeTarget.put(key, null)
            return null
        }

        // If property is a collection of Enum.
        if (isEnumTypeCollection(property)) {
            List finalValue = []

            Class collectionType = getCollectionType(property)
            value.each {
                finalValue << enumValue(property, it, collectionType)
            }

            nativeTarget.put(key, finalValue)
            return finalValue
        }

        def finalValue = enumValue(property, value)
        nativeTarget.put(key, finalValue)
        return finalValue
    }
}