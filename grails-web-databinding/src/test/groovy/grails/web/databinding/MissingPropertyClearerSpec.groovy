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
package grails.web.databinding

import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import spock.lang.Specification

import grails.databinding.DataBindingSource

class MissingPropertyClearerSpec extends Specification {

    private DataBindingSource sourceOf(Map values) {
        Stub(DataBindingSource) {
            getPropertyNames() >> values.keySet()
            getPropertyValue(_ as String) >> { String name -> values[name] }
            containsProperty(_ as String) >> { String name -> values.containsKey(name) }
        }
    }

    void 'a property present in the include list but missing from the source is cleared to null'() {
        given:
        MpcTarget target = new MpcTarget(name: 'original', age: 5)
        DataBindingSource source = sourceOf([age: 7])
        BindingResult result = new BeanPropertyBindingResult(target, 'target')

        when:
        MissingPropertyClearer.clearMissingIncludedProperties(target, source, ['name', 'age'], null, null, result)

        then:
        target.name == null
        target.age == 5
        !result.hasErrors()
    }

    void 'a property present in the source is left untouched'() {
        given:
        MpcTarget target = new MpcTarget(name: 'original')
        DataBindingSource source = sourceOf([name: 'original'])
        BindingResult result = new BeanPropertyBindingResult(target, 'target')

        when:
        MissingPropertyClearer.clearMissingIncludedProperties(target, source, ['name'], null, null, result)

        then:
        target.name == 'original'
    }

    void 'an excluded property is never cleared even when missing from the source and included'() {
        given:
        MpcTarget target = new MpcTarget(name: 'original')
        DataBindingSource source = sourceOf([:])
        BindingResult result = new BeanPropertyBindingResult(target, 'target')

        when:
        MissingPropertyClearer.clearMissingIncludedProperties(target, source, ['name'], ['name'], null, result)

        then:
        target.name == 'original'
    }

    void 'a filter prefix scopes which source key is checked for presence'() {
        given:
        MpcTarget target = new MpcTarget(name: 'original')
        DataBindingSource source = sourceOf(['book.name': 'present'])
        BindingResult result = new BeanPropertyBindingResult(target, 'target')

        when:
        MissingPropertyClearer.clearMissingIncludedProperties(target, source, ['name'], null, 'book', result)

        then:
        target.name == 'original'
    }

    void 'a non included property is never cleared'() {
        given:
        MpcTarget target = new MpcTarget(name: 'original')
        DataBindingSource source = sourceOf([:])
        BindingResult result = new BeanPropertyBindingResult(target, 'target')

        when:
        MissingPropertyClearer.clearMissingIncludedProperties(target, source, ['age'], null, null, result)

        then:
        target.name == 'original'
    }

}

class MpcTarget {

    String name
    Integer age

}
