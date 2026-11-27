package liquibase.ext.hibernate.database

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import liquibase.Scope
import liquibase.database.DatabaseConnection
import liquibase.exception.DatabaseException
import liquibase.ext.hibernate.database.connection.HibernateConnection
import org.hibernate.boot.Metadata
import org.hibernate.boot.MetadataSources
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.PropertyValue
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.TypedStringValue
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.ManagedProperties
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver

/**
 * Database implementation for "spring" hibernate configurations where a bean name is given. If a package is used, {@link HibernateSpringPackageDatabase} will be used.
 */
@CompileStatic
@POJO // see HibernateDatabase
class HibernateSpringBeanDatabase extends HibernateDatabase {

    private BeanDefinition beanDefinition
    private ManagedProperties beanDefinitionProperties

    @Override
    boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        String url = conn.getURL()
        return url != null && url.startsWith('hibernate:spring:')
    }

    /**
     * Calls {@link #loadBeanDefinition()}
     */
    @Override
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        loadBeanDefinition()
        return super.buildMetadataFromPath()
    }

    @Override
    String getProperty(String name) {
        String value = super.getProperty(name)
        if (value != null) {
            return value
        }
        String fromBeanDefinition = findPropertyInBeanDefinition(name)
        if (fromBeanDefinition != null) {
            return fromBeanDefinition
        }
        return beanDefinitionProperties != null ? beanDefinitionProperties.getProperty(name) : null
    }

    private String findPropertyInBeanDefinition(String name) {
        if (beanDefinitionProperties == null) {
            return null
        }
        for (Map.Entry<Object, Object> entry : beanDefinitionProperties.entrySet()) {
            if (name.equals(resolveString(entry.getKey()))) {
                String value = resolveString(entry.getValue())
                if (value != null) {
                    return value
                }
            }
        }
        return null
    }

    private String resolveString(Object obj) {
        if (obj instanceof TypedStringValue) {
            return ((TypedStringValue) obj).getValue()
        } else if (obj instanceof String) {
            return (String) obj
        }
        return null
    }

    /**
     * Parse the given URL assuming it is a spring XML file
     */
    protected void loadBeanDefinition() {
        // Read configuration
        BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry()
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry)
        reader.setNamespaceAware(true)

        // Fix: Use try-with-resources to ensure HibernateConnection is closed (PMD #8)
        HibernateConnection connection = getHibernateConnection()
        try {
            String path = connection.getPath()
            if (path == null) {
                throw new IllegalStateException('Hibernate connection path is null')
            }
            reader.loadBeanDefinitions(new ClassPathResource(path))

            Properties props = connection.getProperties()
            if (props == null) {
                throw new IllegalStateException('Hibernate connection properties are null')
            }

            String beanName = props.getProperty('bean')
            if (beanName == null) {
                throw new IllegalStateException("A 'bean' name is required, definition in '" + path + "'.")
            }

            try {
                beanDefinition = registry.getBeanDefinition(beanName)
                PropertyValue hibernateProperties = beanDefinition.getPropertyValues().getPropertyValue('hibernateProperties')
                Object propertiesValue = hibernateProperties != null ? hibernateProperties.getValue() : null
                if (propertiesValue instanceof ManagedProperties) {
                    beanDefinitionProperties = (ManagedProperties) propertiesValue
                }
            } catch (NoSuchBeanDefinitionException e) {
                throw new IllegalStateException(
                        "A bean named '" + beanName + "' could not be found in '" + path + "'.", e)
            }
        } finally {
            connection.close()
        }
    }

    @Override
    protected void configureSources(MetadataSources sources) throws DatabaseException {
        BeanDefinition bd = beanDefinition
        if (bd == null) {
            throw new DatabaseException('Bean definition is not loaded.')
        }
        MutablePropertyValues properties = bd.getPropertyValues()

        // Add annotated classes list.
        for (String className : extractListProperty(properties, 'annotatedClasses')) {
            Scope.getCurrentScope().getLog(getClass()).info('Found annotated class ' + className)
            sources.addAnnotatedClass(findClass(className))
        }

        // Add mapping locations
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver()
        for (String mappingLocation : extractListProperty(properties, 'mappingLocations')) {
            try {
                Scope.getCurrentScope().getLog(getClass()).info('Found mappingLocation ' + mappingLocation)
                Resource[] resources = resourcePatternResolver.getResources(mappingLocation)
                for (Resource resource : resources) {
                    URL url = resource.getURL()
                    Scope.getCurrentScope().getLog(getClass()).info('Adding resource  ' + url)
                    sources.addURL(url)
                }
            } catch (IOException e) {
                // Fix: Pass 'e' as cause to preserve stack trace (PMD #9)
                throw new RuntimeException('Error resolving mapping location: ' + mappingLocation, e)
            }
        }
    }

    private List<String> extractListProperty(MutablePropertyValues properties, String propertyName) {
        List<String> result = new ArrayList<>()
        PropertyValue propertyValue = properties.getPropertyValue(propertyName)
        Object value = propertyValue != null ? propertyValue.getValue() : null
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof TypedStringValue) {
                    String stringValue = ((TypedStringValue) item).getValue()
                    if (stringValue != null) {
                        result.add(stringValue)
                    }
                }
            }
        }
        return result
    }

    private Class<?> findClass(String className) {
        try {
            Class<?> newClass = Class.forName(className)
            if (Object.isAssignableFrom(newClass)) {
                return newClass.asSubclass(Object)
            } else {
                throw new IllegalStateException("The provided class '" + className + "' is not assignable from the '" +
                        Object.getName() + "' superclass.")
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Unable to find required class: '" + className + "'. Please check classpath and class name.", e)
        }
    }

    @Override
    String getShortName() {
        return 'hibernateSpringBean'
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return 'Hibernate Spring Bean'
    }

}
