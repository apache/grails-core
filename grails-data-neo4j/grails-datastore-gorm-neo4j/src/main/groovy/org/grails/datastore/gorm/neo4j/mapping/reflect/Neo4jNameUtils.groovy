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
package org.grails.datastore.gorm.neo4j.mapping.reflect

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.parsers.PlingStemmer
import org.grails.datastore.mapping.reflect.NameUtils


/**
 * Utilities for Neo4j naming conventions
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class Neo4jNameUtils extends NameUtils {

    /**
     * @param word The word
     * @return Whether the given word is plural
     */
    static boolean isPlural( String word ) {
        PlingStemmer.isPlural(word)
    }

    /**
     * @param word The word
     * @return Whether the given word is singular
     */
    static boolean isSingular( String word ) {
        PlingStemmer.isSingular( word )
    }
}
