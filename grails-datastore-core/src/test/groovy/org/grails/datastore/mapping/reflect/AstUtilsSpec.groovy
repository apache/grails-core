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

package org.grails.datastore.mapping.reflect

import java.lang.reflect.Modifier

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import spock.lang.Specification

/**
 * Created by graemerocher on 19/04/2017.
 */
class AstUtilsSpec extends Specification {

    void "test implements interface"() {
        given:
        ClassNode node = ClassHelper.make("Test")
        def itfc = ClassHelper.make("ITest")
        node.addInterface(itfc)

        expect:
        AstUtils.implementsInterface(node, itfc)
        AstUtils.implementsInterface(node, itfc.name)
        !AstUtils.implementsInterface(node, "Another")
    }

    void "findInterface finds an interface listed after one with super-interfaces of its own"() {
        given:
        ClassNode base = anInterface('Base')
        ClassNode target = anInterface('grails.gorm.rx.RxEntity')
        ClassNode entity = new ClassNode('Book', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE,
                [anInterface('WithSuper', base), target] as ClassNode[], null)
        ClassNode mixed = new ClassNode('Author', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE,
                [anInterface('Mixed', anInterface('Deep', base), target)] as ClassNode[], null)

        expect:
        AstUtils.findInterface(entity, target.name).is(target)
        AstUtils.findInterface(mixed, target.name).is(target)
        AstUtils.findInterface(entity, 'Another') == null
    }

    private static ClassNode anInterface(String name, ClassNode... superInterfaces) {
        new ClassNode(name, Modifier.PUBLIC | Modifier.INTERFACE | Modifier.ABSTRACT, ClassHelper.OBJECT_TYPE,
                superInterfaces, null)
    }

    void "copyAnnotations copies an annotation the target doesn't already carry"() {
        given:
        def from = newMethodNode()
        def to = newMethodNode()
        def annotationType = ClassHelper.make("grails.gorm.transactions.NotTransactional")
        from.addAnnotation(new AnnotationNode(annotationType))

        when:
        AstUtils.copyAnnotations(from, to)

        then:
        to.getAnnotations(annotationType).size() == 1
    }

    void "copyAnnotations copies repeatable annotations by default"() {
        given: "a source carrying the same annotation type twice, as a repeatable annotation legitimately does"
        def from = newMethodNode()
        def to = newMethodNode()
        def annotationType = ClassHelper.make("jakarta.validation.constraints.Pattern")
        from.addAnnotation(new AnnotationNode(annotationType))
        from.addAnnotation(new AnnotationNode(annotationType))

        when:
        AstUtils.copyAnnotations(from, to)

        then: "both are copied - the default must not silently drop all but the first"
        to.getAnnotations(annotationType).size() == 2
    }

    void "copyAnnotations with skipExisting does not add a type the target already carries"() {
        given: "a target that a caller has already annotated directly, e.g. to avoid double-adding @NotTransactional/@ReadOnly"
        def from = newMethodNode()
        def to = newMethodNode()
        def annotationType = ClassHelper.make("grails.gorm.transactions.NotTransactional")
        from.addAnnotation(new AnnotationNode(annotationType))
        to.addAnnotation(new AnnotationNode(annotationType))

        when:
        AstUtils.copyAnnotations(from, to, null, null, true)

        then: "the target keeps its own single instance rather than gaining a duplicate, which Groovy's compiler rejects"
        to.getAnnotations(annotationType).size() == 1
    }

    void "copyAnnotations without skipExisting keeps the pre-existing annotation and the copy"() {
        given:
        def from = newMethodNode()
        def to = newMethodNode()
        def annotationType = ClassHelper.make("grails.gorm.transactions.NotTransactional")
        from.addAnnotation(new AnnotationNode(annotationType))
        to.addAnnotation(new AnnotationNode(annotationType))

        when:
        AstUtils.copyAnnotations(from, to)

        then: "the default is unchanged from the general-purpose contract - callers opt in to deduplication"
        to.getAnnotations(annotationType).size() == 2
    }

    private static MethodNode newMethodNode() {
        new MethodNode("test", 0, ClassHelper.VOID_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, null)
    }
}
