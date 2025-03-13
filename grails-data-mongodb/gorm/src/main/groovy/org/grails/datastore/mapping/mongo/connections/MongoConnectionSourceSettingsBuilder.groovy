package org.grails.datastore.mapping.mongo.connections

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.ConfigurationBuilder
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.springframework.core.env.PropertyResolver
import org.springframework.util.ReflectionUtils

/**
 * Creates MongoDB configuration
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class MongoConnectionSourceSettingsBuilder extends ConfigurationBuilder<MongoConnectionSourceSettings, MongoConnectionSourceSettings>{

    MongoClientSettings.Builder clientOptionsBuilder

    MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, String configurationPrefix, ConnectionSourceSettings fallback) {
        super(propertyResolver, configurationPrefix, fallback)
    }

    MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver) {
        super(propertyResolver, MongoSettings.PREFIX)
    }

    MongoConnectionSourceSettingsBuilder(PropertyResolver propertyResolver, MongoConnectionSourceSettings fallback) {
        super(propertyResolver, MongoSettings.PREFIX, fallback)
    }

    @Override
    protected MongoConnectionSourceSettings createBuilder() {
        return new MongoConnectionSourceSettings()
    }

    @Override
    protected MongoConnectionSourceSettings toConfiguration(MongoConnectionSourceSettings builder) {
        return builder
    }

    @Override
    protected void newChildBuilder(Object builder, String configurationPath) {
        if(builder instanceof MongoClientSettings.Builder) {
            clientOptionsBuilder = (MongoClientSettings.Builder)builder
        }
        applyConnectionString(builder)
        applyCredentials(builder)
    }

    @Override
    Object newChildBuilderForFallback(Object childBuilder, Object fallbackConfig) {
        if(( childBuilder instanceof MongoClientSettings.Builder) && (fallbackConfig instanceof MongoClientSettings.Builder)) {
            return MongoClientSettings.builder(((MongoClientSettings.Builder)fallbackConfig).build())
        }
        return childBuilder
    }

    @Override
    protected void startBuild(Object builder, String configurationPath) {
        applyCredentials(builder)
        applyConnectionString(builder)
    }

    protected void applyCredentials(builder) {
        def credentialListMethod = ReflectionUtils.findMethod(builder.getClass(), 'credentialList', List)
        if (credentialListMethod != null) {
            def username = rootBuilder.username
            def password = rootBuilder.password
            def databaseName = rootBuilder.database

            if (username != null && password != null) {
                def credential = MongoCredential.createCredential(username, databaseName, password.toCharArray())
                credentialListMethod.invoke(builder, Arrays.asList(credential))
            }
        }
    }

    protected void applyConnectionString(builder) {
        def applyConnectionStringMethod = ReflectionUtils.findMethod(builder.getClass(), 'applyConnectionString', ConnectionString)
        if (applyConnectionStringMethod != null) {
            ConnectionString connectionString = rootBuilder.url
            if (connectionString == null) {

                def username = rootBuilder.username
                def password = rootBuilder.password
                def host = rootBuilder.host
                def port = rootBuilder.port
                def databaseName = rootBuilder.databaseName
                String uAndP = username && password ? "$username:$password@" : ''
                connectionString = new ConnectionString("mongodb://${uAndP}${host}:${port}/$databaseName")
            }
            applyConnectionStringMethod.invoke(builder, connectionString)
        }
    }
}
