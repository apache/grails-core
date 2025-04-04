package org.grails.datastore.gorm.jdbc.connections;

import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.gorm.jdbc.DataSourceBuilder;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.springframework.core.env.PropertyResolver;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.jndi.JndiObjectFactoryBean;

import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * A {@link ConnectionSourceFactory} for creating JDBC {@link DataSource} connections
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class DataSourceConnectionSourceFactory extends AbstractConnectionSourceFactory<DataSource, DataSourceSettings> {
    @Override
    protected <F extends ConnectionSourceSettings> DataSourceSettings buildSettings(String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource) {
        String configurationPrefix = isDefaultDataSource ? Settings.SETTING_DATASOURCE : Settings.SETTING_DATASOURCES + '.' + name;
        DataSourceSettingsBuilder builder;
        if(isDefaultDataSource) {
            String qualified = Settings.SETTING_DATASOURCES + '.' + Settings.SETTING_DATASOURCE;
            Map config = configuration.getProperty(qualified, Map.class, Collections.emptyMap());
            if(!config.isEmpty()) {
                builder = new DataSourceSettingsBuilder(configuration, qualified);
            }
            else {
                builder = new DataSourceSettingsBuilder(configuration, configurationPrefix);
            }
        }
        else {
            builder = new DataSourceSettingsBuilder(configuration, configurationPrefix);
        }

        DataSourceSettings settings = builder.build();
        return settings;
    }

    public ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {

        DataSource dataSource;
        if(settings.getJndiName() != null && !settings.getJndiName().isEmpty()) {
            JndiObjectFactoryBean jndiObjectFactoryBean = new JndiObjectFactoryBean();
            jndiObjectFactoryBean.setExpectedType(DataSource.class);
            jndiObjectFactoryBean.setJndiName(settings.getJndiName());
            try {
                jndiObjectFactoryBean.afterPropertiesSet();
            } catch (NamingException e) {
                throw new ConfigurationException("Unable to configure JNDI data source: " + e.getMessage(), e);
            }
            dataSource = (DataSource)jndiObjectFactoryBean.getObject();
            dataSource = proxy(dataSource, settings);
            return new DefaultConnectionSource<>(name, dataSource, settings);
        }
        else {

            DataSourceBuilder dataSourceBuilder = new DataSourceBuilder(getClass().getClassLoader());
            dataSourceBuilder.setPooled(settings.isPooled());
            dataSourceBuilder.setReadOnly(settings.isReadOnly());
            String driverClassName = settings.getDriverClassName();
            String username = settings.getUsername();
            String password = settings.getPassword();
            Map properties = settings.getProperties();
            String url = settings.getUrl();
            Class type = settings.getType();

            if(properties != null && !properties.isEmpty()) {
                dataSourceBuilder.properties(settings.toProperties());
            }
            dataSourceBuilder.url(url);

            if(driverClassName != null) {
                dataSourceBuilder.driverClassName(driverClassName);
            }
            if(username != null && password != null) {
                dataSourceBuilder.username(username);
                dataSourceBuilder.password(password);
            }

            if (type != null) {
                dataSourceBuilder.type(type);
            }

            dataSource = dataSourceBuilder.build();
            dataSource = proxy(dataSource, settings);
            return new DataSourceConnectionSource(name, dataSource, settings);
        }
    }

    protected DataSource proxy(DataSource dataSource, DataSourceSettings settings) {
        if(settings.isLazy()) {
            dataSource = new LazyConnectionDataSourceProxy(dataSource);
        }
        if(settings.isTransactionAware()) {
            dataSource = new TransactionAwareDataSourceProxy(dataSource);
        }
        return dataSource;
    }

    @Override
    public Serializable getConnectionSourcesConfigurationKey() {
        return Settings.SETTING_DATASOURCES;
    }
}
