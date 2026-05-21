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
package grails.plugin.json.view.api.jsonapi

import org.grails.datastore.mapping.model.PersistentProperty
import spock.lang.Specification
import spock.lang.Unroll

class DefaultJsonApiIdGeneratorSpec extends Specification {

    @Unroll("DefaultJsonApiIdGenerator.generateId(#subject) == #expectedResult")
    void "test basic ID generation"() {
        given:
            DefaultJsonApiIdRenderer renderer = new DefaultJsonApiIdRenderer()

        when:
            String result = renderer.render(subject, Mock(PersistentProperty) { getName() >> "id "})

        then:
            notThrown(Exception)
            result == expectedResult

        where:
            subject      | expectedResult
            null         | null
            [foo: 'bar'] | null
    }

}
