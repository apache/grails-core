package liquibase.ext.hibernate.database;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import liquibase.Scope;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedProperties;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Database implementation for "spring" hibernate configurations where a bean name is given. If a package is used, {@link HibernateSpringPackageDatabase} will be used.
 */
public class HibernateSpringBeanDatabase extends HibernateDatabase {

    private BeanDefinition beanDefinition;
    private ManagedProperties beanDefinitionProperties;

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        return Optional.ofNullable(conn.getURL())
                .map(url -> url.startsWith("hibernate:spring:"))
                .orElse(false);
    }

    /**
     * Calls {@link #loadBeanDefinition()}
     */
    @Override
    protected Metadata buildMetadataFromPath() throws DatabaseException {
        loadBeanDefinition();
        return super.buildMetadataFromPath();
    }

    @Override
    public String getProperty(String name) {
        return Optional.ofNullable(super.getProperty(name))
                .or(() -> findPropertyInBeanDefinition(name))
                .orElseGet(() -> beanDefinitionProperties != null ? beanDefinitionProperties.getProperty(name) : null);
    }

    private Optional<String> findPropertyInBeanDefinition(String name) {
        return Optional.ofNullable(beanDefinitionProperties)
                .flatMap(props -> props.entrySet().stream()
                        .filter(entry -> name.equals(resolveString(entry.getKey())))
                        .map(entry -> resolveString(entry.getValue()))
                        .filter(java.util.Objects::nonNull)
                        .findFirst());
    }

    private String resolveString(Object obj) {
        if (obj instanceof TypedStringValue tsv) {
            return tsv.getValue();
        } else if (obj instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Parse the given URL assuming it is a spring XML file
     */
    protected void loadBeanDefinition() {
        // Read configuration
        BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
        reader.setNamespaceAware(true);
        HibernateConnection connection = getHibernateConnection();
        String path = Optional.ofNullable(connection.getPath())
                .orElseThrow(() -> new IllegalStateException("Hibernate connection path is null"));
        reader.loadBeanDefinitions(new ClassPathResource(path));

        Properties props = Optional.ofNullable(connection.getProperties())
                .orElseThrow(() -> new IllegalStateException("Hibernate connection properties are null"));

        String beanName = Optional.ofNullable(props.getProperty("bean"))
                .orElseThrow(() -> new IllegalStateException("A 'bean' name is required, definition in '" + path + "'."));

        try {
            beanDefinition = registry.getBeanDefinition(beanName);
            Optional.ofNullable(beanDefinition.getPropertyValues().getPropertyValue("hibernateProperties"))
                    .map(PropertyValue::getValue)
                    .filter(ManagedProperties.class::isInstance)
                    .map(ManagedProperties.class::cast)
                    .ifPresent(p -> beanDefinitionProperties = p);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalStateException(
                    "A bean named '" + beanName + "' could not be found in '" + path + "'.", e);
        }
    }

    @Override
    protected void configureSources(MetadataSources sources) throws DatabaseException {
        BeanDefinition bd = Optional.ofNullable(beanDefinition)
                .orElseThrow(() -> new DatabaseException("Bean definition is not loaded."));
        MutablePropertyValues properties = bd.getPropertyValues();

        // Add annotated classes list.
        extractListProperty(properties, "annotatedClasses")
                .forEach(className -> {
                    Scope.getCurrentScope().getLog(getClass()).info("Found annotated class " + className);
                    sources.addAnnotatedClass(findClass(className));
                });

        try {
            // Add mapping locations
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            extractListProperty(properties, "mappingLocations")
                    .forEach(mappingLocation -> {
                        try {
                            Scope.getCurrentScope().getLog(getClass()).info("Found mappingLocation " + mappingLocation);
                            Resource[] resources = resourcePatternResolver.getResources(mappingLocation);
                            if (resources != null) {
                                for (Resource resource : resources) {
                                    URL url = resource.getURL();
                                    if (url != null) {
                                        Scope.getCurrentScope().getLog(getClass()).info("Adding resource  " + url);
                                        sources.addURL(url);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }

    private Stream<String> extractListProperty(MutablePropertyValues properties, String propertyName) {
        return Optional.ofNullable(properties.getPropertyValue(propertyName))
                .map(PropertyValue::getValue)
                .filter(List.class::isInstance)
                .map(v -> (List<?>) v)
                .stream()
                .flatMap(List::stream)
                .filter(TypedStringValue.class::isInstance)
                .map(TypedStringValue.class::cast)
                .map(TypedStringValue::getValue)
                .filter(java.util.Objects::nonNull);
    }

    private Class<?> findClass(String className) {
        return findClass(className, Object.class);
    }

    private <T> Class<? extends T> findClass(String className, Class<T> superClass) {
        try {
            Class<?> newClass = Class.forName(className);
            if (superClass.isAssignableFrom(newClass)) {
                return newClass.asSubclass(superClass);
            } else {
                throw new IllegalStateException("The provided class '" + className + "' is not assignable from the '"
                        + superClass.getName() + "' superclass.");
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Unable to find required class: '" + className + "'. Please check classpath and class name.");
        }
    }

    @Override
    public String getShortName() {
        return "hibernateSpringBean";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Hibernate Spring Bean";
    }
}
