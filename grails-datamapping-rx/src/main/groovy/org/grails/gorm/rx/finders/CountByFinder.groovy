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
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.query.RxQuery

/**
 * Implementation of countBy* dynamic finder for RxGORM
 *
 * @since 6.0
 */
@CompileStatic
class CountByFinder extends org.grails.datastore.gorm.finders.CountByFinder {

    final RxDatastoreClient datastoreClient
    CountByFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient.mappingContext)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected Object doInvokeInternal(DynamicFinderInvocation invocation) {
        Class javaClass = invocation.getJavaClass()
        Query query = datastoreClient.createQuery(javaClass)
        applyAdditionalCriteria(query, invocation.getCriteria())
        applyDetachedCriteria(query, invocation.getDetachedCriteria())
        configureQueryWithArguments(javaClass, query, invocation.getArguments())
        query.add(getJunction(invocation))
        query.projections().count()
        return ((RxQuery) query).singleResult()
    }
}
