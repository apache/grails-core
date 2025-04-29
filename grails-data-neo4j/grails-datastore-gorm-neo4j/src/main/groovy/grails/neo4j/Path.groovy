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

package grails.neo4j

/**
 * Represents a Neo4j path
 *
 * @author Graeme Rocher
 * @see org.neo4j.driver.types.Path
 * @since 6.1
 */
interface Path<S, E> extends Iterable<Segment<S,E>> {

    /**
     * A segment
     *
     * @see org.neo4j.driver.types.Path.Segment
     */
    interface Segment<S, E> {
        Relationship<S, E> relationship()

        S start()

        E end()
    }


    /** @return the start node of this path */
    S start()

    /** @return the end node of this path */
    E end()

    /** @return the number of segments in this path, which will be the same as the number of relationships */
    int length()

    /**
     * @return The domain instances that make up all the nodes
     */
    Iterable nodes()

    /**
     * Whether the path contains the given object. The entity should correctly implement equals/hashCode
     * @param o The object
     * @return True if it does
     */
    boolean contains(Object o)
}
