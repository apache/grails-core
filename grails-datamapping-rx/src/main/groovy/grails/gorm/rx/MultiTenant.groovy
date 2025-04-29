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

package grails.gorm.rx

import grails.gorm.api.GormAllOperations
import grails.gorm.rx.api.RxGormAllOperations
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.gorm.rx.api.RxGormEnhancer

/**
 * A trait for domain classes that should be treated as multi tenant
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
trait MultiTenant<D> extends RxEntity<D> {
    /**
     * Execute the closure with the given tenantId
     *
     * @param tenantId The tenant id
     * @param callable The closure
     * @return The result of the closure
     */
    static <T> T withTenant(Serializable tenantId, @DelegatesTo(RxGormAllOperations) Closure<T> callable) {
        RxGormEnhancer.findStaticApi(this).withTenant tenantId, callable
    }

    /**
     * Execute the closure for each tenant
     *
     * @param callable The closure
     * @return The result of the closure
     */
    static RxGormAllOperations<D> eachTenant( @DelegatesTo(RxGormAllOperations) Closure callable) {
        RxGormEnhancer.findStaticApi(this, ConnectionSource.DEFAULT).eachTenant callable
    }

    /**
     * Return the {@link GormAllOperations} for the given tenant id
     *
     * @param tenantId The tenant id
     * @return The operations
     */
    static RxGormAllOperations<D> withTenant(Serializable tenantId) {
        (RxGormAllOperations<D>)RxGormEnhancer.findStaticApi(this).withTenant(tenantId)
    }
}