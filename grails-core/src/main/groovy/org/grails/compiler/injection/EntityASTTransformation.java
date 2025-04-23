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

import grails.compiler.ast.ClassInjector;
import grails.compiler.ast.GrailsDomainClassInjector;
import grails.persistence.Entity;
import groovy.transform.CompilationUnitAware;

import java.util.List;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.grails.core.artefact.DomainClassArtefactHandler;


@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class EntityASTTransformation implements ASTTransformation, CompilationUnitAware {

    private static final ClassNode MY_TYPE = new ClassNode(Entity.class);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    protected CompilationUnit compilationUnit;

    public void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {

        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: $node.class / $parent.class");
        }

        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];
        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode cNode = (ClassNode) parent;
        String cName = cNode.getName();
        if (cNode.isInterface()) {
            throw new RuntimeException("Error processing interface '" + cName + "'. " +
                    MY_TYPE_NAME + " not allowed for interfaces.");
        }

        applyTransformation(sourceUnit, cNode);

    }

    public void applyTransformation(SourceUnit sourceUnit, ClassNode classNode) {
        if(GrailsASTUtils.isApplied(classNode, EntityASTTransformation.class)) {
            return;
        }
        GrailsASTUtils.markApplied(classNode, EntityASTTransformation.class);
        
        GrailsDomainClassInjector domainInjector = new DefaultGrailsDomainClassInjector();
        domainInjector.performInjectionOnAnnotatedEntity(classNode);

        ClassInjector[] classInjectors = GrailsAwareInjectionOperation.getClassInjectors();

        final List<ClassInjector> domainInjectors = ArtefactTypeAstTransformation.findInjectors(DomainClassArtefactHandler.TYPE, classInjectors);

        for (ClassInjector injector : domainInjectors) {
            try {
                injector.performInjection(sourceUnit, classNode);
            } catch (RuntimeException e) {
                try {
                    System.err.println("Error occurred calling AST injector ["+injector.getClass().getName()+"]: " + e.getMessage());
                } catch (Throwable t) {
                    // ignore
                }
                throw e;
            }
        }
        
        if(compilationUnit != null) {
            TraitInjectionUtils.processTraitsForNode(sourceUnit, classNode, DomainClassArtefactHandler.TYPE, compilationUnit);
        }
    }

    @Override
    public void setCompilationUnit(CompilationUnit unit) {
        compilationUnit = unit;
    }
}
