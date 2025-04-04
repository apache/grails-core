/*
 * Copyright 2017 the original author or authors.
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
package grails.gorm.multitenancy

/**
 * A Service with utility methods for working with Multi-Tenancy
 *
 * @author Graeme Rocher
 * @since 6.1
 */
interface TenantService {

    /**
     * Execute the given closure for each tenant. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     *
     * @param callable The closure
     * @return The result of the closure
     */
    void eachTenant(Closure callable)


    /**
     * @return The current tenant id
     *
     * @throws org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException if no current tenant is found
     */
    Serializable currentId()

    /**
     * Execute the given closure without any tenant id. In Multi tenancy mode SINGLE this will execute against the default data source. If multi tenancy mode
     * MULTI this will execute without including the "tenantId" on any query. Use with caution.
     *
     * @param callable The closure
     * @return The result of the closure
     */
    public <T> T withoutId(Closure<T> callable)

    /**
     * Execute the given closure with the current tenant. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     *
     * @param callable The closure
     * @return The result of the closure
     */
    public <T> T withCurrent(Closure<T> callable)

    /**
     * Execute the given closure with given tenant id. This method will create a new datastore session for the scope of the call and hence is designed to be used to manage the connection life cycle
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    public <T> T withId(Serializable tenantId, Closure<T> callable)
}