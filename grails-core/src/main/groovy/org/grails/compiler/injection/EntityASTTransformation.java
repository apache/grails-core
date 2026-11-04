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

import groovy.transform.CompilationUnitAware;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.TransformWithPriority;

import grails.compiler.ast.ClassInjector;
import grails.compiler.ast.GrailsDomainClassInjector;
import grails.persistence.Entity;
import org.apache.grails.common.compiler.GroovyTransformOrder;
import org.grails.core.artefact.DomainClassArtefactHandler;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class EntityASTTransformation implements ASTTransformation, CompilationUnitAware, TransformWithPriority {

    private static final ClassNode MY_TYPE = new ClassNode(Entity.class);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    protected CompilationUnit compilationUnit;

    public void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {

        if (!(astNodes[0] instanceof AnnotationNode node) || !(astNodes[1] instanceof AnnotatedNode parent)) {
            throw new RuntimeException("Internal error: wrong types: $node.class / $parent.class");
        }

        if (!MY_TYPE.equals(node.getClassNode()) || !(parent instanceof ClassNode cNode)) {
            return;
        }

        String cName = cNode.getName();
        if (cNode.isInterface()) {
            throw new RuntimeException("Error processing interface '" + cName + "'. " +
                    MY_TYPE_NAME + " not allowed for interfaces.");
        }

        applyTransformation(sourceUnit, cNode);

    }

    public void applyTransformation(SourceUnit sourceUnit, ClassNode classNode) {
        if (GrailsASTUtils.isApplied(classNode, EntityASTTransformation.class)) {
            return;
        }
        GrailsASTUtils.markApplied(classNode, EntityASTTransformation.class);

        ClassInjector[] classInjectors = GrailsAwareInjectionOperation.getClassInjectors();

        final List<ClassInjector> domainInjectors = ArtefactTypeAstTransformation.findInjectors(DomainClassArtefactHandler.TYPE, classInjectors);

        // The discovered injectors run first so that GORM's own transformation, where it is on the
        // classpath, decides the type of the injected id: it knows which GORM implementation the
        // entity is mapped with, and this one does not. DefaultGrailsDomainClassInjector then fills in
        // whatever is still missing - which is everything, for an entity GORM did not process, and
        // nothing for one it did, since each of its injections is guarded on the property already
        // being there. The same order holds for entities under grails-app/domain, where
        // GormTransformer explicitly has the highest injector precedence.
        for (ClassInjector injector : domainInjectors) {
            try {
                injector.performInjection(sourceUnit, classNode);
            } catch (RuntimeException e) {
                try {
                    System.err.println("Error occurred calling AST injector [" + injector.getClass().getName() + "]: " + e.getMessage());
                } catch (Throwable t) {
                    // ignore
                }
                throw e;
            }
        }

        GrailsDomainClassInjector domainInjector = new DefaultGrailsDomainClassInjector();
        domainInjector.performInjectionOnAnnotatedEntity(classNode);

        if (compilationUnit != null) {
            TraitInjectionUtils.processTraitsForNode(sourceUnit, classNode, DomainClassArtefactHandler.TYPE, compilationUnit);
        }
    }

    @Override
    public void setCompilationUnit(CompilationUnit unit) {
        compilationUnit = unit;
    }

    @Override
    public int priority() {
        return GroovyTransformOrder.ENTITY_ORDER;
    }
}
