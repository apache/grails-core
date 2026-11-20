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
package grails.gsp.taglib.compiler

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification

class TagLibArtefactTypeAstTransformationSpec extends Specification {

    def cleanup() {
        System.clearProperty(TagLibArtefactTypeAstTransformation.WARN_DEPRECATED_CLOSURES_PROPERTY)
    }

    void "deprecation warning is emitted by default for closure-based tag fields"() {
        when:
        String stderr = runWarningCheckAndCaptureStderr()

        then:
        stderr.contains('deprecated')
        stderr.contains('greeting')
    }

    void "deprecation warning is suppressed when grails.taglib.warnDeprecatedClosures is false"() {
        given:
        System.setProperty(TagLibArtefactTypeAstTransformation.WARN_DEPRECATED_CLOSURES_PROPERTY, 'false')

        when:
        String stderr = runWarningCheckAndCaptureStderr()

        then:
        !stderr.contains('deprecated')
    }

    private static String runWarningCheckAndCaptureStderr() {
        ClassNode owner = new ClassNode('FooTagLib', 0, ClassHelper.OBJECT_TYPE)
        FieldNode field = new FieldNode('greeting', 0, new ClassNode(Closure), owner, null)
        field.lineNumber = 1
        field.columnNumber = 1
        owner.addField(field)
        SourceUnit sourceUnit = SourceUnit.create('FooTagLib.groovy', 'class FooTagLib { Closure greeting = { -> } }')

        PrintStream originalErr = System.err
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        System.setErr(new PrintStream(captured))
        try {
            new TagLibArtefactTypeAstTransformation().addClosureTagDeprecationWarnings(sourceUnit, owner)
        } finally {
            System.setErr(originalErr)
        }
        return captured.toString()
    }
}
