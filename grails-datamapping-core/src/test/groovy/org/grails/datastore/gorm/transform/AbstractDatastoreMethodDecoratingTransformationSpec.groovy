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
package org.grails.datastore.gorm.transform

import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.control.SourceUnit

import org.springframework.beans.factory.annotation.Autowired

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.services.Service

/**
 * {@code AbstractDatastoreMethodDecoratingTransformation} is only ever exercised in this module
 * through {@code TransactionalTransform} and {@code TenantTransform}, both of which are always driven
 * through a real compilation, so {@code enhanceClassNode} is never called directly and its Service-
 * interface branch (only reachable for a class implementing {@code org.grails.datastore.mapping.services.Service},
 * which none of the real transform specs' fixtures do) is never exercised at all. Because
 * {@code enhanceClassNode} only touches the {@code ClassNode} it's given - it never dereferences the
 * {@code SourceUnit} parameter unless {@code compilationUnit} is set, which it isn't for a bare
 * instance - it can be called directly against hand-built {@code ClassNode}s, the same technique used
 * for the other abstract transformation specs in this package.
 */
class AbstractDatastoreMethodDecoratingTransformationSpec extends Specification {

    static class MinimalDatastoreDecoratingTransformation extends AbstractDatastoreMethodDecoratingTransformation {

        @Override
        protected ClassNode getAnnotationType() {
            ClassHelper.make(CompileStatic)
        }

        @Override
        protected Object getAppliedMarker() {
            'datastore-decorating-applied-marker'
        }

        @Override
        protected String getRenamedMethodPrefix() {
            '$test__'
        }

        @Override
        protected Expression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode,
                                                         MethodNode methodNode, MethodCallExpression originalMethodCall, BlockStatement newMethodBody) {
            originalMethodCall
        }

        @Override
        int priority() {
            0
        }
    }

    private static ClassNode newTargetClassNode(String name) {
        new ClassNode(name, Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
    }

    private static Parameter[] stringConnectionNameParam() {
        [new Parameter(ClassHelper.STRING_TYPE, 'connectionName')] as Parameter[]
    }

    void "enhanceClassNode adds a targetDatastore field and public getter/setter methods to a plain class"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.PlainDecoratedTarget')
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(CompileStatic))

        when:
        transformation.enhanceClassNode(null, annotationNode, classNode)

        then: 'the datastore field is added, typed as the default Datastore'
        classNode.getField('$targetDatastore').type == ClassHelper.make(Datastore)

        and: 'both getTargetDatastore overloads are added as public methods'
        classNode.getMethod('getTargetDatastore', Parameter.EMPTY_ARRAY) != null
        classNode.getMethod('getTargetDatastore', stringConnectionNameParam()) != null

        and: 'a public setter is added, autowired but not required'
        MethodNode setter = classNode.getMethods('setTargetDatastore')[0]
        Modifier.isPublic(setter.modifiers)
        AnnotationNode autowired = setter.getAnnotations(ClassHelper.make(Autowired))[0]
        ((ConstantExpression) autowired.getMember('required')).value == false
    }

    void "enhanceClassNode adds only protected getTargetDatastore methods and no field when the class implements Service"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.ServiceDecoratedTarget')
        classNode.addInterface(ClassHelper.make(Service))
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(CompileStatic))

        when:
        transformation.enhanceClassNode(null, annotationNode, classNode)

        then: 'no field is added - the Service is looked up rather than injected'
        classNode.getField('$targetDatastore') == null

        and: 'both getTargetDatastore overloads are added, but protected rather than public'
        MethodNode noArgGetter = classNode.getMethod('getTargetDatastore', Parameter.EMPTY_ARRAY)
        MethodNode connectionGetter = classNode.getMethod('getTargetDatastore', stringConnectionNameParam())
        Modifier.isProtected(noArgGetter.modifiers)
        Modifier.isProtected(connectionGetter.modifiers)

        and: 'no setter is added at all'
        classNode.getMethods('setTargetDatastore').empty
    }

    void "enhanceClassNode uses MultipleConnectionSourceCapableDatastore as the field type when a connection name is specified"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.ConnectionDecoratedTarget')
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(CompileStatic))
        annotationNode.addMember('connection', new ConstantExpression('foo'))

        when:
        transformation.enhanceClassNode(null, annotationNode, classNode)

        then:
        classNode.getField('$targetDatastore').type == ClassHelper.make(MultipleConnectionSourceCapableDatastore)
    }

    void "enhanceClassNode is idempotent once the applied marker is already set on the class node"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.AlreadyAppliedTarget')
        classNode.putNodeMetaData(transformation.getAppliedMarker(), transformation.getAppliedMarker())
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(CompileStatic))

        when:
        transformation.enhanceClassNode(null, annotationNode, classNode)

        then: 'nothing is added because the marker short-circuits enhancement'
        classNode.getField('$targetDatastore') == null
        classNode.methods.every { it.name != 'getTargetDatastore' }
    }

    void "enhanceClassNode is a no-op for an interface class node"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode interfaceNode = new ClassNode(
                'org.grails.datastore.gorm.transform.fixture.NoDecorationInterfaceTarget',
                Modifier.PUBLIC | Modifier.INTERFACE, ClassHelper.OBJECT_TYPE)
        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(CompileStatic))

        when:
        transformation.enhanceClassNode(null, annotationNode, interfaceNode)

        then:
        interfaceNode.getField('$targetDatastore') == null
        interfaceNode.methods.empty
    }
}
