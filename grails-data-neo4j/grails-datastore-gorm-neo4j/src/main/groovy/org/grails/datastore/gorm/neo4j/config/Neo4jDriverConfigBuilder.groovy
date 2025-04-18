package org.grails.datastore.gorm.neo4j.config

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.config.ConfigurationBuilder
import org.neo4j.driver.Config
import org.springframework.core.env.PropertyResolver
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Constructs the Neo4j driver configuration
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class Neo4jDriverConfigBuilder extends ConfigurationBuilder<Config.ConfigBuilder, Config> {

    Neo4jDriverConfigBuilder(PropertyResolver propertyResolver) {
        super(propertyResolver, Settings.SETTING_NEO4J_DRIVER_PROPERTIES, "with")
    }

    Neo4jDriverConfigBuilder(PropertyResolver propertyResolver, String configurationPrefix) {
        super(propertyResolver, configurationPrefix, "with")
    }

    Neo4jDriverConfigBuilder(PropertyResolver propertyResolver, String configurationPrefix, Object fallBackConfiguration) {
        super(propertyResolver, configurationPrefix, fallBackConfiguration, "with")
    }

    @Override
    protected Config.ConfigBuilder createBuilder() {
        return Config.builder()
    }

    @Override
    protected Config toConfiguration(Config.ConfigBuilder builder) {
        return builder.build()
    }

    @Override
    protected Object getFallBackValue(Object fallBackConfig, String methodName) {
        if(fallBackConfig != null) {
            Method fallBackMethod = ReflectionUtils.findMethod(fallBackConfig.getClass(), methodName)
            if(fallBackMethod != null && Modifier.isPublic(fallBackMethod.getModifiers())) {
                return fallBackMethod.invoke(fallBackConfig)

            }
            else {
                return super.getFallBackValue(fallBackConfig, methodName)
            }
        }
        return null
    }
}
