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
package org.grails.datastore.mapping.query.api;

/**
 * Models a list of projections
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ProjectionList {

    /**
     * A Projection that obtains the id of an object
     *
     * @return The projection list
     */
    ProjectionList id();

    /**
     * Count the number of records returned
     *
     * @return The projection list
     */
    ProjectionList count();

    /**
     * Count the number of records returned
     *
     * @param property The property name to count
     * @return The projection list
     */
    ProjectionList countDistinct(String property);

    /**
     * Defines a group by projection for datastores that support it
     *
     * @param property The property name
     * @return The projection list
     */
    ProjectionList groupProperty(String property);

    /**
     * Projection to return only distinct records
     *
     * @return The projection list
     */
    ProjectionList distinct();

    /**
     * Projection to return only distinct properties
     *
     * @param property The property name to use
     * @return The projection list
     */
    ProjectionList distinct(String property);

    /**
     * Count the number of records returned
     *
     * @return The projection list
     */
    ProjectionList rowCount();

    /**
     * A projection that obtains the value of a property of an entity
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList property(String name);

    /**
     * Computes the sum of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList sum(String name);

    /**
     * Computes the min value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList min(String name);

    /**
     * Computes the max value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList max(String name);

    /**
     * Computes the average value of a property
     *
     * @param name The name of the property
     * @return The PropertyProjection instance
     */
    ProjectionList avg(String name);
}
