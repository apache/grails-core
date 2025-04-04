package org.grails.datastore.gorm.jdbc.connections;

import org.grails.datastore.mapping.config.Settings;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.sql.DataSource;

/**
 * A {@link DataSourceConnectionSourceFactory} for building data sources that could come from spring
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class SpringDataSourceConnectionSourceFactory extends DataSourceConnectionSourceFactory implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public ConnectionSource<DataSource, DataSourceSettings> create(String name, DataSourceSettings settings) {
        String dataSourceName = ConnectionSource.DEFAULT.equals(name) ? Settings.SETTING_DATASOURCE : Settings.SETTING_DATASOURCE + "_" + name;
        dataSourceName = Settings.SETTING_DATASOURCE.equals(name) ? Settings.SETTING_DATASOURCE : dataSourceName;
        DataSource springDataSource;
        try {
            springDataSource = applicationContext.getBean(dataSourceName, DataSource.class);
            return new DataSourceConnectionSource(name, springDataSource, settings);
        } catch (NoSuchBeanDefinitionException e) {
            return super.create(name, settings);
        }
    }
}
