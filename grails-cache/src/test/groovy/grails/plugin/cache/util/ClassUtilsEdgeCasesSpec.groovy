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
package grails.plugin.cache.util

import spock.lang.Specification

class ClassUtilsEdgeCasesSpec extends Specification {

    void 'a missing property or field resolves to null'() {
        expect:
        ClassUtils.getPropertyOrFieldValue(new CueTarget(), 'nothing') == null
        ClassUtils.getPropertyOrFieldValue(new CueTarget(), 'nullField') == null
    }

    void 'a getter that throws falls back to the field'() {
        expect:
        ClassUtils.getPropertyOrFieldValue(new CueTarget(), 'broken') == 'field'
    }

    void 'inherited fields and getters are found'() {
        expect:
        ClassUtils.getPropertyOrFieldValue(new CueChild(), 'parentField') == 'parent'
        ClassUtils.getPropertyOrFieldValue(new CueChild(), 'parentProperty') == 'getter'
        ClassUtils.getPropertyOrFieldValue(new CueChild(), 'staticField') == 'static'
    }

    void 'a null object throws'() {
        when:
        ClassUtils.getPropertyOrFieldValue(null, 'anything')

        then:
        thrown(NullPointerException)
    }

}

class CueTarget {

    private String broken = 'field'
    private String nullField = null

    String getBroken() {
        throw new IllegalStateException('boom')
    }

}

class CueParent {

    private static final String staticField = 'static'
    private String parentField = 'parent'

    String getParentProperty() {
        'getter'
    }

}

class CueChild extends CueParent {
}
