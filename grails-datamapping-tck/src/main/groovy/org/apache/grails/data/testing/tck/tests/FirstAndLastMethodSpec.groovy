/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.PersonWithCompositeKey
import org.apache.grails.data.testing.tck.domains.SimpleWidget
import org.apache.grails.data.testing.tck.domains.SimpleWidgetWithNonStandardId
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.PendingFeatureIf

class FirstAndLastMethodSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([SimpleWidget, PersonWithCompositeKey, SimpleWidgetWithNonStandardId])
    }

    void "Test first and last method with empty datastore"() {
        given:
        assert SimpleWidget.count() == 0

        when:
        def result = SimpleWidget.first()

        then:
        result == null

        when:
        result = SimpleWidget.last()

        then:
        result == null
    }

    void "Test first and last method with multiple entities in the datastore"() {
        given:
        assert new SimpleWidget(name: 'one', spanishName: 'uno').save()
        assert new SimpleWidget(name: 'two', spanishName: 'dos').save()
        assert new SimpleWidget(name: 'three', spanishName: 'tres').save()
        assert SimpleWidget.count() == 3

        when:
        def result = SimpleWidget.first()

        then:
        result?.name == 'one'

        when:
        result = SimpleWidget.last()

        then:
        result?.name == 'three'
    }

    void "Test first and last method with one entity"() {
        given:
        assert new SimpleWidget(name: 'one', spanishName: 'uno').save()
        assert SimpleWidget.count() == 1

        when:
        def result = SimpleWidget.first()

        then:
        result?.name == 'one'

        when:
        result = SimpleWidget.last()

        then:
        result?.name == 'one'
    }

    void "Test first and last method with sort parameter"() {
        given:
        assert new SimpleWidget(name: 'one', spanishName: 'uno').save()
        assert new SimpleWidget(name: 'two', spanishName: 'dos').save()
        assert new SimpleWidget(name: 'three', spanishName: 'tres').save()
        assert SimpleWidget.count() == 3

        when:
        def result = SimpleWidget.first(sort: 'name')

        then:
        result?.name == 'one'

        when:
        result = SimpleWidget.last(sort: 'name')

        then:
        result?.name == 'two'

        when:
        result = SimpleWidget.first('name')

        then:
        result?.name == 'one'

        when:
        result = SimpleWidget.last('name')

        then:
        result?.name == 'two'

        when:
        result = SimpleWidget.first(sort: 'spanishName')

        then:
        result?.spanishName == 'dos'

        when:
        result = SimpleWidget.last(sort: 'spanishName')

        then:
        result?.spanishName == 'uno'

        when:
        result = SimpleWidget.first('spanishName')

        then:
        result?.spanishName == 'dos'

        when:
        result = SimpleWidget.last('spanishName')

        then:
        result?.spanishName == 'uno'
    }

    void "Test first and last method with non standard identifier"() {
        given:
        ['one', 'two', 'three'].each { name ->
            assert new SimpleWidgetWithNonStandardId(name: name).save()
        }
        assert SimpleWidgetWithNonStandardId.count() == 3

        when:
        def result = SimpleWidgetWithNonStandardId.first()

        then:
        result?.name == 'one'

        when:
        result = SimpleWidgetWithNonStandardId.last()

        then:
        result?.name == 'three'
    }

    @PendingFeatureIf(
            value = {
                System.getProperty('hibernate5.gorm.suite')
            },
            reason = 'Was previously @Ignore'
    )
    void "Test first and last method with composite key"() {
        given:
        assert new PersonWithCompositeKey(firstName: 'Steve', lastName: 'Harris', age: 56).save()
        assert new PersonWithCompositeKey(firstName: 'Dave', lastName: 'Murray', age: 54).save()
        assert new PersonWithCompositeKey(firstName: 'Adrian', lastName: 'Smith', age: 55).save()
        assert new PersonWithCompositeKey(firstName: 'Bruce', lastName: 'Dickinson', age: 53).save()
        assert PersonWithCompositeKey.count() == 4

        when:
        def result = PersonWithCompositeKey.first()

        then:
        result?.firstName == 'Steve'

        when:
        result = PersonWithCompositeKey.last()

        then:
        result?.firstName == 'Bruce'

        when:
        result = PersonWithCompositeKey.first('firstName')

        then:
        result?.firstName == 'Adrian'

        when:
        result = PersonWithCompositeKey.last('firstName')

        then:
        result?.firstName == 'Steve'

        when:
        result = PersonWithCompositeKey.first(sort: 'firstName')

        then:
        result?.firstName == 'Adrian'

        when:
        result = PersonWithCompositeKey.last(sort: 'firstName')

        then:
        result?.firstName == 'Steve'

        when:
        result = PersonWithCompositeKey.first('age')

        then:
        result?.firstName == 'Bruce'

        when:
        result = PersonWithCompositeKey.last('age')

        then:
        result?.firstName == 'Steve'

        when:
        result = PersonWithCompositeKey.first(sort: 'age')

        then:
        result?.firstName == 'Bruce'

        when:
        result = PersonWithCompositeKey.last(sort: 'age')

        then:
        result?.firstName == 'Steve'
    }
}
