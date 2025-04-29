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

import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

/**
 * Created by graemerocher on 25/08/2016.
 */
class SingleCharPropertySpec extends Specification {
    void "Test parse GORM entity with single char properties"() {
        when:"A gorm entity is parsed"
        def cls = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Person {
    String firstName
    String lastName
}

@Entity
class PersonLink {
    Person a
    Person b

    String toString() {
        "$a -> $b"
    }
}
''')
        then:"It is a valid class"
        new ClassNode(cls).methods
    }
}
