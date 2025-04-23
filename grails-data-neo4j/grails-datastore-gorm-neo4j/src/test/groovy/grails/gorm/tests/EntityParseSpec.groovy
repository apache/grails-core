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

package grails.gorm.tests

import grails.neo4j.Neo4jEntity
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

/**
 * Created by graemerocher on 05/07/16.
 */
class EntityParseSpec extends Specification {

    void "test parse entity at runtime"() {
        when:"An entity is parsed"
        def gcl = new GroovyClassLoader()
        Class cls = gcl.parseClass('''
@grails.gorm.annotation.Entity
class Foo {
    String title
}
''')

        then:"The class is correct"
        Neo4jEntity.isAssignableFrom(cls)
        new ClassNode(cls).methods
    }
}
