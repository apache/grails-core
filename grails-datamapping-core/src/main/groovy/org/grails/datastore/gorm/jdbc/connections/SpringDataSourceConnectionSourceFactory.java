/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
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
