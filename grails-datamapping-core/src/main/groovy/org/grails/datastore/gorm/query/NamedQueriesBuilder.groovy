/* Copyright (C) 2010 SpringSource
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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.NameUtils
/**
 * Handles creation of named queries
 *
 * @author Graeme Rocher
 * @author Jeff Brown
 */
class NamedQueriesBuilder {

    private boolean initialized = false
    private Map<String, Closure> namedQueries = [:]

    @CompileStatic
    Map<String, Closure> evaluate(Closure namedQueriesClosure) {
        Closure closure = (Closure)namedQueriesClosure.clone()
        closure.resolveStrategy = Closure.DELEGATE_ONLY
        closure.delegate = this
        closure.call()
        initialized = true
        return namedQueries
    }

    def methodMissing(String name, args) {
        if (args && args[0] instanceof Closure && !initialized) {
            Closure criteriaClosure = (Closure)args[0]
            namedQueries.put(name, criteriaClosure)

            return null
        }
        throw new MissingMethodException(name, NamedQueriesBuilder, args)
    }
}

