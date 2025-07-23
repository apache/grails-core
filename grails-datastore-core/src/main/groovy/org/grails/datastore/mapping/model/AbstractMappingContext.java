/* Copyright (C) 2010-2025 the original author or authors.
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

import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.grails.datastore.mapping.config.AbstractGormMappingFactory;
import org.grails.datastore.mapping.config.ConfigurationUtils;
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings;
import org.grails.datastore.mapping.engine.BeanEntityAccess;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.lifecycle.Initializable;
import org.grails.datastore.mapping.model.types.conversion.DefaultConversionService;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.proxy.JavassistProxyFactory;
import org.grails.datastore.mapping.proxy.ProxyFactory;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.reflect.FieldEntityAccess;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.env.PropertyResolver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.validation.Validator;

/**
 * Abstract implementation of the MappingContext interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractMappingContext implements MappingContext, Initializable {

    public static final String GROOVY_PROXY_FACTORY_NAME = "org.grails.datastore.gorm.proxy.GroovyProxyFactory";
    public static final String JAVASIST_PROXY_FACTORY = "javassist.util.proxy.ProxyFactory";
    public static final String CONFIGURATION_PREFIX = "grails.gorm.";
    protected Collection<PersistentEntity> persistentEntities = new ConcurrentLinkedQueue<>();
    protected Map<String,PersistentEntity>  persistentEntitiesByName = new ConcurrentHashMap<>();
    protected Map<PersistentEntity,Map<String,PersistentEntity>>  persistentEntitiesByDiscriminator = new ConcurrentHashMap<>();
    protected Map<PersistentEntity,Collection<PersistentEntity>>  persistentEntitiesByParent = new ConcurrentHashMap<>();
    protected Map<PersistentEntity,Validator>  entityValidators = new ConcurrentHashMap<>();
    protected Collection<Listener> eventListeners = new ConcurrentLinkedQueue<>();
    protected GenericConversionService conversionService = new DefaultConversionService();
    protected ProxyFactory proxyFactory;
    protected ValidatorRegistry validatorRegistry;
    private boolean canInitializeEntities = true;
    private boolean initialized;
    private MultiTenancySettings.MultiTenancyMode multiTenancyMode;

    public AbstractMappingContext() {
        this.multiTenancyMode = MultiTenancySettings.MultiTenancyMode.NONE;
    }

    public AbstractMappingContext(ConnectionSourceSettings settings) {
        initialize(settings);
    }

    @Override
    public MultiTenancySettings.MultiTenancyMode getMultiTenancyMode() {
        return this.multiTenancyMode;
    }

    protected void initialize(ConnectionSourceSettings settings) {
        FieldEntityAccess.clearReflectors();
        this.multiTenancyMode = settings.getMultiTenancy().getMode();

        // initialize custom type marshallers
        MappingFactory mappingFactory = getMappingFactory();
        Iterable<CustomTypeMarshaller> customTypeMarshallers = ConfigurationUtils.findServices(settings.getCustom().getTypes(), CustomTypeMarshaller.class);
        for (CustomTypeMarshaller customTypeMarshaller : customTypeMarshallers) {
            if(customTypeMarshaller.supports(this)) {
                mappingFactory.registerCustomType(customTypeMarshaller);
            }
        }

        // default constraints and mapping
        if(mappingFactory instanceof AbstractGormMappingFactory) {
            AbstractGormMappingFactory gormMappingFactory = (AbstractGormMappingFactory) mappingFactory;
            gormMappingFactory.setDefaultConstraints(settings.getDefault().getConstraints());
            gormMappingFactory.setDefaultMapping(settings.getDefault().getMapping());
        }
    }

    public ConversionService getConversionService() {
        return conversionService;
    }

    public ConverterRegistry getConverterRegistry() {
        return conversionService;
    }

    public void setCanInitializeEntities(boolean canInitializeEntities) {
        this.canInitializeEntities = canInitializeEntities;
    }

    public abstract MappingFactory getMappingFactory();

    public void configure(PropertyResolver configuration) {
        if(configuration == null) {
            return;
        }

        String simpleName = Introspector.decapitalize(getClass().getSimpleName());
        String suffix = "MappingContext";

        if(simpleName.endsWith(suffix)) {
            simpleName = simpleName.substring(0, simpleName.length() - suffix.length());
        }

        String prefix = CONFIGURATION_PREFIX + simpleName;
        String configurationKey = prefix + ".custom.types";

        Iterable<CustomTypeMarshaller> customTypeMarshallers = ConfigurationUtils.findServices(configuration, configurationKey, CustomTypeMarshaller.class);
        for (CustomTypeMarshaller customTypeMarshaller : customTypeMarshallers) {
            if(customTypeMarshaller.supports(this)) {
                getMappingFactory().registerCustomType(customTypeMarshaller);
            }
        }
    }

    @Override
    public ProxyHandler getProxyHandler() {
        return getProxyFactory();
    }

    public ProxyFactory getProxyFactory() {
        if (this.proxyFactory == null) {
            ClassLoader classLoader = AbstractMappingContext.class.getClassLoader();
            if (ClassUtils.isPresent(JAVASIST_PROXY_FACTORY, classLoader)) {
                proxyFactory = DefaultProxyFactoryCreator.create();
            }
            else if (ClassUtils.isPresent(GROOVY_PROXY_FACTORY_NAME, classLoader)) {
                try {
                    proxyFactory = (ProxyFactory) BeanUtils.instantiateClass(ClassUtils.forName(GROOVY_PROXY_FACTORY_NAME, classLoader));
                } catch (ClassNotFoundException e) {
                    proxyFactory = DefaultProxyFactoryCreator.create();
                }
            }
            else {
                proxyFactory = DefaultProxyFactoryCreator.create();
            }
        }
        return proxyFactory;
    }

    public void addMappingContextListener(Listener listener) {
        if (listener != null)
            eventListeners.add(listener);
    }

    public void setProxyFactory(ProxyFactory factory) {
        if (factory != null) {
            this.proxyFactory = factory;
        }
    }

    @Override
    public void setValidatorRegistry(ValidatorRegistry validatorRegistry) {
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public ValidatorRegistry getValidatorRegistry() {
        return validatorRegistry;
    }

    private static class DefaultProxyFactoryCreator {
        public static ProxyFactory create() {
            return new JavassistProxyFactory();
        }
    }

    public void addTypeConverter(Converter converter) {
        conversionService.addConverter(converter);
    }

    /**
     * Retrieves a validator for an entity
     *
     * @param entity The entity The entity
     * @return The validator or null if there is none
     */
    public Validator getEntityValidator(PersistentEntity entity) {
        if (entity != null) {

            Validator validator = entityValidators.get(entity);
            if(validator == null && validatorRegistry != null) {
                Validator v = validatorRegistry.getValidator(entity);
                if(v != null) {
                    entityValidators.put(entity, v);
                    return v;
                }
            }
            return validator;
        }
        return null;
    }

    /**
     * Adds a validator for an entity
     *
     * @param entity The PersistentEntity The entity
     * @param validator The validator The validator
     */
    public void addEntityValidator(PersistentEntity entity, Validator validator) {
        if (entity != null && validator != null) {
            entityValidators.put(entity, validator);
        }
    }

    /**
     * Adds an external PersistentEntity instance, one that is not managed and persisted by this context
     *
     * @param javaClass The Java class representing the entity
     * @return The PersistentEntity instance
     */
    public PersistentEntity addExternalPersistentEntity(Class javaClass) {
        Assert.notNull(javaClass, "PersistentEntity class cannot be null");

        PersistentEntity entity = persistentEntitiesByName.get(javaClass.getName());

        if (entity == null) {
            entity = addPersistentEntityInternal(javaClass, true, canInitializeEntities);
        }

        return entity;
    }

    /**
     * Adds a PersistentEntity instance
     *
     * @param javaClass The Java class representing the entity
     * @param override  Whether to override an existing entity
     * @return The PersistentEntity instance
     */
    public PersistentEntity addPersistentEntity(Class javaClass, boolean override) {
        Assert.notNull(javaClass, "PersistentEntity class cannot be null");
        if (override) {
            return addPersistentEntityInternal(javaClass, false, canInitializeEntities);
        }
        return addPersistentEntity(javaClass);
    }

    public Collection<PersistentEntity> addPersistentEntities(Class... javaClasses) {
        Collection<PersistentEntity> entities = new ArrayList<>();

        for (Class javaClass : javaClasses) {
            PersistentEntity entity = createPersistentEntity(javaClass);
            if(entity == null) continue;

            registerEntityWithContext(entity);
            entities.add(entity);

        }
        if(canInitializeEntities) {
            for (PersistentEntity entity : entities) {
                initializePersistentEntity(entity);
            }
        }
        for (Listener eventListener : eventListeners) {
            for (PersistentEntity entity : entities) {
                eventListener.persistentEntityAdded(entity);
            }
        }
        return entities;
    }

    /**
     * Adds a PersistentEntity instance
     *
     * @param javaClass The Java class representing the entity
     * @return The PersistentEntity instance
     */
    public PersistentEntity addPersistentEntity(Class javaClass) {
        Assert.notNull(javaClass, "PersistentEntity class cannot be null");

        PersistentEntity entity = persistentEntitiesByName.get(javaClass.getName());

        if (entity == null) {
            entity = addPersistentEntityInternal(javaClass, false, canInitializeEntities);
        }

        return entity;
    }

    private PersistentEntity addPersistentEntityInternal(Class javaClass, boolean isExternal, boolean isInitialize) {
        PersistentEntity entity = createPersistentEntity(javaClass, isExternal);
        if (entity == null) {
            return null;
        }
        entity.setExternal(isExternal);

        registerEntityWithContext(entity);

        if(isInitialize) {
            initializePersistentEntity(entity);
        }

        for (Listener eventListener : eventListeners) {
            eventListener.persistentEntityAdded(entity);
        }
        return entity;
    }

    private void registerEntityWithContext(PersistentEntity entity) {
        persistentEntities.remove(entity);
        persistentEntities.add(entity);
        persistentEntitiesByName.put(entity.getName(), entity);

    }

    public void initialize() {
        for(PersistentEntity entity : persistentEntities) {
            initializePersistentEntity(entity);
            FieldEntityAccess.getOrIntializeReflector(entity);
        }
        this.initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void initializePersistentEntity(PersistentEntity entity) {
        try {
            entity.initialize();
        }
        catch(IllegalMappingException x) {
            persistentEntities.remove(entity);
            persistentEntitiesByName.remove(entity.getName());
            throw x;
        }

        if (!entity.isRoot()) {
            PersistentEntity parent = entity.getParentEntity();

            Collection<PersistentEntity> directChildren = persistentEntitiesByParent.get(parent);
            if (directChildren == null) {
                directChildren = new HashSet<>();
                persistentEntitiesByParent.put(parent, directChildren);
            }
            directChildren.add(entity);

            while (parent != null) {
                if(!parent.isInitialized()) {
                    parent.initialize();
                }

                Map<String, PersistentEntity> children = persistentEntitiesByDiscriminator.get(parent);
                if (children == null) {
                    children = new ConcurrentHashMap<>();
                    persistentEntitiesByDiscriminator.put(parent, children);
                }
                children.put(entity.getDiscriminator(), entity);

                parent = parent.getParentEntity();
            }
        }

    }

    /**
     * Returns true if the given entity is in an inheritance hierarchy
     *
     * @param entity The entity
     * @return True if it is
     */
    public boolean isInInheritanceHierarchy(PersistentEntity entity) {
        if (entity != null) {
            if (!entity.isRoot()) return true;
            PersistentEntity rootEntity = entity.getRootEntity();
            final Map<String, PersistentEntity> children = persistentEntitiesByDiscriminator.get(rootEntity);
            if (children != null && !children.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<PersistentEntity> getChildEntities(PersistentEntity root) {
        final Map<String, PersistentEntity> children = persistentEntitiesByDiscriminator.get(root);
        if(children != null) {
            return Collections.unmodifiableCollection(children.values());
        }
        else {
            return Collections.emptySet();
        }
    }

    @Override
    public Collection<PersistentEntity> getDirectChildEntities(PersistentEntity root) {
        Collection<PersistentEntity> entities = persistentEntitiesByParent.get(root);
        if(entities == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(entities);
    }

    public PersistentEntity getChildEntityByDiscriminator(PersistentEntity root, String discriminator) {
        final Map<String, PersistentEntity> children = persistentEntitiesByDiscriminator.get(root);
        if (children != null) {
            return children.get(discriminator);
        }
        return null;
    }

    protected abstract PersistentEntity createPersistentEntity(Class javaClass);

    protected Object resolveMappingStrategy(Class javaClass){
        try {
            Field field = javaClass.getDeclaredField(GormProperties.MAPPING_STRATEGY);
            if(field != null && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field.get(javaClass);
            }
        } catch (Throwable e) {
            return null;
        }
        return null;
    }

    protected boolean isValidMappingStrategy(Class javaClass, Object mappingStrategy) {
        if(mappingStrategy == null) {
            return true;
        }

        String name = getClass().getSimpleName();
        String suffix = MappingContext.class.getSimpleName();
        if(name.endsWith(suffix)) {
            name = name.substring(0, name.length() - suffix.length()).toLowerCase(Locale.ENGLISH);
            return name.equalsIgnoreCase(mappingStrategy.toString());
        }
        return false;
    }

    protected PersistentEntity createPersistentEntity(Class javaClass, boolean external) {
        return createPersistentEntity(javaClass);
    }

    public PersistentEntity createEmbeddedEntity(Class type) {
        EmbeddedPersistentEntity embedded = new EmbeddedPersistentEntity(type, this);
        embedded.initialize();
        return embedded;
    }

    public Collection<PersistentEntity> getPersistentEntities() {
        return persistentEntities;
    }

    public boolean isPersistentEntity(Class type) {
        return type != null && getPersistentEntity(type.getName()) != null;

    }

    public boolean isPersistentEntity(Object value) {
        return value != null && isPersistentEntity(value.getClass());
    }

    public PersistentEntity getPersistentEntity(String name) {
        final int proxyIndicator = name.indexOf("_$$_");
        if (proxyIndicator > -1) {
            name = name.substring(0, proxyIndicator);
        }

        return persistentEntitiesByName.get(name);
    }

    /**
     * Obtains a {@link EntityReflector} instance for the given entity
     *
     * @param entity The entity
     * @return The class data
     */
    @Override
    public EntityReflector getEntityReflector(PersistentEntity entity) {
        return FieldEntityAccess.getOrIntializeReflector(entity);
    }

    @Override
    public EntityAccess createEntityAccess(PersistentEntity entity, Object instance) {
        final ProxyFactory proxyFactory = getProxyFactory();
        instance = proxyFactory.unwrap(instance);
        if(entity != null) {

            if (entity.getJavaClass() != instance.getClass()) {
                // try subclass
                final PersistentEntity subEntity = getPersistentEntity(instance.getClass().getName());
                if (subEntity != null) {
                    entity = subEntity;
                }
            }
            return new FieldEntityAccess(entity, instance, getConversionService());
        }
        else {
            EntityReflector reflector = FieldEntityAccess.getReflector(instance.getClass().getName());
            if(reflector != null) {
                return new FieldEntityAccess(reflector.getPersitentEntity(), instance, conversionService);
            }
            else {
                return new BeanEntityAccess(null, instance);
            }
        }
    }
}
