package org.grails.datastore.gorm.jdbc.connections

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings

import javax.sql.DataSource

/**
 * DataSource settings
 *
 * @author Graeme Rocher
 */
@Builder(builderStrategy = SimpleStrategy, prefix = '')
@AutoClone
class DataSourceSettings extends ConnectionSourceSettings {
    /**
     * The data source URL, defaults to an H2 in-memory database
     */
    String url = "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000"

    /**
     * The driver class name
     */
    String driverClassName
    /**
     * The username
     */
    String username

    /**
     * The password
     */
    String password

    /**
     * The JNDI name
     */
    String jndiName
    /**
     * Whether the data source is pooled
     */
    boolean pooled = true

    /**
     * Whether the data source is lazy
     */
    boolean lazy = true

    /**
     * Whether the data source is aware of an ongoing Spring transaction
     */
    boolean transactionAware = true

    /**
     * Whether the connection is readonly
     */
    boolean readOnly = false
    /**
     * The dialect to use
     */
    Class dialect

    /**
     * The schema handler to use
     */
    Class<? extends SchemaHandler> schemaHandler = DefaultSchemaHandler

    /**
     * Whether to log SQL
     */
    boolean logSql = false

    /**
     * Whether to format the SQL
     */
    boolean formatSql = false

    /**
     * The default value for `hibernate.hbm2ddl.auto`
     */
    String dbCreate = "none"

    /**
     * The data source properties
     */
    Map properties = [:]

    /**
     * The connection pool to use
     */
    Class<? extends DataSource> type

    /**
     * Convert to Hibernate properties
     *
     * @return The hibernate properties
     */
    @CompileStatic
    Properties toHibernateProperties() {
        Properties props = new Properties()
        props.put("hibernate.hbm2ddl.auto", dbCreate)
        props.put("hibernate.show_sql", String.valueOf(logSql))
        props.put("hibernate.format_sql", String.valueOf(formatSql))
        if(dialect != null) {
            props.put("hibernate.dialect", dialect.name)
        }
        return props
    }

    /**
     * @return Convert to datasource properties
     */
    @CompileStatic
    Map<String,String> toProperties() {
        Map<String,String> properties = new LinkedHashMap<>()
        properties.putAll(this.properties)
        properties.put("url", url)
        if(driverClassName) {
            properties.put("driverClassName", driverClassName)
        }
        if(username) {
            properties.put("username", username)
        }
        if(password) {
            properties.put("username", password)
        }
        if(readOnly) {
            properties.put("defaultReadOnly", String.valueOf(readOnly))
        }
        return properties
    }
}