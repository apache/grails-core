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

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.trait.TraitComposer
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * An abstract transformation that applies a Trait
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractTraitApplyingGormASTTransformation extends AbstractGormASTTransformation {

    @Override
    void visit(SourceUnit source, AnnotationNode annotationNode, AnnotatedNode annotatedNode) {
        this.sourceUnit = source
        if(annotatedNode instanceof ClassNode) {
            visit(source, annotationNode, (ClassNode)annotatedNode)
        }
    }

    void visit(SourceUnit source, AnnotationNode annotationNode, ClassNode classNode) {
        this.sourceUnit = source
        Class traitJavaClass = getTraitClass()
        if(shouldWeave(annotationNode, classNode)) {
            weaveTrait(classNode, source, traitJavaClass)
        }
        visitAfterTraitApplied(source, annotationNode, classNode)
    }

    boolean shouldWeave(AnnotationNode annotationNode, ClassNode classNode) {
        return true
    }

    /**
     * Weave the given trait into the given ClassNode
     *
     * @param classNode The class node
     * @param source The source unit
     * @param traitJavaClass The trait java class
     */
    protected void weaveTrait(ClassNode classNode, SourceUnit source, Class traitJavaClass, ClassNode... genericArguments) {
        weaveTraitWithGenerics(classNode, traitJavaClass, genericArguments)
        if (compilationUnit != null) {
            TraitComposer.doExtendTraits(classNode, source, compilationUnit);
        }
    }

    static void weaveTraitWithGenerics(ClassNode classNode, Class traitJavaClass, ClassNode... genericArguments) {
        if (classNode.isInterface()) return

        ClassNode traitClassNode = ClassHelper.make(traitJavaClass)
        boolean shouldWeave = !classNode.implementsInterface(traitClassNode)

        if (shouldWeave) {
            GenericsType[] genericsTypes = traitClassNode.genericsTypes
            if (genericsTypes != null && genericsTypes.length != genericArguments.length) {
                ClassNode[] newGenericArguments = new ClassNode[genericsTypes.length]
                int i = 0
                for (GenericsType gt in genericsTypes) {
                    if (i < genericArguments.length) {
                        newGenericArguments[i] = genericArguments[i].plainNodeReference
                    } else {
                        newGenericArguments[i] = ClassHelper.OBJECT_TYPE.plainNodeReference
                    }
                    i++
                }
                genericArguments = newGenericArguments
            }

            if (genericArguments.length > 0) {
                ClassNode originTraitClassNode = traitClassNode
                traitClassNode = GenericsUtils.makeClassSafeWithGenerics(originTraitClassNode, genericsTypes)
                final Map<String, ClassNode> parameterNameToParameterValue = new LinkedHashMap<String, ClassNode>()
                if (genericsTypes != null) {
                    int j = 0
                    for (GenericsType gt : traitClassNode.genericsTypes) {
                        parameterNameToParameterValue.put(gt.getName(), genericArguments[j++])
                    }
                }
                classNode.addInterface(AstUtils.replaceGenericsPlaceholders(traitClassNode, parameterNameToParameterValue))
                classNode.setUsingGenerics(true)
            } else {
                classNode.addInterface(traitClassNode.plainNodeReference)
            }

        }
    }

    void visitAfterTraitApplied(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        // no-dop
    }

    protected abstract Class getTraitClass()

}
