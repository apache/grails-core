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

package org.grails.datastore.rx.query

import groovy.transform.CompileStatic
import rx.Observable

/**
 * Represents a reactive query implementation in RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
interface RxQuery<T> {


    /**
     * @return All results matching this query as an observable
     */
    Observable<T> findAll()

    /**
     * @param queryArguments The query arguments. These are things like the max, offset etc.
     *
     * @return All results matching this query as an observable
     */
    Observable<T> findAll(Map<String,Object> queryArguments)

    /**
     *
     * @return A single result matching this query as an observable
     */

    Observable<T> singleResult()

    /**
     * @param queryArguments The query arguments. These are things like the max, offset etc.
     *
     * @return A single result matching this query as an observable
     */

    Observable<T> singleResult(Map<String,Object> queryArguments)

    /**
     * update all entities matching this query with the given properties
     *
     * @param properties The properties
     *
     * @return An observable with the number of entities updated
     */
    Observable<Number> updateAll(Map properties)

    /**
     * delete all entities matching this query
     *
     *
     * @return An observable with the number of entities updated
     */
    Observable<Number> deleteAll()
}
