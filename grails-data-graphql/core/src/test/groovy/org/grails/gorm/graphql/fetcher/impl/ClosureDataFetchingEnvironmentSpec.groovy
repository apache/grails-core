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

package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.debug.DebugBar
import org.grails.gorm.graphql.domain.general.debug.DebugCircular
import org.grails.gorm.graphql.domain.general.debug.DebugFoo
import org.grails.gorm.graphql.domain.general.debug.DebugFooItem

class ClosureDataFetchingEnvironmentSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [DebugFoo, DebugBar, DebugFooItem, DebugCircular] }

    void "test delegate methods forward to the wrapped environment"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment) {
            getSource() >> 'foo'
        }
        ClosureDataFetchingEnvironment closureEnv = new ClosureDataFetchingEnvironment(environment, null)

        expect:
        closureEnv.source == 'foo'
    }

    void "test getFetchArguments returns null when the domain type is null"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment)
        ClosureDataFetchingEnvironment closureEnv = new ClosureDataFetchingEnvironment(environment, null)

        expect:
        closureEnv.getFetchArguments() == null
        closureEnv.getJoinProperties() == null
    }

    void "test getFetchArguments returns null when the domain type is not a GORM entity"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment)
        ClosureDataFetchingEnvironment closureEnv = new ClosureDataFetchingEnvironment(environment, String)

        expect:
        closureEnv.getFetchArguments() == null
        closureEnv.getJoinProperties() == null
    }

    void "test getFetchArguments and getJoinProperties delegate to EntityFetchOptions for a GORM entity"() {
        given:
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment) {
            getMergedField() >> null
        }
        ClosureDataFetchingEnvironment closureEnv = new ClosureDataFetchingEnvironment(environment, DebugFoo)

        expect:
        closureEnv.getJoinProperties() == [] as Set
        closureEnv.getFetchArguments() == [:]
    }
}
