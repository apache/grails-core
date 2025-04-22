/* Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.databinding

import spock.lang.Issue
import spock.lang.Specification

class BindUsingSpec extends Specification {

    void 'Test BindUsing for specific property'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new ClassWithBindUsingOnProperty()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([name: 'Jeff Was Here']))

        then:
        'JEFF WAS HERE' == obj.name
    }

    @Issue('GRAILS-11048')
    void 'Test inheriting a property marked with BindUsing'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new SubclassOfClassWithBindUsingOnProperty()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([name: 'Jeff Was Here']))

        then:
        'JEFF WAS HERE' == obj.name
    }

    void 'Test BindUsing on the class'() {
        given:
        def binder = new SimpleDataBinder()
        def obj = new ClassWithBindUsing()

        when:
        binder.bind(obj, new SimpleMapDataBindingSource([doubleIt: 9, tripleIt: 20, leaveIt: 30]))

        then:
        obj.doubleIt == 18
        obj.tripleIt == 60
        obj.leaveIt == 30
    }

}

class ClassWithBindUsingOnProperty {
    @BindUsing({
        obj, source -> source['name']?.toUpperCase()
    })
    String name
}

class SubclassOfClassWithBindUsingOnProperty extends ClassWithBindUsingOnProperty {
}

@BindUsing(MultiplyingBindingHelper)
class ClassWithBindUsing {
    Integer leaveIt
    Integer doubleIt
    Integer tripleIt
}

class MultiplyingBindingHelper implements BindingHelper<Integer> {
    Integer getPropertyValue(Object obj, String propertyName, DataBindingSource source) {
        def value = source[propertyName]
        def convertedValue = value
        switch(propertyName) {
            case 'doubleIt':
                convertedValue = value * 2
                break
            case 'tripleIt':
                convertedValue = value * 3
                break
        }
        convertedValue
    }
}