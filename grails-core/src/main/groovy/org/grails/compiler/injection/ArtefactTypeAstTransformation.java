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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import groovy.transform.CompilationUnitAware;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import grails.artefact.Artefact;
import grails.build.logging.GrailsConsole;
import grails.compiler.ast.AllArtefactClassInjector;
import grails.compiler.ast.ClassInjector;
import grails.compiler.ast.GlobalClassInjector;
import grails.compiler.ast.GrailsArtefactClassInjector;
import org.apache.grails.common.compiler.GroovyTransformOrder;

/**
 * A transformation used to apply transformers to classes not located in Grails
 * directory structure. For example any class can be annotated with
 * &#064;Artefact("Controller") to make it into a controller no matter what the location.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ArtefactTypeAstTransformation extends AbstractArtefactTypeAstTransformation implements CompilationUnitAware {
    private static final ClassNode MY_TYPE = new ClassNode(Artefact.class);

    protected CompilationUnit compilationUnit;

    public void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        AnnotatedNode parent = (AnnotatedNode) astNodes[1];
        AnnotationNode node = (AnnotationNode) astNodes[0];

        if (!(node instanceof AnnotationNode) || !(parent instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: $node.class / $parent.class");
        }

        if (!isArtefactAnnotationNode(node) || !(parent instanceof ClassNode)) {
            return;
        }

        ClassNode cNode = (ClassNode) parent;
        if (cNode.isInterface()) {
            throw new RuntimeException("Error processing interface '" + cNode.getName() + "'. @" +
                    getAnnotationType().getNameWithoutPackage() + " not allowed for interfaces.");
        }

        if(isApplied(cNode)) {
            return;
        }

        String artefactType = resolveArtefactType(sourceUnit, node, cNode);
        if(artefactType != null) {
            AbstractGrailsArtefactTransformer.addToTransformedClasses(cNode.getName());
        }
        performInjectionOnArtefactType(sourceUnit, cNode, artefactType);

        performTraitInjectionOnArtefactType(sourceUnit, cNode, artefactType);

        postProcess(sourceUnit, node, cNode, artefactType);

        markApplied(cNode);
    }

    protected void performTraitInjectionOnArtefactType(SourceUnit sourceUnit,
            ClassNode cNode, String artefactType) {
        if (compilationUnit != null) {
            TraitInjectionUtils.processTraitsForNode(sourceUnit, cNode, artefactType, compilationUnit);
        }
    }

    protected boolean isApplied(ClassNode cNode) {
        return GrailsASTUtils.isApplied(cNode, getAstAppliedMarkerClass());
    }

    protected void markApplied(ClassNode classNode) {
        GrailsASTUtils.markApplied(classNode, getAstAppliedMarkerClass());
    }

    protected Class<?> getAstAppliedMarkerClass() {
        return ArtefactTypeAstTransformation.class;
    }

    protected void postProcess(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode, String artefactType) {
        if(!getAnnotationType().equals(annotationNode.getClassNode())) {
            // add @Artefact annotation to resulting class so that "short cut" annotations like @TagLib
            // also produce an @Artefact annotation in the resulting class file
            AnnotationNode annotation=new AnnotationNode(getAnnotationType());
            annotation.addMember("value", new ConstantExpression(artefactType));
            classNode.addAnnotation(annotation);
        }
    }

    protected String resolveArtefactType(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        Expression value = annotationNode.getMember("value");

        if (value != null) {
            if (value instanceof ConstantExpression) {
                ConstantExpression ce = (ConstantExpression) value;
                return ce.getText();
            }
            if (value instanceof PropertyExpression) {
                PropertyExpression pe = (PropertyExpression) value;

                Expression objectExpression = pe.getObjectExpression();
                if (objectExpression instanceof ClassExpression) {
                    ClassExpression ce = (ClassExpression) objectExpression;
                    try {
                        Field field = ce.getType().getTypeClass().getDeclaredField(pe.getPropertyAsString());
                        return (String)field.get(null);
                    } catch (Exception e) {}
                }
            }
        }

        throw new RuntimeException("Class ["+classNode.getName()+"] contains an invalid @Artefact annotation. No artefact found for value specified.");
    }

    protected boolean isArtefactAnnotationNode(AnnotationNode annotationNode) {
        return getAnnotationType().equals(annotationNode.getClassNode());
    }

    protected ClassNode getAnnotationType() {
        return new ClassNode(getAnnotationTypeClass());
    }

    protected Class getAnnotationTypeClass() {
        return MY_TYPE.getTypeClass();
    }

    public void performInjectionOnArtefactType(SourceUnit sourceUnit, ClassNode cNode, String artefactType) {
        List<ClassInjector> injectors = findInjectors(artefactType, GrailsAwareInjectionOperation.getClassInjectors());
        for (ClassInjector injector : injectors) {
            if(injector instanceof CompilationUnitAware) {
                ((CompilationUnitAware) injector).setCompilationUnit(this.compilationUnit);
            }
        }
        performInjection(sourceUnit, cNode, injectors);
    }

    @Deprecated
    public static void doPerformInjectionOnArtefactType(SourceUnit sourceUnit, ClassNode cNode, String artefactType) {
        List<ClassInjector> injectors = findInjectors(artefactType, GrailsAwareInjectionOperation.getClassInjectors());
        performInjection(sourceUnit, cNode, injectors);
    }

    public static void performInjection(SourceUnit sourceUnit, ClassNode cNode, Collection<ClassInjector> injectors) {
        try {
            for (ClassInjector injector : injectors) {
                if(!GrailsASTUtils.isApplied(cNode, injector.getClass())) {
                    GrailsASTUtils.markApplied(cNode, injector.getClass());
                    injector.performInjectionOnAnnotatedClass(sourceUnit, cNode);
                }
            }
        } catch (RuntimeException e) {
            try {
                GrailsConsole.getInstance().error("Error occurred calling AST injector: " + e.getMessage(), e);
            } catch (Throwable t) {
                // ignore it
            }
            throw e;
        }
    }

    public static List<ClassInjector> findInjectors(String artefactType, ClassInjector[] classInjectors) {
        List<ClassInjector> injectors = new ArrayList<ClassInjector>();
        for (ClassInjector classInjector : classInjectors) {
            if (classInjector instanceof AllArtefactClassInjector) {
                injectors.add(classInjector);
            }
            else if(classInjector instanceof GlobalClassInjector) {
                injectors.add(classInjector);
            }
            else if (classInjector instanceof GrailsArtefactClassInjector) {
                GrailsArtefactClassInjector gace = (GrailsArtefactClassInjector) classInjector;

                if (hasArtefactType(artefactType,gace)) {
                    injectors.add(gace);
                }
            }
        }
        return injectors;
    }

    public static boolean hasArtefactType(String artefactType, GrailsArtefactClassInjector gace) {
        for (String _artefactType : gace.getArtefactTypes()) {
            if(_artefactType.equals("*")) return true;
            if (_artefactType.equals(artefactType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setCompilationUnit(CompilationUnit unit) {
        compilationUnit = unit;
    }

    @Override
    public int priority() {
        return GroovyTransformOrder.ARTIFACT_TYPE_ORDER;
    }
}
