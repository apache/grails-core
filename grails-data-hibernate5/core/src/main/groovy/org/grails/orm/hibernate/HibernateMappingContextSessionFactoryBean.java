package org.grails.orm.hibernate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.NamingStrategy;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.orm.hibernate5.HibernateExceptionTranslator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;

import javax.naming.NameNotFoundException;
import javax.sql.DataSource;
import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Configures a SessionFactory using a {@link org.grails.orm.hibernate.cfg.HibernateMappingContext} and a {@link org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration}
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernateMappingContextSessionFactoryBean extends HibernateExceptionTranslator
        implements FactoryBean<SessionFactory>, ResourceLoaderAware, DisposableBean,
        ApplicationContextAware, InitializingBean, BeanClassLoaderAware {
    protected Class<? extends HibernateMappingContextConfiguration> configClass = HibernateMappingContextConfiguration.class;
    protected HibernateMappingContext hibernateMappingContext;
    protected PlatformTransactionManager transactionManager;

    private DataSource dataSource;
    private Resource[] configLocations;
    private String[] mappingResources;
    private Resource[] mappingLocations;
    private Resource[] cacheableMappingLocations;
    private Resource[] mappingJarLocations;
    private Resource[] mappingDirectoryLocations;
    private Interceptor entityInterceptor;
    private NamingStrategy namingStrategy;
    private Properties hibernateProperties;
    private Class<?>[] annotatedClasses;
    private String[] annotatedPackages;
    private String[] packagesToScan;
    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private HibernateMappingContextConfiguration configuration;
    private SessionFactory sessionFactory;

    private static final Log LOG = LogFactory.getLog(HibernateMappingContextSessionFactoryBean.class);
    protected Class<?> currentSessionContextClass;
    protected Map<String, Object> eventListeners;
    protected HibernateEventListeners hibernateEventListeners;
    protected ApplicationContext applicationContext;
    protected boolean proxyIfReloadEnabled = false;
    protected String sessionFactoryBeanName = "sessionFactory";
    protected String dataSourceName = ConnectionSource.DEFAULT;
    protected ClassLoader classLoader;


    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void afterPropertiesSet() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader cl = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            buildSessionFactory();
        }
        finally {
            thread.setContextClassLoader(cl);
        }
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setHibernateMappingContext(HibernateMappingContext hibernateMappingContext) {
        this.hibernateMappingContext = hibernateMappingContext;
    }

    /**
     * Sets the class to be used for Hibernate Configuration.
     * @param configClass A subclass of the Hibernate Configuration class
     */
    public void setConfigClass(Class<? extends HibernateMappingContextConfiguration> configClass) {
        this.configClass = configClass;
    }
    /**
     * Set the DataSource to be used by the SessionFactory.
     * If set, this will override corresponding settings in Hibernate properties.
     * <p>If this is set, the Hibernate settings should not define
     * a connection provider to avoid meaningless double configuration.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Set the location of a single Hibernate XML config file, for example as
     * classpath resource "classpath:hibernate.cfg.xml".
     * <p>Note: Can be omitted when all necessary properties and mapping
     * resources are specified locally via this bean.
     * @see org.hibernate.cfg.Configuration#configure(java.net.URL)
     */
    public void setConfigLocation(Resource configLocation) {
        configLocations = new Resource[] {configLocation};
    }

    /**
     * Set the locations of multiple Hibernate XML config files, for example as
     * classpath resources "classpath:hibernate.cfg.xml,classpath:extension.cfg.xml".
     * <p>Note: Can be omitted when all necessary properties and mapping
     * resources are specified locally via this bean.
     * @see org.hibernate.cfg.Configuration#configure(java.net.URL)
     */
    public void setConfigLocations(Resource[] configLocations) {
        this.configLocations = configLocations;
    }
    public Resource[] getConfigLocations() {
        return configLocations;
    }

    /**
     * Set Hibernate mapping resources to be found in the class path,
     * like "example.hbm.xml" or "mypackage/example.hbm.xml".
     * Analogous to mapping entries in a Hibernate XML config file.
     * Alternative to the more generic setMappingLocations method.
     * <p>Can be used to add to mappings from a Hibernate XML config file,
     * or to specify all mappings locally.
     * @see #setMappingLocations
     * @see org.hibernate.cfg.Configuration#addResource
     */
    public void setMappingResources(String[] mappingResources) {
        this.mappingResources = mappingResources;
    }
    public String[] getMappingResources() {
        return mappingResources;
    }

    /**
     * Set locations of Hibernate mapping files, for example as classpath
     * resource "classpath:example.hbm.xml". Supports any resource location
     * via Spring's resource abstraction, for example relative paths like
     * "WEB-INF/mappings/example.hbm.xml" when running in an application context.
     * <p>Can be used to add to mappings from a Hibernate XML config file,
     * or to specify all mappings locally.
     * @see org.hibernate.cfg.Configuration#addInputStream
     */
    public void setMappingLocations(Resource[] mappingLocations) {
        this.mappingLocations = mappingLocations;
    }
    public Resource[] getMappingLocations() {
        return mappingLocations;
    }

    /**
     * Set locations of cacheable Hibernate mapping files, for example as web app
     * resource "/WEB-INF/mapping/example.hbm.xml". Supports any resource location
     * via Spring's resource abstraction, as long as the resource can be resolved
     * in the file system.
     * <p>Can be used to add to mappings from a Hibernate XML config file,
     * or to specify all mappings locally.
     * @see org.hibernate.cfg.Configuration#addCacheableFile(java.io.File)
     */
    public void setCacheableMappingLocations(Resource[] cacheableMappingLocations) {
        this.cacheableMappingLocations = cacheableMappingLocations;
    }
    public Resource[] getCacheableMappingLocations() {
        return cacheableMappingLocations;
    }

    /**
     * Set locations of jar files that contain Hibernate mapping resources,
     * like "WEB-INF/lib/example.hbm.jar".
     * <p>Can be used to add to mappings from a Hibernate XML config file,
     * or to specify all mappings locally.
     * @see org.hibernate.cfg.Configuration#addJar(java.io.File)
     */
    public void setMappingJarLocations(Resource[] mappingJarLocations) {
        this.mappingJarLocations = mappingJarLocations;
    }
    public Resource[] getMappingJarLocations() {
        return mappingJarLocations;
    }

    /**
     * Set locations of directories that contain Hibernate mapping resources,
     * like "WEB-INF/mappings".
     * <p>Can be used to add to mappings from a Hibernate XML config file,
     * or to specify all mappings locally.
     * @see org.hibernate.cfg.Configuration#addDirectory(java.io.File)
     */
    public void setMappingDirectoryLocations(Resource[] mappingDirectoryLocations) {
        this.mappingDirectoryLocations = mappingDirectoryLocations;
    }
    public Resource[] getMappingDirectoryLocations() {
        return mappingDirectoryLocations;
    }

    /**
     * Set a Hibernate entity interceptor that allows to inspect and change
     * property values before writing to and reading from the database.
     * Will get applied to any new Session created by this factory.
     * @see org.hibernate.cfg.Configuration#setInterceptor
     */
    public void setEntityInterceptor(Interceptor entityInterceptor) {
        this.entityInterceptor = entityInterceptor;
    }
    public Interceptor getEntityInterceptor() {
        return entityInterceptor;
    }

    /**
     * Set a Hibernate NamingStrategy for the SessionFactory, determining the
     * physical column and table names given the info in the mapping document.
     */
    public void setNamingStrategy(NamingStrategy namingStrategy) {
        this.namingStrategy = namingStrategy;
    }
    public NamingStrategy getNamingStrategy() {
        return namingStrategy;
    }

    /**
     * Set Hibernate properties, such as "hibernate.dialect".
     * <p>Note: Do not specify a transaction provider here when using
     * Spring-driven transactions. It is also advisable to omit connection
     * provider settings and use a Spring-set DataSource instead.
     * @see #setDataSource
     */
    public void setHibernateProperties(Properties hibernateProperties) {
        this.hibernateProperties = hibernateProperties;
    }

    /**
     * Return the Hibernate properties, if any. Mainly available for
     * configuration through property paths that specify individual keys.
     */
    public Properties getHibernateProperties() {
        if (hibernateProperties == null) {
            hibernateProperties = new Properties();
        }
        return hibernateProperties;
    }

    /**
     * Specify annotated entity classes to register with this Hibernate SessionFactory.
     * @see org.hibernate.cfg.Configuration#addAnnotatedClass(Class)
     */
    public void setAnnotatedClasses(Class<?>[] annotatedClasses) {
        this.annotatedClasses = annotatedClasses;
    }
    public Class<?>[] getAnnotatedClasses() {
        return annotatedClasses;
    }

    /**
     * Specify the names of annotated packages, for which package-level
     * annotation metadata will be read.
     * @see org.hibernate.cfg.Configuration#addPackage(String)
     */
    public void setAnnotatedPackages(String[] annotatedPackages) {
        this.annotatedPackages = annotatedPackages;
    }
    public String[] getAnnotatedPackages() {
        return annotatedPackages;
    }

    /**
     * Specify packages to search for autodetection of your entity classes in the
     * classpath. This is analogous to Spring's component-scan feature
     * ({@link org.springframework.context.annotation.ClassPathBeanDefinitionScanner}).
     */
    public void setPackagesToScan(String... packagesToScan) {
        this.packagesToScan = packagesToScan;
    }
    public String[] getPackagesToScan() {
        return packagesToScan;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        resourcePatternResolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * @param proxyIfReloadEnabled Sets whether a proxy should be created if reload is enabled
     */
    public void setProxyIfReloadEnabled(boolean proxyIfReloadEnabled) {
        this.proxyIfReloadEnabled = proxyIfReloadEnabled;
    }
    public boolean isProxyIfReloadEnabled() {
        return proxyIfReloadEnabled;
    }

    /**
     * Sets class to be used for the Hibernate CurrentSessionContext.
     *
     * @param currentSessionContextClass An implementation of the CurrentSessionContext interface
     */
    public void setCurrentSessionContextClass(Class<?> currentSessionContextClass) {
        this.currentSessionContextClass = currentSessionContextClass;
    }
    public Class<?> getCurrentSessionContextClass() {
        return currentSessionContextClass;
    }

    public Class<? extends HibernateMappingContextConfiguration> getConfigClass() {
        return configClass;
    }

    public void setHibernateEventListeners(final HibernateEventListeners listeners) {
        hibernateEventListeners = listeners;
    }
    public HibernateEventListeners getHibernateEventListeners() {
        return hibernateEventListeners;
    }

    public void setSessionFactoryBeanName(String name) {
        sessionFactoryBeanName = name;
    }
    public String getSessionFactoryBeanName() {
        return sessionFactoryBeanName;
    }

    public void setDataSourceName(String name) {
        dataSourceName = name;
    }
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Specify the Hibernate event listeners to register, with listener types
     * as keys and listener objects as values. Instead of a single listener object,
     * you can also pass in a list or set of listeners objects as value.
     * <p>See the Hibernate documentation for further details on listener types
     * and associated listener interfaces.
     * @param eventListeners Map with listener type Strings as keys and
     * listener objects as values
     */
    public void setEventListeners(Map<String, Object> eventListeners) {
        this.eventListeners = eventListeners;
    }
    public Map<String, Object> getEventListeners() {
        return eventListeners;
    }

    protected void buildSessionFactory() throws Exception {

        configuration = newConfiguration();

        if(hibernateMappingContext == null) {

            throw new IllegalArgumentException("HibernateMappingContext is required.");
        }

        configuration.setHibernateMappingContext(hibernateMappingContext);

        if (configLocations != null) {
            for (Resource resource : configLocations) {
                // Load Hibernate configuration from given location.
                configuration.configure(resource.getURL());
            }
        }

        if (mappingResources != null) {
            // Register given Hibernate mapping definitions, contained in resource files.
            for (String mapping : mappingResources) {
                Resource mr = new ClassPathResource(mapping.trim(), resourcePatternResolver.getClassLoader());
                configuration.addInputStream(mr.getInputStream());
            }
        }

        if (mappingLocations != null) {
            // Register given Hibernate mapping definitions, contained in resource files.
            for (Resource resource : mappingLocations) {
                configuration.addInputStream(resource.getInputStream());
            }
        }

        if (cacheableMappingLocations != null) {
            // Register given cacheable Hibernate mapping definitions, read from the file system.
            for (Resource resource : cacheableMappingLocations) {
                configuration.addCacheableFile(resource.getFile());
            }
        }

        if (mappingJarLocations != null) {
            // Register given Hibernate mapping definitions, contained in jar files.
            for (Resource resource : mappingJarLocations) {
                configuration.addJar(resource.getFile());
            }
        }

        if (mappingDirectoryLocations != null) {
            // Register all Hibernate mapping definitions in the given directories.
            for (Resource resource : mappingDirectoryLocations) {
                File file = resource.getFile();
                if (!file.isDirectory()) {
                    throw new IllegalArgumentException("Mapping directory location [" + resource + "] does not denote a directory");
                }
                configuration.addDirectory(file);
            }
        }

        if (entityInterceptor != null) {
            configuration.setInterceptor(entityInterceptor);
        }

        if (namingStrategy != null) {
//            configuration.setNamingStrategy(namingStrategy);
        }

        if (hibernateProperties != null) {
            configuration.addProperties(hibernateProperties);
        }

        if (annotatedClasses != null) {
            configuration.addAnnotatedClasses(annotatedClasses);
        }


        if (annotatedPackages != null) {
            configuration.addPackages(annotatedPackages);
        }

        if (packagesToScan != null) {
            configuration.scanPackages(packagesToScan);
        }

        if (eventListeners != null) {
            configuration.setEventListeners(eventListeners);
        }

        sessionFactory = doBuildSessionFactory();
    }

    protected SessionFactory doBuildSessionFactory() {
        return configuration.buildSessionFactory();
    }

    /**
     * Return the Hibernate Configuration object used to build the SessionFactory.
     * Allows for access to configuration metadata stored there (rarely needed).
     * @throws IllegalStateException if the Configuration object has not been initialized yet
     */
    public final Configuration getConfiguration() {
        Assert.state(configuration != null, "Configuration not initialized yet");
        return configuration;
    }

    public SessionFactory getObject() {
        return sessionFactory;
    }

    public Class<?> getObjectType() {
        return sessionFactory == null ? SessionFactory.class : sessionFactory.getClass();
    }

    public boolean isSingleton() {
        return true;
    }

    public void destroy() {
        try {
            sessionFactory.close();
        }
        catch (HibernateException e) {
            if (e.getCause() instanceof NameNotFoundException) {
                LOG.debug(e.getCause().getMessage(), e);
            }
            else {
                throw e;
            }
        }
    }

    protected HibernateMappingContextConfiguration newConfiguration() throws Exception {
        if (configClass == null) {
            configClass = HibernateMappingContextConfiguration.class;
        }
        HibernateMappingContextConfiguration config = BeanUtils.instantiateClass(configClass);
        config.setDataSourceName(dataSourceName);
        config.setApplicationContext(applicationContext);
        config.setSessionFactoryBeanName(sessionFactoryBeanName);
        config.setHibernateEventListeners(hibernateEventListeners);
        if (currentSessionContextClass != null) {
            config.setProperty(Environment.CURRENT_SESSION_CONTEXT_CLASS, currentSessionContextClass.getName());
        }
        return config;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
