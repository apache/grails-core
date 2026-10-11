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
package org.grails.datastore.gorm.support

import org.springframework.util.SerializationUtils
import spock.lang.Specification

class EventTriggerCallerSpec extends Specification {

    void "method callers invoke the matching method and invert boolean results"() {
        given:
        EtcMethods target = new EtcMethods()

        when:
        EventTriggerCaller caller = EventTriggerCaller.buildCaller('beforeInsert', EtcMethods)

        then:
        caller != null
        !caller.noOperationCaller
        caller.asBoolean()
        caller.invertBooleanReturnValue
        !caller.call(target)
        target.calls == ['beforeInsert']

        when:
        caller.invertBooleanReturnValue = false

        then:
        caller.call(target)
        !caller.invertBooleanReturnValue

        when:
        EventTriggerCaller veto = EventTriggerCaller.buildCaller('beforeDelete', EtcMethods)

        then:
        veto.call(target)
        !EventTriggerCaller.buildCaller('afterLoad', EtcMethods).call(target)
    }

    void "method callers prefer the requested argument types and fall back to any signature"() {
        given:
        EtcMethods target = new EtcMethods()

        when:
        EventTriggerCaller withList = EventTriggerCaller.buildCaller('beforeValidate', EtcMethods, null, [List] as Class[])
        EventTriggerCaller noArgs = EventTriggerCaller.buildCaller('beforeValidate', EtcMethods, null, [] as Class[])
        EventTriggerCaller fallback = EventTriggerCaller.buildCaller('beforeUpdate', EtcMethods, null, [List] as Class[])

        then:
        !withList.call(target, [['name']] as Object[])
        !noArgs.call(target)
        !fallback.call(target, ['ignored', 'extra'] as Object[])
        target.calls == ['beforeValidate:[name]', 'beforeValidate', 'beforeUpdate']

        when: 'a method with fewer parameters than arguments only receives what fits'
        withList.call(target, [['a'], 'extra'] as Object[])

        then:
        target.calls.last() == 'beforeValidate:[a]'
    }

    void "closure fields are invoked with the entity as delegate and static closures are cloned first"() {
        given:
        EtcClosures target = new EtcClosures()

        when:
        EventTriggerCaller instanceCaller = EventTriggerCaller.buildCaller('beforeInsert', EtcClosures)
        EventTriggerCaller staticCaller = EventTriggerCaller.buildCaller('beforeUpdate', EtcClosures)
        EventTriggerCaller notAClosure = EventTriggerCaller.buildCaller('marker', EtcClosures)

        then:
        !instanceCaller.call(target)
        target.calls == ['insert:ETC']
        !staticCaller.call(target, [['x']] as Object[])
        target.calls == ['insert:ETC', 'update:[x]']
        EtcClosures.beforeUpdate.delegate == EtcClosures
        !notAClosure.call(target)
        target.calls.size() == 2
    }

    void "meta class methods and properties are resolved when nothing is declared on the class"() {
        given:
        EtcEmpty target = new EtcEmpty()
        List seen = []
        EtcEmpty.metaClass.beforeInsert = { -> seen << 'meta-method'; false }
        EtcEmpty.metaClass.beforeUpdate = { -> seen << 'meta-property-closure'; true }

        when:
        EventTriggerCaller method = EventTriggerCaller.buildCaller('beforeInsert', EtcEmpty)
        EventTriggerCaller missing = EventTriggerCaller.buildCaller('afterInsert', EtcEmpty)

        then:
        method != null
        method.call(target)
        seen == ['meta-method']
        missing == null

        when:
        EventTriggerCaller wrapped = EventTriggerCaller.wrapNullInNoopCaller(missing)

        then:
        wrapped.noOperationCaller
        !wrapped.asBoolean()
        !wrapped.call(target)
        EventTriggerCaller.wrapNullInNoopCaller(method).is(method)

        cleanup:
        GroovySystem.metaClassRegistry.removeMetaClass(EtcEmpty)
    }

    void "the before validate helper caches callers per class and survives serialization"() {
        given:
        BeforeValidateHelper helper = new BeforeValidateHelper()
        EtcMethods target = new EtcMethods()
        EtcEmpty empty = new EtcEmpty()

        when:
        helper.invokeBeforeValidate(target, ['name'])
        helper.invokeBeforeValidate(target, null)
        helper.invokeBeforeValidate(empty, ['x'])

        then:
        target.calls == ['beforeValidate:[name]', 'beforeValidate']
        BeforeValidateHelper.BEFORE_VALIDATE == 'beforeValidate'

        when:
        BeforeValidateHelper restored = SerializationUtils.deserialize(SerializationUtils.serialize(helper))
        restored.invokeBeforeValidate(target, ['again'])

        then:
        target.calls.last() == 'beforeValidate:[again]'

        when:
        BeforeValidateHelper.BeforeValidateEventTriggerCaller caller =
                new BeforeValidateHelper.BeforeValidateEventTriggerCaller(EtcNoArgValidate, null)
        EtcNoArgValidate noArg = new EtcNoArgValidate()
        caller.call(noArg, ['field'])
        caller.call(noArg, null)

        then:
        noArg.count == 2
    }

}

class EtcMethods {

    List calls = []

    boolean beforeInsert() {
        calls << 'beforeInsert'
        true
    }

    boolean beforeDelete() {
        calls << 'beforeDelete'
        false
    }

    void afterLoad() {
        calls << 'afterLoad'
    }

    void beforeValidate() {
        calls << 'beforeValidate'
    }

    void beforeValidate(List fields) {
        calls << 'beforeValidate:' + fields
    }

    void beforeUpdate() {
        calls << 'beforeUpdate'
    }

}

class EtcClosures {

    List calls = []
    String tag = 'ETC'
    String marker = 'not a closure'

    def beforeInsert = { -> calls << 'insert:' + tag; true }

    static Closure beforeUpdate = { List fields -> calls << 'update:' + fields; true }

}

class EtcEmpty { }

class EtcNoArgValidate {

    int count = 0

    void beforeValidate() {
        count++
    }

}
