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

package org.grails.datastore.mapping.multitenancy.resolvers

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

/**
 * A {@link TenantResolver} that resolves from a System property called "gorm.tenantId". Useful for testing.
 */
@CompileStatic
class SystemPropertyTenantResolver implements TenantResolver {

    public static final String PROPERTY_NAME = "gorm.tenantId"

    @Override
    Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        def value = System.getProperty(PROPERTY_NAME)
        if(value) {
            return value
        }
        else {
            throw new TenantNotFoundException()
        }
    }
}
