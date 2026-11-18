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

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Local, annotation-driven transformation used only to test {@link AbstractTraitApplyingGormASTTransformation}
 * through a genuine {@code CompilationUnit}. It deliberately does not override {@code shouldWeave},
 * so the default implementation runs, and it weaves {@link TestWeavableTrait} - a trait with no
 * generic type parameters - onto whatever class is annotated with {@link ApplyTestTraitWeaving}.
 *
 * @see AbstractTraitApplyingGormASTTransformationSpec
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class TestTraitWeavingTransformation extends AbstractTraitApplyingGormASTTransformation implements ASTTransformation {

    private static final ClassNode MY_TYPE = ClassHelper.make(ApplyTestTraitWeaving)
    private static final Object APPLIED_MARKER = new Object()

    @Override
    protected Class getTraitClass() {
        TestWeavableTrait
    }

    @Override
    protected ClassNode getAnnotationType() {
        MY_TYPE
    }

    @Override
    protected Object getAppliedMarker() {
        APPLIED_MARKER
    }

    @Override
    int priority() {
        0
    }
}
