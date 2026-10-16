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

package grails.build.support

import spock.lang.Specification

class MetaClassRegistryCleanerSpec extends Specification {

    def "an instance given a metaclass of its own twice is tracked under one key and cleaned up"() {
        given: 'a registered cleaner and an instance with no metaclass of its own'
        MetaClassRegistryCleaner cleaner = MetaClassRegistryCleaner.createAndRegister()
        Object target = new Object()
        ExpandoMetaClass first = new ExpandoMetaClass(Object, false, true)
        first.hello = { -> 'hello' }
        first.initialize()
        ExpandoMetaClass second = new ExpandoMetaClass(Object, false, true)
        second.again = { -> 'again' }
        second.initialize()

        when: 'the instance is given one metaclass, then another (each assignment is a registry change)'
        target.metaClass = first
        target.metaClass = second

        then: 'the second change looks the first one up under a new key for the same instance, and finds it'
        notThrown(StackOverflowError)
        target.again() == 'again'

        when: 'the cleaner restores what it tracked'
        MetaClassRegistryCleaner.cleanAndRemove(cleaner)
        target.again()

        then: 'the second metaclass is gone'
        thrown(MissingMethodException)
    }

    def "instances that are equal by value keep metaclasses of their own"() {
        given:
        MetaClassRegistryCleaner cleaner = MetaClassRegistryCleaner.createAndRegister()
        List first = []
        List second = []

        when:
        first.metaClass.whoami = { -> 'first' }
        second.metaClass.whoami = { -> 'second' }

        then: 'the key compares the instances by identity, not by value'
        first == second
        first.whoami() == 'first'
        second.whoami() == 'second'

        cleanup:
        MetaClassRegistryCleaner.cleanAndRemove(cleaner)
    }
}
