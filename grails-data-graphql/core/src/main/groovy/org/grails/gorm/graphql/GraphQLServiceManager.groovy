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

package org.grails.gorm.graphql

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.services.ServiceNotFoundException

/**
 * Used to store references to the actual implementations of most of
 * the interfaces used in the project to make it easier to pass
 * multiple managers (services) to methods.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class GraphQLServiceManager {

    protected Map<Class, Object> services = [:]

    void registerService(Class clazz, Object service) {
        services.put(clazz, service)
    }

    public <T extends Object> T getService(Class<T> serviceType) throws ServiceNotFoundException {
        if (services.containsKey(serviceType)) {
            return (T)services.get(serviceType)
        }
        throw new ServiceNotFoundException("No GraphQL service could be found for ${serviceType.name}")
    }

}
