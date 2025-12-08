/* Copyright 2016 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.datasource;

import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport;
import org.grails.datastore.mapping.model.PersistentEntity;

import java.util.List;

/**
 * Support methods for Multiple data source handling
 *
 * @author Graeme Rocher
 * @since 5.0.2
 */
public class MultipleDataSourceSupport {
    /**
     * If a domain class uses more than one datasource, we need to know which one to use
     * when calling a method without a namespace qualifier.
     *
     * @param domainClass the domain class
     * @return the default datasource name
     */
    public static String getDefaultDataSource(PersistentEntity domainClass) {
        return ConnectionSourcesSupport.getDefaultConnectionSourceName(domainClass);
    }

    public static List<String> getDatasourceNames(PersistentEntity domainClass) {
        return ConnectionSourcesSupport.getConnectionSourceNames(domainClass);
    }

    public static boolean usesDatasource(PersistentEntity domainClass, String dataSourceName) {
        return ConnectionSourcesSupport.usesConnectionSource(domainClass, dataSourceName);
    }
}
