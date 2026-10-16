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
package org.grails.datastore.mapping.model.types

import java.beans.PropertyDescriptor

import groovy.transform.CompileStatic
import jakarta.persistence.CascadeType
import jakarta.persistence.FetchType

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.AbstractPersistentProperty
import org.grails.datastore.mapping.model.IllegalMappingException
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.validation.CascadeValidateType

/**
 * Models an association between one class and another
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings('rawtypes')
abstract class Association<T extends Property> extends AbstractPersistentProperty<T> {

    private static final Set<CascadeType> DEFAULT_OWNER_CASCADE = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(CascadeType.ALL)))

    private static final Set<CascadeType> DEFAULT_CHILD_CASCADE = Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(CascadeType.PERSIST)))

    private PersistentEntity associatedEntity
    private String referencedPropertyName
    private boolean owningSide
    private boolean orphanRemoval = false

    private Set<CascadeType> cascadeOperations
    private CascadeValidateType cascadeValidateType

    private static final Map<String, CascadeType> cascadeTypeConversions = new LinkedHashMap<>()

    static {
        cascadeTypeConversions.put('all', CascadeType.ALL)
        cascadeTypeConversions.put('all-delete-orphan', CascadeType.ALL)
        cascadeTypeConversions.put('merge', CascadeType.MERGE)
        cascadeTypeConversions.put('save-update', CascadeType.PERSIST)
        cascadeTypeConversions.put('delete', CascadeType.REMOVE)
        cascadeTypeConversions.put('remove', CascadeType.REMOVE)
        cascadeTypeConversions.put('refresh', CascadeType.REFRESH)
        cascadeTypeConversions.put('persist', CascadeType.PERSIST)
        // Unsupported Types
        // "all-delete-orphan", "lock", "replicate", "evict", "delete-orphan"
    }

    Association(PersistentEntity owner, MappingContext context, PropertyDescriptor descriptor) {
        super(owner, context, descriptor)
    }

    Association(PersistentEntity owner, MappingContext context, String name, Class type) {
        super(owner, context, name, type)
    }

    /**
     * @return The fetch strategy for the association
     */
    FetchType getFetchStrategy() {
        return getMapping().getMappedForm().getFetchStrategy()
    }

    /**
     * @return True if the association is bidirectional
     */
    boolean isBidirectional() {
        return associatedEntity != null && referencedPropertyName != null
    }

    /**
     * @return Whether orphaned entities should be removed when cascading deletes to this association
     */
    boolean isOrphanRemoval() {
        return orphanRemoval
    }

    /**
     * @return The inverse side or null if the association is not bidirectional
     */
    Association getInverseSide() {
        final PersistentProperty associatedProperty = associatedEntity.getPropertyByName(referencedPropertyName)
        if (associatedProperty == null) {
            return null
        }
        if (associatedProperty instanceof Association) {
            return (Association) associatedProperty
        }
        throw new IllegalMappingException('The inverse side [' + associatedEntity.getName() + '.' +
                associatedProperty.getName() + '] of the association [' + getOwner().getName() + '.' +
                getName() + '] is not valid. Associations can only map to other entities and collection types.')
    }

    /**
     * Returns true if the this association cascade for the given cascade operation
     *
     * @param cascadeOperation The cascadeOperation
     * @return True if it does
     */
    boolean doesCascade(CascadeType cascadeOperation) {
        return doesCascade([cascadeOperation] as CascadeType[])
    }

    /**
     * Returns true if this association cascades for the given cascade operation
     *
     * @param operations The cascadeOperations
     * @return True if it does
     */
    boolean doesCascade(CascadeType... operations) {
        Set<CascadeType> cascades = getCascadeOperations()
        if (cascades.contains(CascadeType.ALL)) {
            return true
        }
        else if (operations != null) {
            for (CascadeType cascadeOperation in operations) {
                if (cascades.contains(cascadeOperation)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Returns true if this association should cascade validation to the given entity.
     * Note that if the object state is persisted, it may still be validated as part of the object graph.
     *
     * @param associatedObject The associated object that may or may not be validated further
     * @return True if validation should cascade
     */
    boolean doesCascadeValidate(Object associatedObject) {
        CascadeValidateType validateType = getCascadeValidateOperation()

        // Never cascade validation for this association
        if (validateType == CascadeValidateType.NONE) {
            return false
        }

        // Only owned associations are eligible
        if (validateType == CascadeValidateType.OWNED) {
            return isOwningSide()
        }

        boolean defaultCascade = isOwningSide() || doesCascade(CascadeType.PERSIST, CascadeType.MERGE)

        // Only cascade if the associated object is flagged as dirty. This presumes the object wasn't loaded
        // from persistence in an invalid state, which is probably a reasonable assumption.
        if (validateType == CascadeValidateType.DIRTY && associatedObject instanceof DirtyCheckable) {
            return defaultCascade && ((DirtyCheckable) associatedObject).hasChanged()
        }

        // Default
        return defaultCascade
    }

    /**
     * @return Whether this association is embedded
     */
    boolean isEmbedded() {
        return this instanceof Embedded || this instanceof EmbeddedCollection
    }

    /**
     * @return Whether this association is embedded
     */
    boolean isBasic() {
        return this instanceof Basic
    }

    /**
     * Returns whether this side owns the relationship. This controls
     * the default cascading behavior if none is specified
     *
     * @return True if this property is the owning side
     */
    boolean isOwningSide() {
        return owningSide
    }

    /**
     * Sets whether this association is the owning side
     *
     * @param owningSide True if it is
     */
    void setOwningSide(boolean owningSide) {
        this.owningSide = owningSide
    }

    /**
     * Sets the associated entity
     *
     * @param associatedEntity The associated entity
     */
    void setAssociatedEntity(PersistentEntity associatedEntity) {
        this.associatedEntity = associatedEntity
    }

    /**
     * @return The entity associated with the this association
     */
    PersistentEntity getAssociatedEntity() {
        return associatedEntity
    }

    /**
     * Sets the name of the inverse property
     * @param referencedPropertyName The referenced property name
     */
    void setReferencedPropertyName(String referencedPropertyName) {
        this.referencedPropertyName = referencedPropertyName
    }

    /**
     * @return Returns the name of the inverse property or null if this association is unidirectional
     */
    String getReferencedPropertyName() {
        return referencedPropertyName
    }

    @Override
    String toString() {
        return getOwner().getName() + '->' + getName()
    }

    /**
     * @return Whether the association is a List
     */
    boolean isList() {
        return List.isAssignableFrom(getType())
    }

    /**
     * @return Whether the association is circular
     */
    boolean isCircular() {
        PersistentEntity associated = getAssociatedEntity()
        return associated != null && associated.getJavaClass().isAssignableFrom(owner.getJavaClass())
    }

    boolean isCorrectlyOwned() {
        PersistentEntity associated = getAssociatedEntity()
        return associated != null && associated.isOwningEntity(getOwner())
    }

    protected Set<CascadeType> getCascadeOperations() {
        if (cascadeOperations == null) {
            buildCascadeOperations()
        }
        return cascadeOperations
    }

    protected CascadeValidateType getCascadeValidateOperation() {
        if (cascadeValidateType == null) {
            cascadeValidateType = initializeCascadeValidateType()
        }
        return cascadeValidateType
    }

    /**
     * It is possible this method could be called multiple times in some threaded initialization scenarios.
     * It needs to either remain idempotent or have the synchronization beefed up if that precondition ever changes.
     */
    private synchronized void buildCascadeOperations() {
        T mappedForm = this.getMapping().getMappedForm()
        this.orphanRemoval = mappedForm.isOrphanRemoval()
        final String cascade = mappedForm.getCascade()
        if (cascade != null) {
            final String[] specifiedOperations = cascade.toLowerCase().split(',')
            Set<CascadeType> operations = new HashSet<>()
            for (String operation in specifiedOperations) {
                final String key = operation.trim()
                if (cascadeTypeConversions.containsKey(key)) {
                    operations.add(cascadeTypeConversions.get(key))
                }
                if (key.contains('delete-orphan')) {
                    this.orphanRemoval = true
                }
            }
            this.cascadeOperations = Collections.unmodifiableSet(operations)
        }
        else {
            List<CascadeType> cascades = mappedForm.getCascades()
            if (cascades != null) {
                this.cascadeOperations = Collections.unmodifiableSet(new HashSet<>(cascades))
            }
            else if (isOwningSide()) {
                this.cascadeOperations = DEFAULT_OWNER_CASCADE
            }
            else {
                if ((this instanceof ManyToOne) && isBidirectional()) {
                    // don't cascade by default to many-to-one that is not owned
                    this.cascadeOperations = Collections.emptySet()
                }
                else {
                    this.cascadeOperations = DEFAULT_CHILD_CASCADE
                }
            }
        }
    }

    /**
     * It is possible this method could be called multiple times in some threaded initialization scenarios.
     * It needs to either remain idempotent or have the synchronization beefed up if that precondition ever changes.
     */
    private synchronized CascadeValidateType initializeCascadeValidateType() {
        T mappedForm = this.getMapping().getMappedForm()
        final String cascade = mappedForm.getCascadeValidate()
        return cascade != null ? CascadeValidateType.fromMappedName(cascade) : CascadeValidateType.DEFAULT
    }

    boolean isHasOne() {
        return this instanceof OneToOne && ((ToOne) this).isForeignKeyInChild()
    }

    boolean isOneToOne() {
        return this instanceof OneToOne
    }

    boolean isOneToMany() {
        return this instanceof OneToMany
    }

    boolean isManyToMany() {
        return this instanceof ManyToMany
    }

    boolean isManyToOne() {
        return this instanceof ManyToOne
    }

    boolean canBindOneToOneWithSingleColumnAndForeignKey() {
        if (!isBidirectional()) {
            return false
        }
        Association otherSide = getInverseSide()
        if (otherSide == null || otherSide.isHasOne()) {
            return false
        }
        return !this.isOwningSide() && otherSide.isOwningSide()
    }

    boolean isBidirectionalToManyMap() {
        return Map.isAssignableFrom(this.getType()) && this.isBidirectional()
    }

}
