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
package org.grails.compiler.injection;

import java.util.List;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.TransformWithPriority;

import grails.compiler.ast.AllArtefactClassInjector;
import grails.compiler.ast.AnnotatedClassInjector;
import grails.compiler.ast.ClassInjector;

/**
 * Base implementation for the artefact type transformation.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public abstract class AbstractArtefactTypeAstTransformation implements ASTTransformation, TransformWithPriority {
    protected void performInjectionOnArtefactType(SourceUnit sourceUnit, ClassNode cNode, String artefactType) {
        try {
            ClassInjector[] classInjectors = GrailsAwareInjectionOperation.getClassInjectors();
            List<ClassInjector> injectors = ArtefactTypeAstTransformation.findInjectors(artefactType, classInjectors);
            if (!injectors.isEmpty()) {
                AbstractGrailsArtefactTransformer.addToTransformedClasses(cNode.getName());
                for (ClassInjector injector : injectors) {
                    if(injector instanceof AllArtefactClassInjector) {
                        injector.performInjection(sourceUnit,cNode);
                    }
                    else if(injector instanceof AnnotatedClassInjector) {
                        ((AnnotatedClassInjector)injector).performInjectionOnAnnotatedClass(sourceUnit,null, cNode);
                    }
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Error occurred calling AST injector ["+getClass()+"]: " + e.getMessage());
            throw e;
        }
    }
}
