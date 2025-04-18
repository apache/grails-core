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
package org.grails.datastore.gorm.neo4j;

import java.io.Serializable;

/**
 * An interface for generating unique identifiers for instances
 *
 * @author Stefan
 * @author Graeme Rocher
 */
public interface IdGenerator {
    /**
     * Default id generator types
     */
    enum Type {
        /**
         * Native identifiers using the Node id
         */
        NATIVE,
        /**
         * Assigned identifiers, assigned by the developer
         */
        ASSIGNED,
        /**
         * Identifiers generated using the Snowflake algorithm
         */
        SNOWFLAKE,
        /**
         * Custom generator
         */
        CUSTOM
    }

    /**
     * @return Generate and return the next identifier
     */
    Serializable nextId();

}
