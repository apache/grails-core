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

import org.grails.datastore.mapping.config.Entity;
import org.grails.datastore.mapping.core.EntityCreationException;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.datastore.mapping.model.types.Identity;
import org.grails.datastore.mapping.model.types.OneToMany;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abstract implementation to be subclasses on a per datastore basis
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractPersistentEntity<T extends Entity> implements PersistentEntity {

    protected final Class javaClass;
    protected final MappingContext context;
    protected List<PersistentProperty> persistentProperties;
    protected List<Association> associations;
    protected List<Embedded> embedded;
    protected Map<String, PersistentProperty> propertiesByName = new HashMap<String, PersistentProperty>();
    protected Map<String, PersistentProperty> mappedPropertiesByName = new HashMap<String, PersistentProperty>();
    protected PersistentProperty identity;
    protected PersistentProperty version;
    protected List<String> persistentPropertyNames;
    protected Set owners;
    private final String decapitalizedName;
    private PersistentEntity parentEntity;
    private boolean external;
    private boolean initialized = false;
    private boolean propertiesInitialized = false;
    private boolean versionCompatibleType;
    private boolean versioned = true;
    private PersistentProperty[] compositeIdentity;
    private final String mappingStrategy;
    private final boolean isAbstract;
    private EntityReflector entityReflector;
    private final boolean isMultiTenant;
    private TenantId tenantId;

    public AbstractPersistentEntity(Class javaClass, MappingContext context) {
        Assert.notNull(javaClass, "The argument [javaClass] cannot be null");
        this.javaClass = javaClass;
        this.context = context;
        this.isAbstract = Modifier.isAbstract(javaClass.getModifiers());
        this.isMultiTenant = org.grails.datastore.mapping.reflect.ClassUtils.isMultiTenant(javaClass);
        decapitalizedName = Introspector.decapitalize(javaClass.getSimpleName());
        String classSpecified = ClassPropertyFetcher.getStaticPropertyValue(javaClass, GormProperties.MAPPING_STRATEGY, String.class);
        this.mappingStrategy = classSpecified != null ? classSpecified : GormProperties.DEFAULT_MAPPING_STRATEGY;
    }

    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return compositeIdentity;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    @Override
    public boolean isMultiTenant() {
        return this.isMultiTenant;
    }

    public boolean isExternal() {
        return external;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public String getMappingStrategy() {
        return this.mappingStrategy;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public MappingContext getMappingContext() {
        return context;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void initialize() {
        ClassMapping<T> mapping = getMapping();
        if (!initialized) {

            initialized = true;

            final MappingConfigurationStrategy mappingSyntaxStrategy = context.getMappingSyntaxStrategy();
            owners = mappingSyntaxStrategy.getOwningEntities(javaClass, context);
            Class superClass = javaClass.getSuperclass();
            if (superClass != null &&
                    !superClass.equals(Object.class)) {

                if (mappingSyntaxStrategy.isPersistentEntity(superClass)) {
                    parentEntity = context.addPersistentEntity(superClass);
                }
            }

            persistentProperties = mappingSyntaxStrategy.getPersistentProperties(this, context, mapping, includeIdentifiers());

            persistentPropertyNames = new ArrayList<>();
            associations = new ArrayList();
            embedded = new ArrayList();

            boolean multiTenancyEnabled = isMultiTenant && context.getMultiTenancyMode() == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR;
            for (PersistentProperty persistentProperty : persistentProperties) {
                if (multiTenancyEnabled && persistentProperty instanceof TenantId) {
                    this.tenantId = (TenantId) persistentProperty;
                }
                if (persistentProperty instanceof Identity) {
                    if (compositeIdentity != null) {
                        int l = compositeIdentity.length;
                        compositeIdentity = Arrays.copyOf(compositeIdentity, l + 1);
                        compositeIdentity[l] = identity;
                    } else if (identity != null) {
                        compositeIdentity = new PersistentProperty[]{identity, persistentProperty};
                        identity = null;
                    } else {
                        identity = persistentProperty;
                    }
                }

                if (!(persistentProperty instanceof OneToMany)) {
                    persistentPropertyNames.add(persistentProperty.getName());
                }

                if (persistentProperty instanceof Association) {
                    associations.add((Association) persistentProperty);
                }
                if (persistentProperty instanceof Embedded) {
                    embedded.add((Embedded) persistentProperty);
                }
                propertiesByName.put(persistentProperty.getName(), persistentProperty);
                final String targetName = persistentProperty.getMapping().getMappedForm().getTargetName();
                if (targetName != null) {
                    mappedPropertiesByName.put(targetName, persistentProperty);
                }
            }
            if (associations.isEmpty()) {
                associations = Collections.emptyList();
            }
            if (embedded.isEmpty()) {
                embedded = Collections.emptyList();
            }

            if (identity == null && compositeIdentity == null) {
                identity = resolveIdentifier();
            }

            if (multiTenancyEnabled && tenantId == null) {
                throw new ConfigurationException("Class [" + javaClass.getName() + "] is multi tenant but does not specify a tenant identifier property");
            }

            if (!isExternal()) {
                final T mappedForm = mapping.getMappedForm(); // initialize mapping

                if (mappedForm.isVersioned()) {
                    version = propertiesByName.get(GormProperties.VERSION);
                    if (version == null) {
                        versioned = false;
                    }
                } else {
                    versioned = false;
                }
            }

            final PersistentProperty v = getVersion();
            if (v != null) {
                final Class type = v.getType();
                this.versionCompatibleType = Number.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type);
            }

            if (identity != null) {
                String idName = identity.getName();
                PersistentProperty idProp = propertiesByName.get(idName);
                if (idProp == null) {
                    propertiesByName.put(idName, identity);
                } else {
                    persistentProperties.remove(idProp);
                    persistentPropertyNames.remove(idProp.getName());
                    if (!idProp.getName().equals(GormProperties.IDENTITY)) {
                        disableDefaultId();
                    }
                }
            }
            IdentityMapping identifier = mapping != null ? mapping.getIdentifier() : null;
            if (identity == null && identifier != null) {

                final String[] identifierName = identifier.getIdentifierName();
                final MappingContext mappingContext = getMappingContext();
                if (identifierName.length > 1) {
                    compositeIdentity = mappingContext.getMappingSyntaxStrategy().getCompositeIdentity(javaClass, mappingContext);
                }
                for (String in : identifierName) {
                    final PersistentProperty p = propertiesByName.get(in);
                    if (p != null) {
                        persistentProperties.remove(p);
                    }
                    persistentPropertyNames.remove(in);
                }
                disableDefaultId();
            }
        }

        propertiesInitialized = true;
        this.entityReflector = getMappingContext().getEntityReflector(this);
    }

    private void disableDefaultId() {
        PersistentProperty otherId = getPropertyByName(GormProperties.IDENTITY);
        if (otherId != null) {
            persistentProperties.remove(otherId);
            persistentPropertyNames.remove(GormProperties.IDENTITY);
        }
    }

    @Override
    public EntityReflector getReflector() {
        return this.entityReflector;
    }

    protected boolean isAnnotatedSuperClass(MappingConfigurationStrategy mappingSyntaxStrategy, Class superClass) {

        Annotation[] annotations = superClass.getAnnotations();
        for (Annotation annotation : annotations) {
            String name = annotation.annotationType().getName();
            if (name.equals("grails.persistence.Entity") || name.equals("grails.gorm.annotation.Entity")) {
                return true;
            }
        }
        return false;
    }

    protected boolean includeIdentifiers() {
        return true;
    }

    protected PersistentProperty resolveIdentifier() {
        return context.getMappingSyntaxStrategy().getIdentity(javaClass, context);
    }

    public boolean hasProperty(String name, Class type) {
        final PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(getJavaClass(), name);
        return pd != null && pd.getPropertyType().equals(type);
    }

    public boolean isIdentityName(String propertyName) {
        return getIdentity().getName().equals(propertyName);
    }

    public PersistentEntity getParentEntity() {
        return parentEntity;
    }

    public String getDiscriminator() {
        return getJavaClass().getSimpleName();
    }

    public PersistentEntity getRootEntity() {
        PersistentEntity root = this;
        PersistentEntity parent = getParentEntity();
        while (parent != null) {
            if (!parent.isInitialized()) {
                parent.initialize();
            }
            root = parent;
            parent = parent.getParentEntity();
        }
        return root;
    }

    public boolean isRoot() {
        return getParentEntity() == null;
    }

    public boolean isOwningEntity(PersistentEntity owner) {
        return owner != null && owners.contains(owner.getJavaClass());
    }

    public String getDecapitalizedName() {
        return decapitalizedName;
    }

    public List<String> getPersistentPropertyNames() {
        return persistentPropertyNames;
    }

    public ClassMapping<T> getMapping() {
        return new AbstractClassMapping<Entity>(this, context) {
            @Override
            public Entity getMappedForm() {
                return new Entity();
            }
        };
    }

    public Object newInstance() {
        try {
            return getJavaClass().newInstance();
        } catch (InstantiationException e) {
            throw new EntityCreationException("Unable to create entity of type [" + getJavaClass().getName() +
                    "]: " + e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new EntityCreationException("Unable to create entity of type [" + getJavaClass().getName() +
                    "]: " + e.getMessage(), e);
        }
    }

    public String getName() {
        return javaClass.getName();
    }

    public PersistentProperty getIdentity() {
        return identity;
    }

    public PersistentProperty getVersion() {
        return version;
    }

    public boolean isVersioned() {
        return (this.versionCompatibleType || !propertiesInitialized) && versioned;
    }

    public Class getJavaClass() {
        return javaClass;
    }

    public boolean isInstance(Object obj) {
        return getJavaClass().isInstance(obj);
    }

    public List<PersistentProperty> getPersistentProperties() {
        return persistentProperties;
    }

    public List<Association> getAssociations() {
        return associations;
    }

    @Override
    public List<Embedded> getEmbedded() {
        return embedded;
    }

    public PersistentProperty getPropertyByName(String name) {
        PersistentProperty pp = propertiesByName.get(name);
        if (pp != null) {
            return pp;
        }
        return mappedPropertiesByName.get(name);
    }

    @Override
    public int hashCode() {
        return javaClass.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof PersistentEntity)) {
            return false;
        }
        if (this == o) {
            return true;
        }

        PersistentEntity other = (PersistentEntity) o;
        return javaClass.equals(other.getJavaClass());
    }

    @Override
    public String toString() {
        return javaClass.getName();
    }

    public boolean addOwner(Class type) {
        return owners.add(type);
    }

    private static class MappingProperties {

        private Boolean version = true;
        private boolean intialized = false;

        public boolean isIntialized() {
            return intialized;
        }

        public void setIntialized(boolean intialized) {
            this.intialized = intialized;
        }

        public void setVersion(final boolean version) {
            this.version = version;
        }

        public boolean isVersioned() {
            return version == null ? true : version;
        }

    }
}
