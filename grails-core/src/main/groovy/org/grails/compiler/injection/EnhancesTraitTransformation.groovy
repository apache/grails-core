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

import grails.artefact.Enhances
import grails.compiler.traits.TraitInjector
import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.apache.groovy.ast.tools.AnnotatedNodeUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.MixinNode
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

import static java.lang.reflect.Modifier.PUBLIC

/**
 * Implementation for {@link Enhances)
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
@CompileStatic
class EnhancesTraitTransformation extends AbstractArtefactTypeAstTransformation implements CompilationUnitAware {

    private static final ClassNode MY_TYPE = new ClassNode(Enhances)

    CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {
        AnnotatedNode parent = (AnnotatedNode) astNodes[1]
        AnnotationNode ann = (AnnotationNode) astNodes[0]

        if (!(ann instanceof AnnotationNode) || !(parent instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${ann.getClass()} / ${parent.getClass()}")
        }

        ClassNode cNode = (ClassNode) parent

        if (isTrait(cNode)) {
            def expr = ann.getMember("value")
            if (!(expr instanceof ListExpression)) {
                def newList = new ListExpression()
                newList.addExpression(expr)
                expr = newList
            }
            def interfaces = [ClassHelper.make(TraitInjector)] as ClassNode[]

            String traitClassName = cNode.name
            if (traitClassName.endsWith('$Trait$Helper')) {
                traitClassName = traitClassName[0..-14]
            }

            ClassNode transformerNode = new ClassNode("${traitClassName}TraitInjector", PUBLIC, ClassHelper.OBJECT_TYPE, interfaces, MixinNode.EMPTY_ARRAY)

            def classNodeRef = ClassHelper.make(traitClassName).getPlainNodeReference()
            MethodNode getTraitMethodNode = transformerNode.addMethod(
                    "getTrait", PUBLIC, ClassHelper.CLASS_Type.getPlainNodeReference(), GrailsASTUtils.ZERO_PARAMETERS, null, new ReturnStatement(new ClassExpression(classNodeRef)))
            AnnotatedNodeUtils.markAsGenerated(transformerNode, getTraitMethodNode)

            def strArrayType = ClassHelper.STRING_TYPE.makeArray()
            MethodNode getArtefactTypesMethodNode = transformerNode.addMethod(
                    "getArtefactTypes", PUBLIC, strArrayType, GrailsASTUtils.ZERO_PARAMETERS, null, new ReturnStatement(CastExpression.asExpression(strArrayType, expr)))
            AnnotatedNodeUtils.markAsGenerated(transformerNode, getArtefactTypesMethodNode)

            def ast = source.AST
            transformerNode.module = ast

            ast.classes.add transformerNode

            def compilationTargetDirectory = GlobalGrailsClassInjectorTransformation.resolveCompilationTargetDirectory(source)
            GlobalGrailsClassInjectorTransformation.updateGrailsFactoriesWithType(transformerNode, GlobalGrailsClassInjectorTransformation.TRAIT_INJECTOR_CLASS, compilationTargetDirectory)

        }

    }

    boolean isTrait(ClassNode cNode) {
        org.codehaus.groovy.transform.trait.Traits.isTrait(cNode) || cNode.name.endsWith('$Trait$Helper')
    }

    @Override
    int priority() {
        GroovyTransformOrder.ENHANCES_ORDER
    }
}
