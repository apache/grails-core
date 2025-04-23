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

package grails.gorm.rx.api

import rx.Observable

/**
 * Methods on instances
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface RxGormInstanceOperations<D> {

    /**
     * Obtain the objects identifier
     *
     * @param instance The instance
     * @return The id or null if there is none
     */
    Serializable ident(D instance)

    /**
     * Saves an entity and returns an {@link rx.Observable}, picking either an insert or an update automatically based on whether the object has an id already.
     *
     * @return An {@link rx.Observable} with the result of the operation
     */
    Observable<D> save(D instance)

    /**
     * Saves an entity and returns an {@link Observable}, picking either an insert or an update automatically based on whether the object has an id already.
     *
     * @param arguments The arguments to the save method
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> save(D instance, Map arguments)


    /**
     * Saves an entity and returns an {@link Observable}, forcing an insert operation regardless whether an identifier is already present or not
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> insert(D instance)

    /**
     * Saves an entity and returns an {@link Observable}, forcing an insert operation regardless whether an identifier is already present or not
     *
     * @param arguments The arguments to the save method
     *
     * @return An {@link Observable} with the result of the operation
     */
    Observable<D> insert(D instance, Map arguments)

    /**
     * Deletes an entity
     *
     * @return An observable that returns a boolean true if successful
     */
    Observable<Boolean> delete(D instance)

    /**
     * Deletes an entity
     *
     * @param instance The instance
     * @param arguments The arguments
     *
     * @return An observable that returns a boolean true if successful
     */
    Observable<Boolean> delete(D instance, Map arguments)
}