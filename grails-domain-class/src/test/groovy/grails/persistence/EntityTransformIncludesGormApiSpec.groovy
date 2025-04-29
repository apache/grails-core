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
package grails.persistence

import spock.lang.Specification


/**
 * Created by graemerocher on 20/06/2014.
 */
class EntityTransformIncludesGormApiSpec extends Specification{

    void "Test that with the presence of grails-datastore-gorm that the GORM API is added to compiled entities annotated with @Entity"() {


        when:"A entity annotated with @Entity is compiled"
            def cls = new GroovyClassLoader().parseClass('''
import grails.persistence.*

@Entity
class Book { String title }
''')

        then:"The class has the GORM APIs added to it"
            cls.getMethod('getErrors', null) != null
    }
}
