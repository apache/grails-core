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
package org.grails.compiler.injection

import grails.artefact.Enhanced
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.grails.compiler.injection.GrailsASTUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Burt Beckwith
 */
class GrailsASTUtilsTests {

    @BeforeEach
    protected void setUp() throws Exception {
        System.setProperty("grails.version", "3.0.0")
    }

    @AfterEach
    protected void tearDown() throws Exception {
        System.setProperty("grails.version", "")
    }

    @Test
    void testGetFurthestParent() {
        def fooNode = new ClassNode(Foo)
        def barNode = new ClassNode(Bar)
        def bazNode = new ClassNode(Baz)

        assertEquals Foo.name, GrailsASTUtils.getFurthestParent(fooNode).name
        assertEquals Foo.name, GrailsASTUtils.getFurthestParent(barNode).name
        assertEquals Foo.name, GrailsASTUtils.getFurthestParent(bazNode).name
    }

    @Test
    void testHasAnyAnnotations() {
        def widgetNode = new ClassNode(Widget)
        assert GrailsASTUtils.hasAnyAnnotations(widgetNode, FirstAnnotation, SecondAnnotation)
        assert GrailsASTUtils.hasAnyAnnotations(widgetNode, FirstAnnotation, SecondAnnotation, ThirdAnnotation)
        assert GrailsASTUtils.hasAnyAnnotations(widgetNode, FirstAnnotation, SecondAnnotation, ThirdAnnotation, FourthAnnotation)
        assert GrailsASTUtils.hasAnyAnnotations(widgetNode, SecondAnnotation, ThirdAnnotation, FourthAnnotation)
        assert !GrailsASTUtils.hasAnyAnnotations(widgetNode, ThirdAnnotation, FourthAnnotation)
        assert !GrailsASTUtils.hasAnyAnnotations(widgetNode, FourthAnnotation)
        assert !GrailsASTUtils.hasAnyAnnotations(widgetNode)
    }

    @Test
    void testHasAnnotation() {
        def widgetNode = new ClassNode(Widget)
        assert GrailsASTUtils.hasAnnotation(widgetNode, FirstAnnotation)
        assert GrailsASTUtils.hasAnnotation(widgetNode, SecondAnnotation)
        assert !GrailsASTUtils.hasAnnotation(widgetNode, ThirdAnnotation)
        assert !GrailsASTUtils.hasAnnotation(widgetNode, FourthAnnotation)
    }

    @Test
    void testConstraintMetadata() {
        def result = new AstBuilder().buildFromString('''
            return {
                firstName bindable: true, size: 5..15
                def sb = new StringBuffer()

                // this should NOT be considered a constraint configuration method call because
                // it isn't a method invoked on "this"
                sb.append nullable: true, size: 115
                lastName bindable: false
            }
        ''')
        def closureExpression = result[0].statements[0].expression

        def constraintMetadata = GrailsASTUtils.getConstraintMetadata(closureExpression)
        assert 2 == constraintMetadata.size()

        def firstNameMetadata = constraintMetadata['firstName']
        assert 2 == firstNameMetadata.size()
        def firstNameBindableExpression = firstNameMetadata['bindable']
        assert true == firstNameBindableExpression.value
        def firstNameSizeExpression = firstNameMetadata['size']
        assert 5 == firstNameSizeExpression.from.value
        assert 15 == firstNameSizeExpression.to.value

        def lastNameMetaData = constraintMetadata['lastName']
        assert 1 == lastNameMetaData.size()
        def lastNameBindableExpression = lastNameMetaData['bindable']
        assert false == lastNameBindableExpression.value
    }

    @Test
    void testAddEnhanced() {
        def result = new AstBuilder().buildFromString('''
            class SomeArtefact {}
        ''')
        
        def classNode = result[1]
        
        assert !GrailsASTUtils.hasAnnotation(classNode, Enhanced)
        
        GrailsASTUtils.addEnhancedAnnotation(classNode, 'someFeature')

        def enhancedAnnotation = classNode.getAnnotations(new ClassNode(Enhanced))[0]
        assert enhancedAnnotation.getMember('enhancedFor').expressions.size() == 1
        assert enhancedAnnotation.getMember('enhancedFor').expressions[0].value == 'someFeature'
    }

    @Test
    void testAddEnhancedWithMultipleFeatures() {
        def result = new AstBuilder().buildFromString('''
            class SomeOtherArtefact {}
        ''')
        
        def classNode = result[1]
        
        assert !GrailsASTUtils.hasAnnotation(classNode, Enhanced)
        
        GrailsASTUtils.addEnhancedAnnotation(classNode, 'someFeature', 'someOtherFeature')

        def enhancedAnnotation = classNode.getAnnotations(new ClassNode(Enhanced))[0]
        def featureNames = enhancedAnnotation.getMember('enhancedFor').expressions*.value
        assert featureNames.size() == 2
        assert 'someFeature' in featureNames
        assert 'someOtherFeature' in featureNames
    }

    @Test
    void testAddEnhancedToClassWhichAlreadyHasBeenEnhanced() {
        def result = new AstBuilder().buildFromString('''
            class YetAnotherArtefact {}
        ''')
        
        def classNode = result[1]
        
        assert !GrailsASTUtils.hasAnnotation(classNode, Enhanced)
        
        GrailsASTUtils.addEnhancedAnnotation(classNode, 'someFeature', 'someOtherFeature')

        def enhancedAnnotation = classNode.getAnnotations(new ClassNode(Enhanced))[0]
        def featureNames = enhancedAnnotation.getMember('enhancedFor').expressions*.value
        assert featureNames.size() == 2
        assert 'someFeature' in featureNames
        assert 'someOtherFeature' in featureNames
        
        GrailsASTUtils.addEnhancedAnnotation(classNode, 'aThirdFeature')
        enhancedAnnotation = classNode.getAnnotations(new ClassNode(Enhanced))[0]
        featureNames = enhancedAnnotation.getMember('enhancedFor').expressions*.value
        assert featureNames.size() == 3
        assert 'someFeature' in featureNames
        assert 'someOtherFeature' in featureNames
        assert 'aThirdFeature' in featureNames
        
    }
    
    @Test
    void testAddEnhancedWithFeatureThatIsAlreadyPresent() {
        def result = new AstBuilder().buildFromString('''
            class SomeOtherArtefact {}
        ''')
        
        def classNode = result[1]
        
        assert !GrailsASTUtils.hasAnnotation(classNode, Enhanced)
        
        GrailsASTUtils.addEnhancedAnnotation(classNode, 'someFeature', 'someOtherFeature')

        def enhancedAnnotation = classNode.getAnnotations(new ClassNode(Enhanced))[0]
        def featureNames = enhancedAnnotation.getMember('enhancedFor').expressions*.value
        assert 'someFeature' in featureNames
        assert 'someOtherFeature' in featureNames
        
        GrailsASTUtils.addEnhancedAnnotation(classNode, 'someFeature', 'aThirdFeature')

        enhancedAnnotation = classNode.getAnnotations(new ClassNode(Enhanced))[0]
        featureNames = enhancedAnnotation.getMember('enhancedFor').expressions*.value
        assert featureNames.size() == 3
        assert 'someFeature' in featureNames
        assert 'someOtherFeature' in featureNames
        assert 'aThirdFeature' in featureNames
    }
}

class Foo {}

class Bar extends Foo {}

class Baz extends Bar {}

@Retention(RetentionPolicy.RUNTIME)
@interface FirstAnnotation{}
@Retention(RetentionPolicy.RUNTIME)
@interface SecondAnnotation{}
@Retention(RetentionPolicy.RUNTIME)
@interface ThirdAnnotation{}
@Retention(RetentionPolicy.RUNTIME)
@interface FourthAnnotation{}

@FirstAnnotation
@SecondAnnotation
class Widget {}
