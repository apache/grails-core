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
