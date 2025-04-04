/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.transform

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.datastore.mapping.core.order.OrderedComparator
import org.grails.datastore.mapping.reflect.ClassUtils

import static org.grails.datastore.mapping.reflect.AstUtils.findAnnotation

/**
 * Central AST transformation that ensures that GORM AST Transformations are executed in the correct order.
 * Each GORM transform can implement the {@link org.grails.datastore.mapping.core.Ordered} interface to achieve property placement.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class OrderedGormTransformation extends AbstractASTTransformation implements CompilationUnitAware {

    CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${astNodes[0].getClass()} / ${astNodes[1].getClass()}");
        }

        AnnotatedNode annotatedNode = (AnnotatedNode) astNodes[1];
        Iterable<TransformationInvocation> astTransformations = collectAndOrderGormTransformations(annotatedNode)
        for(transform in astTransformations) {
            transform.invoke(source, annotatedNode)
        }
    }

    Iterable<TransformationInvocation> collectAndOrderGormTransformations(AnnotatedNode annotatedNode) {
        List<AnnotationNode> annotations = new ArrayList<>(annotatedNode.getAnnotations())
        if(annotatedNode instanceof MethodNode) {
            MethodNode mn = (MethodNode)annotatedNode
            for(classAnn in mn.getDeclaringClass().getAnnotations()) {
                if(!annotations.any() { AnnotationNode ann ->
                    ann.classNode.name == classAnn.classNode.name ||
                        findTransformName(ann) == findTransformName(classAnn)
                }) {
                    annotations.add(classAnn)
                }
            }
        }
        List<TransformationInvocation> transforms = []
        for(ann in annotations) {
            String transformName = findTransformName(ann)
            if(transformName) {
                try {
                    def newTransform = ClassUtils.forName(transformName).newInstance()
                    if(newTransform instanceof ASTTransformation) {
                        if(newTransform instanceof CompilationUnitAware) {
                            ((CompilationUnitAware) newTransform).setCompilationUnit( compilationUnit )
                        }
                        transforms.add( new TransformationInvocation(ann, newTransform) )
                    }
                } catch (Throwable e) {
                    addError("Could not load GORM transform for name [$transformName]: $e.message", annotatedNode)
                }
            }
        }
        return transforms.sort { TransformationInvocation a, TransformationInvocation b ->
            new OrderedComparator<>().compare(a.transform, b.transform)
        }.reverse()
    }

    protected String findTransformName(AnnotationNode ann) {
        AnnotationNode gormTransform = findAnnotation(ann.classNode, GormASTTransformationClass)
        String transformName = gormTransform?.getMember("value")?.text
        transformName
    }

    private static class TransformationInvocation {
        private final AnnotationNode annotation
        private final ASTTransformation transform

        TransformationInvocation(AnnotationNode annotation, ASTTransformation transform) {
            this.annotation = annotation
            this.transform = transform
        }

        void invoke(SourceUnit sourceUnit, AnnotatedNode annotatedNode) {
            transform.visit( [annotation, annotatedNode] as ASTNode[], sourceUnit)
        }
    }
}
