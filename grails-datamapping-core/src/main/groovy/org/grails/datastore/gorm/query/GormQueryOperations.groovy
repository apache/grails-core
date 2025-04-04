/*
 * Copyright 2015 original authors
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
package org.grails.datastore.gorm.query



/**
 * Interface defining all typical query GORM operations on class
 *
 * @author Graeme Rocher
 * @since 5.0
 */
interface GormQueryOperations<T> {

    /**
     * Synonym for #get
     */
    T find(Map args , Closure additionalCriteria)

    /**
     * Synonym for #get
     */
    T find(Map args)

    /**
     * Synonym for #get
     */
    T find()

    /**
     * Returns a single result matching the criterion contained within this DetachedCriteria instance
     *
     * @param args The arguments
     * @param additionalCriteria Additional criteria
     * @return A single entity
     */
    T get(Map args, Closure additionalCriteria)

    /**
     * Returns a single result matching the criterion contained within this DetachedCriteria instance
     * @param args The arguments
     * @return A single entity
     */
    T get(Map args)

    /**
     * Returns a single result matching the criterion contained within this DetachedCriteria instance
     *
     * @return A single entity
     */
    T get()

    /**
     * Lists all records matching the criterion contained within this DetachedCriteria instance
     *
     * @return A list of matching instances
     */
    List<T> list()

    /**
     * Lists all records matching the criterion contained within this DetachedCriteria instance
     *
     * @param args The arguments
     * @return A list of matching instances
     */
    List<T> list(Map args)

    /**
     * Lists all records matching the criterion contained within this DetachedCriteria instance
     *
     * @param args The arguments
     * @param additionalCriteria The additional criteria
     * @return A list of matching instances
     */
    List<T> list(Map args , Closure additionalCriteria)

    /**
     * Counts the number of records returned by the query
     *
     * @return The count
     */
    Number count()

    /**
     * Counts the number of records returned by the query
     *
     * @param args The arguments
     * @return The count
     */
    Number count(Map args)

    /**
     * Counts the number of records returned by the query
     *
     * @param args The arguments
     * @param additionalCriteria Any additional criteria
     * @return The count
     */
    Number count(Map args, Closure additionalCriteria)

    /**
     * Method missing handler for dynamic finders
     *
     * @param methodName The method name
     * @param args The arguments
     * @return The return value
     */
    def methodMissing(String methodName, args)

}
