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
package org.grails.datastore.gorm.mongo

import grails.gorm.tests.Pet
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class LikeQuerySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses += [Pet]
    }

    void "Test for like query"() {
        given:
        new Pet(name: "foo").save(flush:true, failOnError:true)
        new Pet(name: "bar").save(flush:true, failOnError:true)
        new Pet(name: "baz").save(flush:true, failOnError:true)
        new Pet(name: "foobar").save(flush:true, failOnError:true)
        new Pet(name: "*").save(flush:true, failOnError:true)
        new Pet(name: "**").save(flush:true, failOnError:true)
        new Pet(name: "***").save(flush:true, failOnError:true)
        manager.session.clear()

        when:
        def results = Pet.findAllByNameLike(search)

        then:
        results*.name == expected

        where:
        search  | expected
        'f'     | []
        'foo'   | ['foo']
        'f%'    | ['foo', 'foobar']
        'f%o'   | ['foo']
        '%foo'  | ['foo']
        'foo%'  | ['foo', 'foobar']
        '%foo%' | ['foo', 'foobar']
        'f.*'   | []
        '*'     | ['*']
        '**'    | ['**']
        '.*'    | []
    }
}