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
package org.grails.datastore.gorm.services.transform.support

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.services.ServiceImplementerAdapter

/**
 * A second, deliberately inert {@link ServiceImplementerAdapter} registered via
 * {@code META-INF/services} purely so that {@code ServiceTransformation} ever has more than one
 * adapter to de-duplicate, exercising the {@code unique { it.class.name }} call it makes over the
 * loaded adapters. It never adapts anything.
 *
 * @see ProbeServiceImplementerAdapter
 */
@CompileStatic
class NoOpServiceImplementerAdapter implements ServiceImplementerAdapter {

    @Override
    ServiceImplementer adapt(ServiceImplementer implementer) {
        return null
    }
}
