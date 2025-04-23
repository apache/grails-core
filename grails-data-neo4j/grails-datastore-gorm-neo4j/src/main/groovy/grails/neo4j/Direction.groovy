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

import groovy.transform.CompileStatic

/**
 * Used to configure the direction of a relationship
 *
 * @author Graeme Rocher
 * @since 6.0.5
 */
@CompileStatic
enum Direction {
    INCOMING, OUTGOING, BOTH

    @Override
    String toString() {
        switch (this) {
            case INCOMING: return '<-'
            case OUTGOING: return '->'
            case BOTH: return '<->'
        }
    }

    /**
     * @return Whether the direction is incoming
     */
    boolean isIncoming() {
        return this == INCOMING || this == BOTH
    }

    /**
     * @return Whether the direction is outgoing
     */
    boolean isOutgoing() {
        return this == OUTGOING || this == BOTH
    }
}