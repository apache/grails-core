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

package org.grails.gorm.rx.finders

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.rx.RxDatastoreClient

/**
 * Implementation of findBy* dynamic finder for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class FindByFinder extends org.grails.datastore.gorm.finders.FindByFinder {

    final RxDatastoreClient datastoreClient

    FindByFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient.mappingContext)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected Object doInvokeInternal(DynamicFinderInvocation invocation) {
        def javaClass = invocation.getJavaClass()
        def query = datastoreClient.createQuery(javaClass)
        query = buildQuery(invocation, javaClass, query)
        invokeQuery(query)
    }
}
