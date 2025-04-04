package org.grails.orm.hibernate.connections;

import org.grails.datastore.gorm.validation.jakarta.JakartaValidatorRegistry;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.core.grailsversion.GrailsVersion;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.HibernateEventListeners;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.grails.datastore.gorm.jdbc.connections.*;
import org.grails.orm.hibernate.support.AbstractClosureEventTriggeringInterceptor;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.spi.MetadataContributor;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.NamingStrategy;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * Constructs {@link SessionFactory} instances from a {@link HibernateMappingContext}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateConnectionSourceFactory extends AbstractHibernateConnectionSourceFactory implements ApplicationContextAware, MessageSourceAware {

    static {
        // use Slf4j logging by default
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    protected HibernateMappingContext mappingContext;
    protected Class[] persistentClasses = new Class[0];
    private ApplicationContext applicationContext;
    protected HibernateEventListeners hibernateEventListeners;
    protected Interceptor interceptor;
    protected MetadataContributor metadataContributor;
    protected MessageSource messageSource = new StaticMessageSource();

    public HibernateConnectionSourceFactory(Class...classes) {
        this.persistentClasses = classes;
    }

    public Class[] getPersistentClasses() {
        return persistentClasses;
    }

    @Autowired(required = false)
    public void setHibernateEventListeners(HibernateEventListeners hibernateEventListeners) {
        this.hibernateEventListeners = hibernateEventListeners;
    }

    @Autowired(required = false)
    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Autowired(required = false)
    public void setMetadataContributor(MetadataContributor metadataContributor) {
        this.metadataContributor = metadataContributor;
    }

    public HibernateMappingContext getMappingContext() {
        return mappingContext;
    }

    @Override
    public ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> create(String name, ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource, HibernateConnectionSourceSettings settings) {
        HibernateMappingContextConfiguration configuration = buildConfiguration(name, dataSourceConnectionSource, settings);
        SessionFactory sessionFactory = configuration.buildSessionFactory();
        return new HibernateConnectionSource(name, sessionFactory, dataSourceConnectionSource, settings);
    }

    public HibernateMappingContextConfiguration buildConfiguration(String name, ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource, HibernateConnectionSourceSettings settings) {
        boolean isDefault = ConnectionSource.DEFAULT.equals(name);

        if(mappingContext == null) {
            mappingContext = new HibernateMappingContext(settings, applicationContext, persistentClasses);
        }

        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
        Class<? extends Configuration> configClass = hibernateSettings.getConfigClass();

        HibernateMappingContextConfiguration configuration;
        if(configClass != null) {
            if( !HibernateMappingContextConfiguration.class.isAssignableFrom(configClass) ) {
                throw new ConfigurationException("The configClass setting must be a subclass for [HibernateMappingContextConfiguration]");
            }
            else {
                configuration = (HibernateMappingContextConfiguration) BeanUtils.instantiateClass(configClass);
            }
        }
        else {
            configuration = new HibernateMappingContextConfiguration();
        }

        if(JakartaValidatorRegistry.isAvailable() && messageSource != null) {
            ValidatorRegistry registry = new JakartaValidatorRegistry(mappingContext,dataSourceConnectionSource.getSettings(), messageSource );
            mappingContext.setValidatorRegistry(registry);
            configuration.getProperties().put("jakarta.persistence.validation.factory", registry);
        }

        if(applicationContext != null && applicationContext.containsBean(dataSourceConnectionSource.getName())) {
            configuration.setApplicationContext(this.applicationContext);
        }
        else {
            configuration.setDataSourceConnectionSource(dataSourceConnectionSource);
        }

        Resource[] configLocations = hibernateSettings.getConfigLocations();
        if (configLocations != null) {
            for (Resource resource : configLocations) {
                // Load Hibernate configuration from given location.
                try {
                    configuration.configure(resource.getURL());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] mappingLocations = hibernateSettings.getMappingLocations();
        if (mappingLocations != null) {
            // Register given Hibernate mapping definitions, contained in resource files.
            for (Resource resource : mappingLocations) {
                try {
                    configuration.addInputStream(resource.getInputStream());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] cacheableMappingLocations = hibernateSettings.getCacheableMappingLocations();
        if (cacheableMappingLocations != null) {
            // Register given cacheable Hibernate mapping definitions, read from the file system.
            for (Resource resource : cacheableMappingLocations) {
                try {
                    configuration.addCacheableFile(resource.getFile());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] mappingJarLocations = hibernateSettings.getMappingJarLocations();
        if (mappingJarLocations != null) {
            // Register given Hibernate mapping definitions, contained in jar files.
            for (Resource resource : mappingJarLocations) {
                try {
                    configuration.addJar(resource.getFile());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] mappingDirectoryLocations = hibernateSettings.getMappingDirectoryLocations();
        if (mappingDirectoryLocations != null) {
            // Register all Hibernate mapping definitions in the given directories.
            for (Resource resource : mappingDirectoryLocations) {
                File file;
                try {
                    file = resource.getFile();
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
                if (!file.isDirectory()) {
                    throw new IllegalArgumentException("Mapping directory location [" + resource + "] does not denote a directory");
                }
                configuration.addDirectory(file);
            }
        }

        if (this.interceptor != null) {
            configuration.setInterceptor(this.interceptor);
        }

        if (this.metadataContributor != null) {
            configuration.setMetadataContributor(metadataContributor);
        }

        Class[] annotatedClasses = hibernateSettings.getAnnotatedClasses();
        if (annotatedClasses != null) {
            configuration.addAnnotatedClasses(annotatedClasses);
        }

        String[] annotatedPackages = hibernateSettings.getAnnotatedPackages();
        if (annotatedPackages != null) {
            configuration.addPackages(annotatedPackages);
        }

        String[] packagesToScan = hibernateSettings.getPackagesToScan();
        if (packagesToScan != null) {
            configuration.scanPackages(packagesToScan);
        }

        Class<? extends AbstractClosureEventTriggeringInterceptor> closureEventTriggeringInterceptorClass = hibernateSettings.getClosureEventTriggeringInterceptorClass();

        AbstractClosureEventTriggeringInterceptor eventTriggeringInterceptor;

        if(closureEventTriggeringInterceptorClass == null) {
            eventTriggeringInterceptor = new ClosureEventTriggeringInterceptor();
        }
        else {
            eventTriggeringInterceptor = BeanUtils.instantiateClass(closureEventTriggeringInterceptorClass);
        }

        hibernateSettings.setEventTriggeringInterceptor(eventTriggeringInterceptor);

        try {
            Class<? extends NamingStrategy> namingStrategy = hibernateSettings.getNaming_strategy();
            if(namingStrategy != null) {
                GrailsDomainBinder.configureNamingStrategy(name, namingStrategy);
            }
        } catch (Throwable e) {
            throw new ConfigurationException("Error configuring naming strategy: " + e.getMessage(), e);
        }

        configuration.setEventListeners(hibernateSettings.toHibernateEventListeners(eventTriggeringInterceptor));
        HibernateEventListeners hibernateEventListeners = hibernateSettings.getHibernateEventListeners();
        configuration.setHibernateEventListeners(this.hibernateEventListeners != null ? this.hibernateEventListeners  : hibernateEventListeners);
        configuration.setHibernateMappingContext(mappingContext);
        configuration.setDataSourceName(name);
        configuration.setSessionFactoryBeanName(isDefault ? "sessionFactory" : "sessionFactory_" + name);
        Properties hibernateProperties = settings.toProperties();
        configuration.addProperties(hibernateProperties);
        return configuration;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(applicationContext != null) {
            this.applicationContext = applicationContext;
            this.messageSource = applicationContext;
            if (!GrailsVersion.isAtLeastMajorMinor(3,3)) {
                SpringDataSourceConnectionSourceFactory springDataSourceConnectionSourceFactory = new SpringDataSourceConnectionSourceFactory();
                springDataSourceConnectionSourceFactory.setApplicationContext(applicationContext);
                this.dataSourceConnectionSourceFactory = springDataSourceConnectionSourceFactory;
            }
        }
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }
}
