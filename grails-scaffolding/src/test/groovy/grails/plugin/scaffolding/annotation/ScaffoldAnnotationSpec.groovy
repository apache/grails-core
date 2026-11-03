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
package grails.plugin.scaffolding.annotation

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeHint
import spock.lang.Specification

import org.apache.grails.scaffolding.aot.ScaffoldingRuntimeHints

class ScaffoldAnnotationSpec extends Specification {

    void 'the scaffold annotation is a runtime type annotation with void defaults'() {
        expect:
        Scaffold.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        Scaffold.getAnnotation(Target).value() == [ElementType.TYPE] as ElementType[]
        Scaffold.getDeclaredMethod('value').defaultValue == Void
        Scaffold.getDeclaredMethod('domain').defaultValue == Void
        Scaffold.getDeclaredMethod('readOnly').defaultValue == false
        Scaffold.declaredMethods*.name.sort() == ['domain', 'readOnly', 'value']
    }

    void 'annotation values are readable from an annotated class'() {
        when:
        Scaffold scaffold = Annotated.getAnnotation(Scaffold)
        Scaffold defaults = Defaults.getAnnotation(Scaffold)

        then:
        scaffold.value() == String
        scaffold.domain() == Integer
        scaffold.readOnly()
        defaults.value() == Void
        defaults.domain() == Void
        !defaults.readOnly()
    }

    void 'the runtime hints register the scaffolding types that are present'() {
        given:
        RuntimeHints hints = new RuntimeHints()

        when:
        new ScaffoldingRuntimeHints().registerHints(hints, getClass().classLoader)
        List<TypeHint> typeHints = hints.reflection().typeHints().toList()

        then:
        typeHints*.type*.name.sort() == ['grails.plugin.scaffolding.RestfulServiceController', 'grails.plugin.scaffolding.ScaffoldingViewResolver', 'grails.plugin.scaffolding.annotation.Scaffold']
        typeHints.every { TypeHint hint ->
            hint.memberCategories == [MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS,
                                      MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS] as Set
        }
    }

}

@Scaffold(value = String, domain = Integer, readOnly = true)
class Annotated {
}

@Scaffold
class Defaults {
}
