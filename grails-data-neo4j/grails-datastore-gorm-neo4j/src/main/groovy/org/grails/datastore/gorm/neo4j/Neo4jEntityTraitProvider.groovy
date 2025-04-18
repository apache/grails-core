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
package org.grails.datastore.gorm.neo4j

import grails.neo4j.Neo4jEntity
import groovy.transform.CompileStatic
import org.grails.compiler.gorm.GormEntityTraitProvider
import org.grails.datastore.mapping.reflect.ClassUtils


/**
 * Tells GORM to use the Neo4jEntity trait
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class Neo4jEntityTraitProvider implements GormEntityTraitProvider {
    final Class entityTrait = Neo4jEntity
    final boolean available = ClassUtils.isPresent("org.neo4j.driver.Driver")
}
