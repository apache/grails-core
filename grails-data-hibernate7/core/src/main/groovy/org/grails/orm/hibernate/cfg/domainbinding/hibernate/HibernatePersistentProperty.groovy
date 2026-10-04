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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import groovy.transform.CompileStatic
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Table
import org.hibernate.mapping.ToOne
import org.hibernate.usertype.UserCollectionType

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig

import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.isNotEmpty
import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.qualify

/** Interface for Hibernate persistent properties */
@CompileStatic
interface HibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

    private static String getMappingName(Class<?> propertyClass, Mapping mapping) {
        // Falls back to the class name both when there is no mapping at all and when the
        // mapping has no custom user type registered for this class (the normal case).
        String mappedName = mapping != null ? mapping.getTypeName(propertyClass) : null
        return mappedName != null ? mappedName : getClassName(propertyClass)
    }

    private static String getClassName(Class<?> propertyClass) {
        return propertyClass != null && !propertyClass.isEnum() ? propertyClass.getName() : null
    }

    default boolean isBidirectionalManyToOneWithListMapping(Property prop) {
        return false
    }

    default HibernateAssociation getHibernateInverseSide() {
        return this instanceof Association ? (HibernateAssociation) ((Association) this).getInverseSide() : null
    }

    default GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return this instanceof Association ?
                (GrailsHibernatePersistentEntity) ((Association) this).getAssociatedEntity() :
                null
    }

    /**
     * @return The type name
     */
    default String getTypeName() {
        return getTypeName(getType())
    }

    /**
     * @param propertyType The property type
     * @return The type name
     */
    default String getTypeName(Class<?> propertyType) {
        return getTypeName(propertyType, getMappedForm(), getHibernateOwner().getMappedForm())
    }

    /**
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(PropertyConfig config, Mapping mapping) {
        return getTypeName(getType(), config, mapping)
    }

    /**
     * @param propertyType The property type
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(Class<?> propertyType, PropertyConfig config, Mapping mapping) {
        // Falls through to the mapping-derived name both when there is no config and when the
        // config carries no explicit type name (the normal case for an unmapped property such as id).
        String configuredName = config != null ? config.getTypeName() : null
        return configuredName != null ? configuredName : getMappingName(propertyType, mapping)
    }

    default GrailsHibernatePersistentEntity getHibernateOwner() {
        return (GrailsHibernatePersistentEntity) getOwner()
    }

    default Class<?> getUserType() {
        PropertyConfig config = getMappedForm()
        if (config == null) {
            return null
        }
        Object typeObj = config.getType()
        Class<?> userType = null
        if (typeObj instanceof Class) {
            userType = (Class<?>) typeObj
        } else if (typeObj != null) {
            String typeClassName = typeObj.toString()
            try {
                userType = Class.forName(typeClassName, true, Thread.currentThread().getContextClassLoader())
            } catch (ClassNotFoundException ignored) {
                // ignore
            }
        }
        return userType
    }

    default boolean isUserButNotCollectionType() {
        return getUserType() != null && !UserCollectionType.isAssignableFrom(getUserType())
    }

    default boolean isEnumType() {
        Class<?> propertyType = getType()
        return propertyType != null && propertyType.isEnum()
    }

    /**
     * @return Whether this property is an enum property.
     */
    default boolean isEnum() {
        return this instanceof HibernateEnumProperty
    }

    default boolean isValidHibernateOneToOne() {
        return false
    }

    default boolean isValidHibernateManyToOne() {
        return false
    }

    default boolean isEmbedded() {
        return this instanceof Embedded
    }

    default void validateAssociation() {
        // no validation by default; association subtypes override
    }

    default boolean isSerializableType() {
        return 'serializable' == getTypeName()
    }

    @Override
    default boolean isLazyAble() {
        return this instanceof HibernateAssociation ||
                !(this instanceof Embedded) && !this.equals(this.getOwner().getIdentity())
    }

    /**
     * @return The mapped form
     */
    default PropertyConfig getHibernateMappedForm() {
        return getMappedForm()
    }

    /**
     * Determines if the property should be lazy.
     * @return True if it should be lazy
     */
    default boolean isLazy() {
        return getHibernateOwner().isLazy(this)
    }

    /**
     * @return true if the property has a join key mapping
     */
    default boolean isJoinKeyMapped() {
        return getMappedForm() != null && getMappedForm().hasJoinKeyMapping() && supportsJoinColumnMapping()
    }

    default String getMappedColumnName() {
        PropertyConfig mappedForm = getMappedForm()
        return mappedForm != null ? mappedForm.getColumn() : null
    }

    default String getColumnName(ColumnConfig cc) {
        // Each step falls through to the next when it yields null.
        if (isJoinKeyMapped()) {
            List<ColumnConfig> keys = getMappedForm().getJoinTable().getKeys()
            String keyName = keys == null || keys.isEmpty() ? null : keys.get(0).getName()
            if (keyName != null) {
                return keyName
            }
        }
        String configuredName = cc != null ? cc.getName() : null
        return configuredName != null ? configuredName : getMappedColumnName()
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The type name
     */
    default String getTypeName(SimpleValue simpleValue) {
        return getTypeProperty(simpleValue).getTypeName()
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The type parameters
     */
    default Properties getTypeParameters(SimpleValue simpleValue) {
        if (getTypeName(simpleValue) != null) {
            PropertyConfig typeMappedForm = getTypeProperty(simpleValue).getMappedForm()
            Properties typeParams = typeMappedForm != null ? typeMappedForm.getTypeParams() : null
            return typeParams != null ? typeParams : new Properties()
        }
        return new Properties()
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The property that defines the type
     */
    default HibernatePersistentProperty getTypeProperty(SimpleValue simpleValue) {
        if (simpleValue instanceof DependantValue) {
            HibernatePersistentProperty identity = getHibernateOwner().getIdentity()
            return identity != null ? identity : this
        }
        return this
    }

    default Table getTable() {
        return getPersistentClass().getTable()
    }

    default PersistentClass getPersistentClass() {
        return getHibernateOwner().getPersistentClass()
    }

    /**
     * Returns the generator name for this property. For identity properties the generator
     * is resolved from the owning entity; for regular properties it comes from the mapped form.
     *
     * @return The generator name, or {@code null} if none is configured
     */
    default String getGeneratorName() {
        PropertyConfig mappedForm = getHibernateMappedForm()
        return mappedForm != null ? mappedForm.getGenerator() : null
    }

    default HibernatePersistentProperty validateProperty() {
        return this
    }

    default String getNameForPropertyAndPath(String path) {
        if (isNotEmpty(path)) {
            return qualify(path, getName())
        }
        return getName()
    }

    /**
     * Builds a {@link HibernateSimpleIdentity} from this property's own mapped form, for use
     * when the owning entity has no explicit simple identity configured. Returns
     * {@link Optional#empty()} when the mapped form is absent, {@code typeParams} is {@code null},
     * or {@code typeParams} is empty.
     *
     * @return an {@link Optional} containing the constructed identity, or empty if the property
     *         carries no generator type parameters
     */
    default Optional<HibernateSimpleIdentity> buildPropertyIdentity() {
        PropertyConfig mappedForm = getHibernateMappedForm()
        Properties typeParams = mappedForm != null ? mappedForm.getTypeParams() : null
        if (typeParams == null || typeParams.isEmpty()) {
            return Optional.<HibernateSimpleIdentity>empty()
        }
        Map<String, String> params = new LinkedHashMap<>()
        for (Map.Entry<Object, Object> entry : typeParams.entrySet()) {
            params.put(entry.getKey().toString(), entry.getValue().toString())
        }
        HibernateSimpleIdentity identity = new HibernateSimpleIdentity()
        identity.setName(getName())
        identity.setType(getType())
        identity.setParams(params)
        return Optional.of(identity)
    }

    /**
     * Marks {@code value} as sorted when the Hibernate value type requires it. Called after binding
     * so that column ordering aligns with the referenced composite identifier.
     * <p>
     * The default handles both {@link ToOne} and {@link DependantValue} — the two value types that
     * require sorted columns for composite foreign keys — because these can be produced by different
     * property types (e.g. a {@link HibernateToManyProperty} can produce a {@link DependantValue}
     * as its collection key). Subtypes may override to add property-specific behaviour.
     *
     * @param value the Hibernate {@link SimpleValue} produced for this property
     */
    default void markValueSorted(SimpleValue value) {
        if (value instanceof ToOne) {
            ((ToOne) value).setSorted(true)
        } else if (value instanceof DependantValue) {
            ((DependantValue) value).setSorted(true)
        }
    }

}
