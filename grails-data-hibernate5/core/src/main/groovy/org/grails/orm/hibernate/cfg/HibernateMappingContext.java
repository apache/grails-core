/*
 * Copyright 2015 original authors
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
package org.grails.orm.hibernate.cfg;

import grails.gorm.annotation.Entity;
import grails.gorm.hibernate.HibernateEntity;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.mapping.config.AbstractGormMappingFactory;
import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.config.JpaMappingConfigurationStrategy;
import org.grails.datastore.mapping.reflect.ClassUtils;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;
import org.springframework.validation.Errors;

import java.lang.annotation.Annotation;

/**
 * A Mapping context for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernateMappingContext extends AbstractMappingContext {

    private static final String[] DEFAULT_IDENTITY_MAPPING = new String[] {GormProperties.IDENTITY};
    private final HibernateMappingFactory mappingFactory;
    private final MappingConfigurationStrategy syntaxStrategy;

    /**
     * Construct a HibernateMappingContext for the given arguments
     *
     * @param settings The {@link HibernateConnectionSourceSettings} settings
     * @param contextObject The context object (for example a Spring ApplicationContext)
     * @param persistentClasses The persistent classes
     */
    public HibernateMappingContext(HibernateConnectionSourceSettings settings, Object contextObject, Class...persistentClasses) {
        this.mappingFactory = new HibernateMappingFactory();

        // The mapping factory needs to be configured before initialize can be safely called
        initialize(settings);

        if(settings != null) {
            this.mappingFactory.setDefaultMapping(settings.getDefault().getMapping());
            this.mappingFactory.setDefaultConstraints(settings.getDefault().getConstraints());
        }
        this.mappingFactory.setContextObject(contextObject);
        this.syntaxStrategy = new JpaMappingConfigurationStrategy(mappingFactory) {
            @Override
            protected boolean supportsCustomType(Class<?> propertyType) {
                return !Errors.class.isAssignableFrom(propertyType);
            }
        };
        this.proxyFactory = new HibernateProxyHandler();
        addPersistentEntities(persistentClasses);
    }

    public HibernateMappingContext(HibernateConnectionSourceSettings settings, Class...persistentClasses) {
        this(settings, null, persistentClasses);
    }

    public HibernateMappingContext() {
        this(new HibernateConnectionSourceSettings());
    }

    /**
     * Sets the default constraints to be used
     *
     * @param defaultConstraints The default constraints
     */
    public void setDefaultConstraints(Closure defaultConstraints) {
        this.mappingFactory.setDefaultConstraints(defaultConstraints);
    }

    @Override
    public MappingConfigurationStrategy getMappingSyntaxStrategy() {
        return syntaxStrategy;
    }

    @Override
    public MappingFactory getMappingFactory() {
        return mappingFactory;
    }

    @Override
    protected PersistentEntity createPersistentEntity(Class javaClass) {
        if(GormEntity.class.isAssignableFrom(javaClass)) {
            Object mappingStrategy = resolveMappingStrategy(javaClass);
            if(isValidMappingStrategy(javaClass, mappingStrategy)) {
                return new HibernatePersistentEntity(javaClass, this);
            }
        }
        return null;
    }

    @Override
    protected boolean isValidMappingStrategy(Class javaClass, Object mappingStrategy) {
        return HibernateEntity.class.isAssignableFrom(javaClass) || super.isValidMappingStrategy(javaClass, mappingStrategy);
    }

    @Override
    protected PersistentEntity createPersistentEntity(Class javaClass, boolean external) {
        return createPersistentEntity(javaClass);
    }

    public static boolean isDomainClass(Class clazz) {
        return doIsDomainClassCheck(clazz);
    }

    private static boolean doIsDomainClassCheck(Class<?> clazz) {
        if(GormEntity.class.isAssignableFrom(clazz)) {
            return true;
        }

        // it's not a closure
        if (Closure.class.isAssignableFrom(clazz)) {
            return false;
        }

        if (clazz.isEnum()) return false;

        Annotation[] allAnnotations = clazz.getAnnotations();
        for (Annotation annotation : allAnnotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            String annName = type.getName();
            if (annName.equals("grails.persistence.Entity")) {
                return true;
            }
            if (type.equals(Entity.class)) {
                return true;
            }
        }

        Class<?> testClass = clazz;
        while (testClass != null && !testClass.equals(GroovyObject.class) && !testClass.equals(Object.class)) {
            try {
                // make sure the identify and version field exist
                testClass.getDeclaredField(GormProperties.IDENTITY);
                testClass.getDeclaredField(GormProperties.VERSION);

                // passes all conditions return true
                return true;
            }
            catch (SecurityException e) {
                // ignore
            }
            catch (NoSuchFieldException e) {
                // ignore
            }
            testClass = testClass.getSuperclass();
        }

        return false;
    }

    @Override
    public PersistentEntity createEmbeddedEntity(Class type) {
        HibernateEmbeddedPersistentEntity embedded = new HibernateEmbeddedPersistentEntity(type, this);
        embedded.initialize();
        return embedded;
    }

    @Override
    public PersistentEntity getPersistentEntity(String name) {
        final int proxyIndicator = name.indexOf("$HibernateProxy$");
        if (proxyIndicator > -1) {
            name = name.substring(0, proxyIndicator);
        }
        return super.getPersistentEntity(name);
    }

    static class HibernateEmbeddedPersistentEntity extends EmbeddedPersistentEntity {
        private final ClassMapping<Mapping> classMapping;
        public HibernateEmbeddedPersistentEntity(Class type, MappingContext ctx) {
            super(type, ctx);
            this.classMapping = new ClassMapping<Mapping>() {
                Mapping mappedForm = (Mapping) context.getMappingFactory().createMappedForm(HibernateEmbeddedPersistentEntity.this);
                @Override
                public PersistentEntity getEntity() {
                    return HibernateEmbeddedPersistentEntity.this;
                }

                @Override
                public Mapping getMappedForm() {
                    return mappedForm;
                }

                @Override
                public IdentityMapping getIdentifier() {
                    return null;
                }
            };
        }

        @Override
        public ClassMapping getMapping() {
            return classMapping;
        }
    }

    class HibernateMappingFactory extends AbstractGormMappingFactory<Mapping,PropertyConfig> {

        public HibernateMappingFactory() {
        }

        @Override
        protected MappingConfigurationBuilder createConfigurationBuilder(PersistentEntity entity, Mapping mapping) {
            return new HibernateMappingBuilder(mapping, entity.getName(), defaultConstraints);
        }

        @Override
        public IdentityMapping createIdentityMapping(final ClassMapping classMapping) {
            final Mapping mappedForm = createMappedForm(classMapping.getEntity());
            final Object identity = mappedForm.getIdentity();
            final ValueGenerator generator;
            if(identity instanceof Identity) {
                Identity id = (Identity) identity;
                String generatorName = id.getGenerator();
                if(generatorName != null) {
                    ValueGenerator resolvedGenerator;
                    try {
                        resolvedGenerator = ValueGenerator.valueOf(generatorName.toUpperCase(java.util.Locale.ENGLISH));
                    } catch (IllegalArgumentException e) {
                        if(ClassUtils.isPresent(generatorName)) {
                            resolvedGenerator = ValueGenerator.CUSTOM;
                        }
                        else {
                            throw new DatastoreConfigurationException("Invalid id generation strategy for entity ["+classMapping.getEntity().getName()+"]: " + generatorName);
                        }
                    }
                    generator = resolvedGenerator;
                }
                else {
                    generator = ValueGenerator.AUTO;
                }
            }
            else {
                generator = ValueGenerator.AUTO;
            }
            return new IdentityMapping() {
                @Override
                public String[] getIdentifierName() {
                    if(identity instanceof Identity) {
                        final String name = ((Identity) identity).getName();
                        if(name != null) {
                            return new String[]{name};
                        }
                        else {
                            return DEFAULT_IDENTITY_MAPPING;
                        }
                    }
                    else if(identity instanceof CompositeIdentity) {
                        return ((CompositeIdentity) identity).getPropertyNames();
                    }
                    return DEFAULT_IDENTITY_MAPPING;
                }

                @Override
                public ValueGenerator getGenerator() {
                    return generator;
                }

                @Override
                public ClassMapping getClassMapping() {
                    return classMapping;
                }

                @Override
                public Property getMappedForm() {
                    return (Property) identity;
                }
            };
        }

        @Override
        protected boolean allowArbitraryCustomTypes() {
            return true;
        }

        @Override
        protected Class<PropertyConfig> getPropertyMappedFormType() {
            return PropertyConfig.class;
        }

        @Override
        protected Class<Mapping> getEntityMappedFormType() {
            return Mapping.class;
        }
    }
}
