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

import grails.compiler.GrailsCompileStatic
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class GrailsASTUtilsSpec extends Specification {

    @Issue('apache/grails-core#10079')
    void 'test domain class detection when the current source unit is associated with a controller'() {
        setup:
        File tmpDir = new File(System.getProperty('java.io.tmpdir'))

        File projectDir = new File(tmpDir, "projectDir")

        // create /projectDir/grails-app/domain/ under java.io.tmpdir
        File grailsAppDir = new File(projectDir, 'grails-app')
        File domainDir = new File(grailsAppDir, 'domain')

        String packagePath = Something.package.name.replace('.' as char, File.separatorChar)

        // create the source file that would contain the source for the
        // relevant domain class...
        File domainPackageDir = new File(domainDir, packagePath)
        domainPackageDir.mkdirs()
        File domainClassFile = new File(domainPackageDir, 'Something.groovy')
        domainClassFile.createNewFile()

        // the controller source file doesn't really need to exist but we need a
        // fully qualified path to where it would be...
        File controllersDir = new File(grailsAppDir, 'controllers')
        File controllerPackageDir = new File(controllersDir, packagePath)
        File controllerClassFile = new File(controllerPackageDir,
                                            'SomethingController.groovy')

        SourceUnit controllerSourceUnit = Mock()
        controllerSourceUnit.getName() >> controllerClassFile.absolutePath

        expect: 'Something should be recognized as a domain because grails-app/domain/org/grails/compiler/injection/Something.groovy exists'
        GrailsASTUtils.isDomainClass(new ClassNode(Something), controllerSourceUnit)

        and: 'SomethingElse should NOT be recognized as a domain because grails-app/domain/org/grails/compiler/injection/SomethingElse.groovy does NOT exist'
        !GrailsASTUtils.isDomainClass(new ClassNode(SomethingElse), controllerSourceUnit)
    }

    @Unroll
    void 'hasStaticCompilationAnnotation is #expected for #description'() {
        expect:
        GrailsASTUtils.hasStaticCompilationAnnotation(annotated(annotation)) == expected

        where:
        annotation          | description            || expected
        CompileStatic       | '@CompileStatic'       || true
        CompileDynamic      | '@CompileDynamic'      || true
        TypeChecked         | '@TypeChecked'         || true
        GrailsCompileStatic | '@GrailsCompileStatic' || true
        null                | 'a plain class'        || false
    }

    void 'addGrailsCompileStaticAnnotation applies @CompileStatic with the Grails type checking extensions'() {
        given:
        ClassNode classNode = new ClassNode('com.example.FooController', 0, ClassHelper.OBJECT_TYPE)

        when:
        boolean applied = GrailsASTUtils.addGrailsCompileStaticAnnotation(classNode)

        then:
        applied
        GrailsASTUtils.hasStaticCompilationAnnotation(classNode)

        and: 'the @CompileStatic annotation carries the Grails type checking extensions'
        AnnotationNode an = classNode.getAnnotations(ClassHelper.make(CompileStatic)).first()
        ListExpression extensions = an.getMember('extensions') as ListExpression
        extensions.expressions*.value == GrailsASTUtils.GRAILS_COMPILE_STATIC_EXTENSIONS
    }

    void 'addGrailsCompileStaticAnnotation is a no-op when an explicit compilation choice is already present'() {
        given:
        ClassNode classNode = annotated(CompileDynamic)

        expect:
        !GrailsASTUtils.addGrailsCompileStaticAnnotation(classNode)
    }

    void 'the Grails compile static extensions match those declared by @GrailsCompileStatic'() {
        expect:
        GrailsASTUtils.GRAILS_COMPILE_STATIC_EXTENSIONS.size() == 9
        'org.grails.compiler.TagLibraryInvokerTypeCheckingExtension' in GrailsASTUtils.GRAILS_COMPILE_STATIC_EXTENSIONS
    }

    private static ClassNode annotated(Class annotation) {
        ClassNode classNode = new ClassNode('com.example.Foo', 0, ClassHelper.OBJECT_TYPE)
        if (annotation != null) {
            classNode.addAnnotation(new AnnotationNode(ClassHelper.make(annotation)))
        }
        classNode
    }
}

class Something {}
class SomethingElse {}
