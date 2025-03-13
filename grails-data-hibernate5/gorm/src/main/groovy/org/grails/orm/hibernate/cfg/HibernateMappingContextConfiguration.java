package org.grails.orm.hibernate.cfg;

import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.EventListenerIntegrator;
import org.grails.orm.hibernate.GrailsSessionContext;
import org.grails.orm.hibernate.HibernateEventListeners;
import org.grails.orm.hibernate.MetadataIntegrator;
import org.grails.orm.hibernate.access.TraitPropertyAccessStrategy;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.MetadataContributor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentSessionContext;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.*;

/**
 * A Configuration that uses a MappingContext to configure Hibernate
 *
 * @since 5.0
 */
public class HibernateMappingContextConfiguration extends Configuration implements ApplicationContextAware {
    private static final long serialVersionUID = -7115087342689305517L;

    private static final String RESOURCE_PATTERN = "/**/*.class";

    private static final TypeFilter[] ENTITY_TYPE_FILTERS = new TypeFilter[] {
            new AnnotationTypeFilter(Entity.class, false),
            new AnnotationTypeFilter(Embeddable.class, false),
            new AnnotationTypeFilter(MappedSuperclass.class, false)};

    protected String sessionFactoryBeanName = "sessionFactory";
    protected String dataSourceName = ConnectionSource.DEFAULT;
    protected HibernateMappingContext hibernateMappingContext;
    private Class<? extends CurrentSessionContext> currentSessionContext = GrailsSessionContext.class;
    private HibernateEventListeners hibernateEventListeners;
    private Map<String, Object> eventListeners;
    private ServiceRegistry serviceRegistry;
    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private MetadataContributor metadataContributor;
    private Set<Class> additionalClasses = new HashSet<>();

    public void setHibernateMappingContext(HibernateMappingContext hibernateMappingContext) {
        this.hibernateMappingContext = hibernateMappingContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(applicationContext);
        String dsName = ConnectionSource.DEFAULT.equals(dataSourceName) ? "dataSource" : "dataSource_" + dataSourceName;
        Properties properties = getProperties();

        if(applicationContext.containsBean(dsName)) {
            properties.put(Environment.DATASOURCE, applicationContext.getBean(dsName));
        }
        properties.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, currentSessionContext.getName());
        properties.put(AvailableSettings.CLASSLOADERS, applicationContext.getClassLoader());
    }

    /**
     * Set the target SQL {@link DataSource}
     *
     * @param connectionSource The data source to use
     */
    public void setDataSourceConnectionSource(ConnectionSource<DataSource, DataSourceSettings> connectionSource) {
        this.dataSourceName = connectionSource.getName();
        DataSource source = connectionSource.getSource();
        getProperties().put(Environment.DATASOURCE, source);
        getProperties().put(Environment.CURRENT_SESSION_CONTEXT_CLASS, GrailsSessionContext.class.getName());
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null && contextClassLoader.getClass().getSimpleName().equalsIgnoreCase("RestartClassLoader")) {
            getProperties().put(AvailableSettings.CLASSLOADERS, contextClassLoader);
        } else {
            getProperties().put(AvailableSettings.CLASSLOADERS, connectionSource.getClass().getClassLoader());
        }
    }

    /**
     * Add the given annotated classes in a batch.
     * @see #addAnnotatedClass
     * @see #scanPackages
     */
    public void addAnnotatedClasses(Class<?>... annotatedClasses) {
        for (Class<?> annotatedClass : annotatedClasses) {
            addAnnotatedClass(annotatedClass);
        }
    }

    @Override
    public Configuration addAnnotatedClass(Class annotatedClass) {
        additionalClasses.add(annotatedClass);
        return super.addAnnotatedClass(annotatedClass);
    }

    /**
     * Add the given annotated packages in a batch.
     * @see #addPackage
     * @see #scanPackages
     */
    public void addPackages(String... annotatedPackages) {
        for (String annotatedPackage :annotatedPackages) {
            addPackage(annotatedPackage);
        }
    }

    /**
     * Perform Spring-based scanning for entity classes, registering them
     * as annotated classes with this {@code Configuration}.
     * @param packagesToScan one or more Java package names
     * @throws HibernateException if scanning fails for any reason
     */
    public void scanPackages(String... packagesToScan) throws HibernateException {
        try {
            for (String pkg : packagesToScan) {
                String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                        ClassUtils.convertClassNameToResourcePath(pkg) + RESOURCE_PATTERN;
                Resource[] resources = resourcePatternResolver.getResources(pattern);
                MetadataReaderFactory readerFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        MetadataReader reader = readerFactory.getMetadataReader(resource);
                        String className = reader.getClassMetadata().getClassName();
                        if (matchesFilter(reader, readerFactory)) {
                            Class<?> loadedClass = resourcePatternResolver.getClassLoader().loadClass(className);
                            addAnnotatedClasses(loadedClass);
                        }
                    }
                }
            }
        }
        catch (IOException ex) {
            throw new MappingException("Failed to scan classpath for unlisted classes", ex);
        }
        catch (ClassNotFoundException ex) {
            throw new MappingException("Failed to load annotated classes from classpath", ex);
        }
    }

    /**
     * Check whether any of the configured entity type filters matches
     * the current class descriptor contained in the metadata reader.
     */
    protected boolean matchesFilter(MetadataReader reader, MetadataReaderFactory readerFactory) throws IOException {
        for (TypeFilter filter : ENTITY_TYPE_FILTERS) {
            if (filter.match(reader, readerFactory)) {
                return true;
            }
        }
        return false;
    }

    public void setSessionFactoryBeanName(String name) {
        sessionFactoryBeanName = name;
    }

    public void setDataSourceName(String name) {
        dataSourceName = name;
    }

    /* (non-Javadoc)
     * @see org.hibernate.cfg.Configuration#buildSessionFactory()
     */
    @Override
    public SessionFactory buildSessionFactory() throws HibernateException {

        // set the class loader to load Groovy classes

        // work around for HHH-2624
        SessionFactory sessionFactory;

        Object classLoaderObject = getProperties().get(AvailableSettings.CLASSLOADERS);
        ClassLoader appClassLoader;

        if(classLoaderObject instanceof ClassLoader) {
            appClassLoader = (ClassLoader) classLoaderObject;
        }
        else {
            appClassLoader = getClass().getClassLoader();
        }

        ConfigurationHelper.resolvePlaceHolders(getProperties());

        final GrailsDomainBinder domainBinder = new GrailsDomainBinder(
                dataSourceName,
                sessionFactoryBeanName,
                hibernateMappingContext
        );

        List<Class> annotatedClasses = new ArrayList<>();
        for (PersistentEntity persistentEntity : hibernateMappingContext.getPersistentEntities()) {
            Class javaClass = persistentEntity.getJavaClass();
            if(javaClass.isAnnotationPresent(Entity.class)) {
                annotatedClasses.add(javaClass);
            }
        }

        if(!additionalClasses.isEmpty()) {
            for (Class additionalClass : additionalClasses) {
                if(GormEntity.class.isAssignableFrom(additionalClass)) {
                    hibernateMappingContext.addPersistentEntity(additionalClass);
                }
            }
        }

        addAnnotatedClasses( annotatedClasses.toArray(new Class[annotatedClasses.size()]));

        ClassLoaderService classLoaderService = new ClassLoaderServiceImpl(appClassLoader) {
            @Override
            public <S> Collection<S> loadJavaServices(Class<S> serviceContract) {
                if(MetadataContributor.class.isAssignableFrom(serviceContract)) {
                    if(metadataContributor != null) {
                        return (Collection<S>) Arrays.asList(domainBinder, metadataContributor);
                    }
                    else {
                        return Collections.singletonList((S) domainBinder);
                    }
                }
                else {
                    return super.loadJavaServices(serviceContract);
                }
            }
        };
        EventListenerIntegrator eventListenerIntegrator = new EventListenerIntegrator(hibernateEventListeners, eventListeners);
        BootstrapServiceRegistry bootstrapServiceRegistry = createBootstrapServiceRegistryBuilder()
                                                                    .applyIntegrator(eventListenerIntegrator)
                                                                    .applyIntegrator(new MetadataIntegrator())
                                                                    .applyClassLoaderService(classLoaderService)
                                                                    .build();
        StrategySelector strategySelector = bootstrapServiceRegistry.getService(StrategySelector.class);

        strategySelector.registerStrategyImplementor(
                PropertyAccessStrategy.class, "traitProperty", TraitPropertyAccessStrategy.class
        );

        setSessionFactoryObserver(new SessionFactoryObserver() {
            private static final long serialVersionUID = 1;
            public void sessionFactoryCreated(SessionFactory factory) {}
            public void sessionFactoryClosed(SessionFactory factory) {
                if (serviceRegistry != null) {
                    ((ServiceRegistryImplementor)serviceRegistry).destroy();
                }
            }
        });

        StandardServiceRegistryBuilder standardServiceRegistryBuilder = createStandardServiceRegistryBuilder(bootstrapServiceRegistry)
                                                                                    .applySettings(getProperties());

        StandardServiceRegistry serviceRegistry = standardServiceRegistryBuilder.build();
        sessionFactory = super.buildSessionFactory(serviceRegistry);
        this.serviceRegistry = serviceRegistry;

        return sessionFactory;
    }

    /**
     * Creates the {@link BootstrapServiceRegistryBuilder} to use
     *
     * @return The {@link BootstrapServiceRegistryBuilder}
     */
    protected BootstrapServiceRegistryBuilder createBootstrapServiceRegistryBuilder() {
        return new BootstrapServiceRegistryBuilder();
    }

    /**
     * Creates the standard service registry builder. Subclasses can override to customize the creation of the StandardServiceRegistry
     *
     * @param bootstrapServiceRegistry The {@link BootstrapServiceRegistry}
     * @return The {@link StandardServiceRegistryBuilder}
     */
    protected StandardServiceRegistryBuilder createStandardServiceRegistryBuilder(BootstrapServiceRegistry bootstrapServiceRegistry) {
        return new StandardServiceRegistryBuilder(bootstrapServiceRegistry);
    }

    /**
     * Default listeners.
     * @param listeners the listeners
     */
    public void setEventListeners(Map<String, Object> listeners) {
        eventListeners = listeners;
    }

    /**
     * User-specifiable extra listeners.
     * @param listeners the listeners
     */
    public void setHibernateEventListeners(HibernateEventListeners listeners) {
        hibernateEventListeners = listeners;
    }

    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }


    @Override
    protected void reset() {
        super.reset();
        try {
            GrailsIdentifierGeneratorFactory.applyNewInstance(this);
        }
        catch (Exception e) {
            // ignore exception
        }
    }

    public void setMetadataContributor(MetadataContributor metadataContributor) {
        this.metadataContributor = metadataContributor;
    }
}
