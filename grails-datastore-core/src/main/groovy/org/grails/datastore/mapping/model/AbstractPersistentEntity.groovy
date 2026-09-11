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

import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.annotation.Annotation
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import org.springframework.beans.BeanUtils
import org.springframework.util.Assert

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.core.EntityCreationException
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.Identity
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.EntityReflector

/**
 * Abstract implementation to be subclasses on a per datastore basis
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings(['rawtypes', 'unchecked'])
abstract class AbstractPersistentEntity<T extends Entity> implements PersistentEntity {

    protected final Class javaClass
    protected final MappingContext context
    protected List<PersistentProperty> persistentProperties
    protected List<Association> associations
    protected List<Embedded> embedded
    protected Map<String, PersistentProperty> propertiesByName = new HashMap<>()
    protected Map<String, PersistentProperty> mappedPropertiesByName = new HashMap<>()
    protected PersistentProperty identity
    protected PersistentProperty version
    protected List<String> persistentPropertyNames
    private final String decapitalizedName
    protected Set owners
    private PersistentEntity parentEntity
    private boolean external
    private boolean initialized = false
    private boolean propertiesInitialized = false
    private boolean versionCompatibleType
    private boolean versioned = true
    private PersistentProperty[] compositeIdentity
    private final String mappingStrategy
    private final boolean abstractClass
    private EntityReflector entityReflector
    private final boolean multiTenantClass
    private TenantId tenantId

    AbstractPersistentEntity(Class javaClass, MappingContext context) {
        Assert.notNull(javaClass, 'The argument [javaClass] cannot be null')
        this.javaClass = javaClass
        this.context = context
        this.abstractClass = Modifier.isAbstract(javaClass.getModifiers())
        this.multiTenantClass = ClassUtils.isMultiTenant(javaClass)
        decapitalizedName = Introspector.decapitalize(javaClass.getSimpleName())
        String classSpecified = ClassPropertyFetcher.getStaticPropertyValue(javaClass, GormProperties.MAPPING_STRATEGY, String)
        this.mappingStrategy = classSpecified != null ? classSpecified : GormProperties.DEFAULT_MAPPING_STRATEGY
    }

    @Override
    PersistentProperty[] getCompositeIdentity() {
        return compositeIdentity
    }

    TenantId getTenantId() {
        return tenantId
    }

    @Override
    boolean isMultiTenant() {
        return this.multiTenantClass
    }

    boolean isExternal() {
        return external
    }

    boolean isAbstract() {
        return abstractClass
    }

    String getMappingStrategy() {
        return this.mappingStrategy
    }

    void setExternal(boolean external) {
        this.external = external
    }

    MappingContext getMappingContext() {
        return context
    }

    boolean isInitialized() {
        return initialized
    }

    void initialize() {
        ClassMapping<T> mapping = getMapping()
        if (!initialized) {
            initialized = true

            final MappingConfigurationStrategy mappingSyntaxStrategy = context.getMappingSyntaxStrategy()
            owners = mappingSyntaxStrategy.getOwningEntities(javaClass, context)
            Class superClass = javaClass.getSuperclass()
            if (superClass != null &&
                    superClass != Object) {
                if (mappingSyntaxStrategy.isPersistentEntity(superClass)) {
                    parentEntity = context.addPersistentEntity(superClass)
                }
            }

            persistentProperties = mappingSyntaxStrategy.getPersistentProperties(this, context, mapping, includeIdentifiers())

            persistentPropertyNames = new ArrayList<>()
            associations = new ArrayList()
            embedded = new ArrayList()

            boolean multiTenancyEnabled = multiTenantClass && context.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
            for (PersistentProperty persistentProperty in persistentProperties) {
                if (multiTenancyEnabled && persistentProperty instanceof TenantId) {
                    this.tenantId = (TenantId) persistentProperty
                }
                if (persistentProperty instanceof Identity) {
                    if (compositeIdentity != null) {
                        int l = compositeIdentity.length
                        compositeIdentity = Arrays.copyOf(compositeIdentity, l + 1)
                        compositeIdentity[l] = identity
                    }
                    else if (identity != null) {
                        compositeIdentity = [identity, persistentProperty] as PersistentProperty[]
                        identity = null
                    }
                    else {
                        identity = persistentProperty
                    }
                }

                if (!(persistentProperty instanceof OneToMany)) {
                    persistentPropertyNames.add(persistentProperty.getName())
                }

                if (persistentProperty instanceof Association) {
                    associations.add((Association) persistentProperty)
                }
                if (persistentProperty instanceof Embedded) {
                    embedded.add((Embedded) persistentProperty)
                }
                propertiesByName.put(persistentProperty.getName(), persistentProperty)
                final String targetName = persistentProperty.getMapping().getMappedForm().getTargetName()
                if (targetName != null) {
                    mappedPropertiesByName.put(targetName, persistentProperty)
                }
            }
            if (associations.isEmpty()) {
                associations = Collections.emptyList()
            }
            if (embedded.isEmpty()) {
                embedded = Collections.emptyList()
            }

            if (identity == null && compositeIdentity == null) {
                identity = resolveIdentifier()
            }

            if (multiTenancyEnabled && tenantId == null) {
                throw new ConfigurationException('Class [' + javaClass.getName() + '] is multi tenant but does not specify a tenant identifier property')
            }

            if (!isExternal()) {
                final T mappedForm = mapping.getMappedForm() // initialize mapping

                if (mappedForm.isVersioned()) {
                    version = propertiesByName.get(GormProperties.VERSION)
                    if (version == null) {
                        versioned = false
                    }
                }
                else {
                    versioned = false
                }
            }

            final PersistentProperty v = getVersion()
            if (v != null) {
                final Class type = v.getType()
                this.versionCompatibleType = Number.isAssignableFrom(type) || Date.isAssignableFrom(type)
            }

            if (identity != null) {
                String idName = identity.getName()
                PersistentProperty idProp = propertiesByName.get(idName)
                if (idProp == null) {
                    propertiesByName.put(idName, identity)
                }
                else {
                    persistentProperties.remove(idProp)
                    persistentPropertyNames.remove(idProp.getName())
                    if (idProp.getName() != GormProperties.IDENTITY) {
                        disableDefaultId()
                    }
                }
            }
            IdentityMapping identifier = mapping != null ? mapping.getIdentifier() : null
            if (identity == null && identifier != null) {
                final String[] identifierName = identifier.getIdentifierName()
                final MappingContext mappingContext = getMappingContext()
                if (identifierName.length > 1) {
                    compositeIdentity = mappingContext.getMappingSyntaxStrategy().getCompositeIdentity(javaClass, mappingContext)
                }
                for (String identifierProperty in identifierName) {
                    final PersistentProperty p = propertiesByName.get(identifierProperty)
                    if (p != null) {
                        persistentProperties.remove(p)
                    }
                    persistentPropertyNames.remove(identifierProperty)
                }
                disableDefaultId()
            }
        }

        propertiesInitialized = true
        this.entityReflector = getMappingContext().getEntityReflector(this)
    }

    private void disableDefaultId() {
        PersistentProperty otherId = getPropertyByName(GormProperties.IDENTITY)
        if (otherId != null) {
            persistentProperties.remove(otherId)
            persistentPropertyNames.remove(GormProperties.IDENTITY)
        }
    }

    @Override
    EntityReflector getReflector() {
        return this.entityReflector
    }

    protected boolean isAnnotatedSuperClass(MappingConfigurationStrategy mappingSyntaxStrategy, Class superClass) {
        Annotation[] annotations = superClass.getAnnotations()
        for (Annotation annotation in annotations) {
            String name = annotation.annotationType().getName()
            if (name == 'grails.persistence.Entity' || name == 'grails.gorm.annotation.Entity') {
                return true
            }
        }
        return false
    }

    protected boolean includeIdentifiers() {
        return true
    }

    protected PersistentProperty resolveIdentifier() {
        return context.getMappingSyntaxStrategy().getIdentity(javaClass, context)
    }

    boolean hasProperty(String name, Class type) {
        final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(getJavaClass(), name)
        return pd != null && pd.getPropertyType() == type
    }

    boolean isIdentityName(String propertyName) {
        PersistentProperty identity = getIdentity()
        if (identity != null) {
            return identity.getName() == propertyName
        }
        return GormProperties.IDENTITY == propertyName
    }

    PersistentEntity getParentEntity() {
        return parentEntity
    }

    String getDiscriminator() {
        return getJavaClass().getSimpleName()
    }

    PersistentEntity getRootEntity() {
        PersistentEntity root = this
        PersistentEntity parent = getParentEntity()
        while (parent != null) {
            if (!parent.isInitialized()) {
                parent.initialize()
            }
            root = parent
            parent = parent.getParentEntity()
        }
        return root
    }

    boolean isRoot() {
        return getParentEntity() == null
    }

    boolean isOwningEntity(PersistentEntity owner) {
        return owner != null && owners.contains(owner.getJavaClass())
    }

    String getDecapitalizedName() {
        return decapitalizedName
    }

    List<String> getPersistentPropertyNames() {
        return persistentPropertyNames
    }

    ClassMapping<T> getMapping() {
        return (ClassMapping<T>) new AbstractClassMapping<Entity>(this, context) {
            @Override
            Entity getMappedForm() {
                return new Entity()
            }
        }
    }

    Object newInstance() {
        try {
            return getJavaClass().getDeclaredConstructor().newInstance()
        }
        catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new EntityCreationException('Unable to create entity of type [' + getJavaClass().getName() +
                    ']: ' + e.getMessage(), e)
        }
    }

    String getName() {
        return javaClass.getName()
    }

    PersistentProperty getIdentity() {
        return identity
    }

    PersistentProperty getVersion() {
        return version
    }

    boolean isVersioned() {
        return (this.versionCompatibleType || !propertiesInitialized) && versioned
    }

    Class<?> getJavaClass() {
        return javaClass
    }

    boolean isInstance(Object obj) {
        return getJavaClass().isInstance(obj)
    }

    List<PersistentProperty> getPersistentProperties() {
        return persistentProperties
    }

    List<Association> getAssociations() {
        return associations
    }

    @Override
    List<Embedded> getEmbedded() {
        return embedded
    }

    PersistentProperty getPropertyByName(String name) {
        PersistentProperty pp = propertiesByName.get(name)
        if (pp != null) {
            return pp
        }
        return mappedPropertiesByName.get(name)
    }

    @Override
    int hashCode() {
        return javaClass.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (o == null || !(o instanceof PersistentEntity)) {
            return false
        }
        if (this.is(o)) {
            return true
        }

        PersistentEntity other = (PersistentEntity) o
        return javaClass == other.getJavaClass()
    }

    @Override
    String toString() {
        return javaClass.getName()
    }

    boolean addOwner(Class type) {
        return owners.add(type)
    }

}
