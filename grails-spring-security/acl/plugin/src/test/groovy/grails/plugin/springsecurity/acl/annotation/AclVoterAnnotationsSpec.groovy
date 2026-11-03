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
package grails.plugin.springsecurity.acl.annotation

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import spock.lang.Specification

class AclVoterAnnotationsSpec extends Specification {

    void 'AclVoter targets fields, methods and types and defaults permissions to READ'() {
        expect:
        AclVoter.getAnnotation(Target).value().toList().containsAll([ElementType.FIELD, ElementType.METHOD, ElementType.TYPE])
        AclVoter.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        AclVoter.isAnnotationPresent(Inherited)
        AclVoter.isAnnotationPresent(Documented)
        AclVoter.getDeclaredMethod('permissions').defaultValue == ['READ'] as String[]
        AclVoter.getDeclaredMethod('name').defaultValue == null
        AclVoter.getDeclaredMethod('configAttribute').defaultValue == null
    }

    void 'AclVoters wraps repeated AclVoter annotations and defaults to none'() {
        expect:
        AclVoters.getAnnotation(Target).value() == [ElementType.TYPE] as ElementType[]
        AclVoters.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        AclVoters.isAnnotationPresent(Inherited)
        AclVoters.isAnnotationPresent(Documented)
        AclVoters.getDeclaredMethod('value').defaultValue == new AclVoter[0]
    }

    void 'the annotations can be read back from an annotated class'() {
        when:
        AclVoters voters = AvsAnnotated.getAnnotation(AclVoters)

        then:
        voters.value().length == 2
        voters.value()[0].name() == 'first'
        voters.value()[0].configAttribute() == 'ACL_FIRST'
        voters.value()[0].permissions() == ['READ'] as String[]
        voters.value()[1].name() == 'second'
        voters.value()[1].permissions() == ['WRITE', 'ADMINISTRATION'] as String[]
    }

}

@AclVoters([
        @AclVoter(name = 'first', configAttribute = 'ACL_FIRST'),
        @AclVoter(name = 'second', configAttribute = 'ACL_SECOND', permissions = ['WRITE', 'ADMINISTRATION'])
])
class AvsAnnotated {
}
