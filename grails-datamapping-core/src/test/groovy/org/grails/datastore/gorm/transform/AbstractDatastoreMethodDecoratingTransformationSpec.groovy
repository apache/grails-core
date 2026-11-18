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
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.control.SourceUnit

import org.springframework.beans.factory.annotation.Autowired

import spock.lang.Specification

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore

/**
 * {@code AbstractDatastoreMethodDecoratingTransformation} is only ever exercised in this module
 * through {@code TransactionalTransform} and {@code TenantTransform}, both of which are always driven
 * through a real compilation, so {@code enhanceClassNode} is never called directly and several of
 * its branches - the {@code datastore} attribute, the {@code connection} attribute, and the guard that
 * backs off when the class already declares its own {@code targetDatastore} property - are only
 * covered incidentally, if at all. This spec drives a minimal subclass through the public
 * {@code visit(ASTNode[], SourceUnit)} entry point against hand-built {@code ClassNode}s, the same
 * technique as {@code AbstractGormASTTransformationSpec}: the class-level path reaches
 * {@code enhanceClassNode}, which only touches the {@code ClassNode} it's given and never dereferences
 * the {@code SourceUnit} unless {@code compilationUnit} is set, which it isn't for a bare instance.
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

    private static AnnotationNode newAnnotationNode() {
        new AnnotationNode(ClassHelper.make(CompileStatic))
    }

    private static void apply(MinimalDatastoreDecoratingTransformation transformation, AnnotationNode annotationNode, ClassNode classNode) {
        transformation.visit([annotationNode, classNode] as ASTNode[], null)
    }

    private static Parameter[] stringConnectionNameParam() {
        [new Parameter(ClassHelper.STRING_TYPE, 'connectionName')] as Parameter[]
    }

    private static Parameter[] singleDatastoreParam(ClassNode datastoreType) {
        [new Parameter(datastoreType, 'd')] as Parameter[]
    }

    private static Parameter[] arrayDatastoreParam(ClassNode datastoreType) {
        [new Parameter(datastoreType.makeArray(), 'datastores')] as Parameter[]
    }

    void "enhanceClassNode adds a private targetDatastore field and public getter/setter methods to a plain class"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.PlainDecoratedTarget')
        AnnotationNode annotationNode = newAnnotationNode()

        when:
        apply(transformation, annotationNode, classNode)

        then: 'the datastore field is added as a private field, typed as the default Datastore'
        FieldNode field = classNode.getField('$targetDatastore')
        Modifier.isPrivate(field.modifiers)
        field.type == ClassHelper.make(Datastore)

        and: 'both getTargetDatastore overloads are added as public methods'
        Modifier.isPublic(classNode.getMethod('getTargetDatastore', Parameter.EMPTY_ARRAY).modifiers)
        Modifier.isPublic(classNode.getMethod('getTargetDatastore', stringConnectionNameParam()).modifiers)

        and: 'a public single-datastore setter is added without autowiring'
        MethodNode singleSetter = classNode.getMethod('setTargetDatastore', singleDatastoreParam(ClassHelper.make(Datastore)))
        Modifier.isPublic(singleSetter.modifiers)
        singleSetter.getAnnotations(ClassHelper.make(Autowired)).empty

        and: 'a public array setter is added, autowired but not required'
        MethodNode arraySetter = classNode.getMethod('setTargetDatastore', arrayDatastoreParam(ClassHelper.make(Datastore)))
        Modifier.isPublic(arraySetter.modifiers)
        AnnotationNode autowired = arraySetter.getAnnotations(ClassHelper.make(Autowired))[0]
        ((ConstantExpression) autowired.getMember('required')).value == false
    }

    void "enhanceClassNode types the field and accessors by the datastore attribute when one is specified"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.SpecificDatastoreTarget')
        ClassNode specificType = ClassHelper.make(MultiTenantCapableDatastore)
        AnnotationNode annotationNode = newAnnotationNode()
        annotationNode.addMember('datastore', new ClassExpression(specificType))

        when:
        apply(transformation, annotationNode, classNode)

        then:
        classNode.getField('$targetDatastore').type == specificType
        classNode.getMethod('getTargetDatastore', Parameter.EMPTY_ARRAY).returnType == specificType
        classNode.getMethod('setTargetDatastore', singleDatastoreParam(specificType)) != null
    }

    void "enhanceClassNode does not generate getTargetDatastore when the class already declares a targetDatastore property"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.OwnPropertyTarget')
        classNode.addProperty('targetDatastore', Modifier.PUBLIC, ClassHelper.make(Datastore), null, null, null)

        when:
        apply(transformation, newAnnotationNode(), classNode)

        then: 'neither getter overload is generated on top of the user-declared property'
        classNode.getMethods('getTargetDatastore').empty

        and: 'the backing field and setters are still added for the transform to assign into'
        classNode.getField('$targetDatastore') != null
        classNode.getMethod('setTargetDatastore', singleDatastoreParam(ClassHelper.make(Datastore))) != null
    }

    void "enhanceClassNode uses MultipleConnectionSourceCapableDatastore as the field type when a connection name is specified"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.ConnectionDecoratedTarget')
        AnnotationNode annotationNode = newAnnotationNode()
        annotationNode.addMember('connection', new ConstantExpression('foo'))

        when:
        apply(transformation, annotationNode, classNode)

        then:
        classNode.getField('$targetDatastore').type == ClassHelper.make(MultipleConnectionSourceCapableDatastore)
    }

    void "enhanceClassNode is idempotent once the class node has been enhanced"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode classNode = newTargetClassNode('org.grails.datastore.gorm.transform.fixture.AlreadyAppliedTarget')
        AnnotationNode annotationNode = newAnnotationNode()
        apply(transformation, annotationNode, classNode)
        FieldNode generatedField = classNode.getField('$targetDatastore')

        when: 'the generated field is removed and the transformation is applied to the same class node again'
        classNode.removeField('$targetDatastore')
        apply(transformation, annotationNode, classNode)

        then: 'the field is not regenerated, because the applied marker short-circuits enhancement before the per-member guards'
        generatedField != null
        classNode.getField('$targetDatastore') == null
        classNode.getMethods('getTargetDatastore').size() == 2
    }

    void "enhanceClassNode is a no-op for an interface class node"() {
        given:
        MinimalDatastoreDecoratingTransformation transformation = new MinimalDatastoreDecoratingTransformation()
        ClassNode interfaceNode = new ClassNode(
                'org.grails.datastore.gorm.transform.fixture.NoDecorationInterfaceTarget',
                Modifier.PUBLIC | Modifier.INTERFACE, ClassHelper.OBJECT_TYPE)
        AnnotationNode annotationNode = newAnnotationNode()

        when:
        apply(transformation, annotationNode, interfaceNode)

        then:
        interfaceNode.getField('$targetDatastore') == null
        interfaceNode.methods.empty
    }
}
