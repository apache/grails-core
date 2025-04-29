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
package org.grails.datastore.mapping.model;

import java.util.List;

import org.grails.datastore.mapping.model.lifecycle.Initializable;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.reflect.EntityReflector;

/**
 * Represents a persistent entity.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface PersistentEntity extends Initializable {

    /**
     * The entity name including any package prefix
     *
     * @return The entity name
     */
    String getName();

    /**
     * Whether this PersistentEntity is mapped using a different store. Used for cross store persistence.
     *
     * @return true if this entity is externally mapped
     */
    boolean isExternal();

    /**
     * @return Whether the entity is multi tenant
     */
    boolean isMultiTenant();

    /**
     * The composite id
     *
     * @return The composite id or null if there isn't one
     */
    PersistentProperty[] getCompositeIdentity();

    /**
     * @return The tenant id
     */
    TenantId getTenantId();

    /**
     * Whether this PersistentEntity is mapped using a different store. Used for cross store persistence.
     * @param external true if this entity is externally mapped
     */
    void setExternal(boolean external);

    /**
     * Returns the identity of the instance
     *
     * @return The identity or null if there isn't one
     */
    PersistentProperty getIdentity();

    /**
     * Returns the version property.
     *
     * @return the property
     */
    PersistentProperty getVersion();

    /**
     * Is the entity versioned for optimistic locking.
     *
     * @return true if versioned
     */
    boolean isVersioned();

    /**
     * A list of properties to be persisted
     * @return A list of PersistentProperty instances
     */
    List<PersistentProperty> getPersistentProperties();

    /**
     * A list of the associations for this entity. This is typically
     * a subset of the list returned by {@link #getPersistentProperties()}
     *
     * @return A list of associations
     */
    List <Association> getAssociations();

    /**
     * A list of embedded associations for this entity. This is typically
     * a subset of the list returned by {@link #getPersistentProperties()}
     *
     * @return A list of associations
     */
    List <Embedded> getEmbedded();

    /**
     * Obtains a PersistentProperty instance by name
     *
     * @param name The name of the property
     * @return The PersistentProperty or null if it doesn't exist
     */
    PersistentProperty getPropertyByName(String name);

    /**
     * @return The underlying Java class for this entity
     */
    Class getJavaClass();

    /**
     * Tests whether the given instance is an instance of this persistent entity
     *
     * @param obj The object
     * @return True if it is
     */
    boolean isInstance(Object obj);

    /**
     * Defines the mapping between this persistent entity
     * and an external form
     *
     * @return The ClassMapping instance
     */
    ClassMapping getMapping();

    /**
     * Constructs a new instance
     * @return The new instnace
     */
    Object newInstance();

    /**
     * A list of property names that a persistent
     * @return A List of strings
     */
    List<String> getPersistentPropertyNames();

    /**
     * @return Returns the name of the class decapitalized form
     */
    String getDecapitalizedName();

    /**
     * Returns whether the specified entity asserts ownership over this
     * entity
     *
     * @param owner The owning entity
     * @return True if it does own this entity
     */
    boolean isOwningEntity(PersistentEntity owner);

    /**
     * Returns the parent entity of this entity
     * @return The ParentEntity instance
     */
    PersistentEntity getParentEntity();

    /**
     * Obtains the root entity of an inheritance hierarchy
     * @return The root entity
     */
    PersistentEntity getRootEntity();

    /**
     * Whether this entity is a root entity
     * @return True if it is a root entity
     */
    boolean isRoot();

    /**
     * The discriminator used when persisting subclasses of an inheritance hierarchy
     * @return The discriminator
     */
    String getDiscriminator();

    /**
     * Obtains the MappingContext where this PersistentEntity is defined
     * @return The MappingContext instance
     */
    MappingContext getMappingContext();

    /**
     * Checks whether an entity has a bean property of the given name and type
     * @param name The name
     * @param type The type
     * @return True if it does
     */
    boolean hasProperty(String name, Class type);

    /**
     * True if the given property is the identifier
     * @param propertyName the property name
     * @return True if it is the identifier
     */
    boolean isIdentityName(String propertyName);

    /**
     * @return The reflector this entity
     */
    EntityReflector getReflector();

    /**
     * Add a class to the owners list of this PersistentEntity
     * @param type The class to add
     * @return True if the operation was successful
     */
    boolean addOwner(Class type);
}
