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

package org.grails.datastore.mapping.services

import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.util.ClassUtils

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.lifecycle.Initializable

/**
 * The default {@link ServiceRegistry} implementation
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@Slf4j
class DefaultServiceRegistry implements ServiceRegistry, Initializable {
    /**
     * The datastore this service relates to
     */
    final Datastore datastore

    protected final Map<String,Service> servicesByInterface
    protected final Collection<Service> services = []
    private boolean initialized

    DefaultServiceRegistry(Datastore datastore, boolean exceptionOnLoadError = true) {
        this.datastore = datastore
        Iterable<Service> services = loadServices()
        Map<String, Service> serviceMap = [:]
        Iterator<Service> serviceIterator = services.iterator()
        while(serviceIterator.hasNext()) {
            try {
                Service service = serviceIterator.next()
                this.services.add(service)
                Class[] allInterfaces = ClassUtils.getAllInterfaces(service)
                Class theClass = service.getClass()
                serviceMap.put(theClass.name, service)
                if( theClass.simpleName.startsWith('$') ) {
                    // handle automatically implemented abstract service implementations
                    Class superClass = theClass.getSuperclass()
                    if(superClass != null && superClass != Object.class && Modifier.isAbstract(superClass.modifiers)) {
                        serviceMap.put(superClass.name, service)
                    }

                }
                for(Class i in allInterfaces) {
                    if(isValidInterface(i)) {
                        serviceMap.put(i.name, service)
                    }
                }
            } catch (Throwable e) {
                log.error("Could not load GORM service: ${e.message}", e)
                if(exceptionOnLoadError) {
                    throw e
                }
            }
        }

        servicesByInterface = Collections.unmodifiableMap(
            serviceMap
        )
    }

    @Override
    Collection<Service> getServices() {
        return Collections.unmodifiableCollection(services)
    }

    @Override
    def <T> T getService(Class<T> interfaceType) throws ServiceNotFoundException {
        final Service s = servicesByInterface.get(interfaceType.name)
        if(s == null) {
            throw new ServiceNotFoundException("No service found for type $interfaceType")
        }
        return (T) s
    }

    protected boolean isValidInterface(Class i) {
        i != Service && i != GroovyObject
    }

    protected Iterable<Service> loadServices() {
        ServiceLoader.load(Service)
    }

    @Override
    void initialize() {
        for(s in services) {
            s.datastore = datastore
        }
        this.initialized = true
    }

    @Override
    boolean isInitialized() {
        return this.initialized
    }
}
