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

package org.grails.compiler.scaffolding

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit

import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import grails.plugin.scaffolding.GormService
import grails.plugin.scaffolding.annotation.Scaffold
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.core.artefact.ServiceArtefactHandler
import org.grails.io.support.GrailsResourceUtils
import org.grails.plugins.web.rest.transform.ResourceTransform

/**
 * Transformation that turns a service into a scaffolding service at compile time if '@Scaffold'
 * is specified
 *
 * @author Scott Murphy Heiberg
 * @since 5.1
 */
@AstTransformer
@CompileStatic
class ScaffoldingServiceInjector implements GrailsArtefactClassInjector {

    final String[] artefactTypes = [ServiceArtefactHandler.TYPE] as String[]
    public static Pattern SERVICE_PATTERN = Pattern.compile('.+/' +
        GrailsResourceUtils.GRAILS_APP_DIR + '/services/(.+)Service\\.groovy')

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        def annotationNode = classNode.getAnnotations(ClassHelper.make(Scaffold)).find()
        if (annotationNode) {
            ClassNode valueClassNode = annotationNode?.getMember('value')?.type
            ClassNode superClassNode = ClassHelper.make(GormService).getPlainNodeReference()
            ClassNode currentSuperClass = classNode.getSuperClass()
            if (currentSuperClass.equals(GrailsASTUtils.OBJECT_CLASS_NODE)) {
                def domainClass = annotationNode.getMember('domain')?.type
                if (!domainClass) {
                    def genericsTypes = valueClassNode?.genericsTypes
                    boolean hasGenerics = genericsTypes != null && genericsTypes.length > 0

                    if (hasGenerics) {
                        domainClass = ScaffoldingControllerInjector.extractGenericDomainClass(valueClassNode)
                        if (domainClass) {
                            annotationNode.addMember('domain', new ClassExpression(domainClass))
                        }
                        superClassNode = valueClassNode.getPlainNodeReference()
                    } else if (valueClassNode) {
                        domainClass = valueClassNode
                        annotationNode.addMember('domain', new ClassExpression(domainClass))
                        annotationNode.setMember('value', new ClassExpression(superClassNode))
                    }
                }
                if (!domainClass) {
                    GrailsASTUtils.error(source, classNode, "Scaffolded service (${classNode.name}) with @Scaffold does not have domain class set.", true)
                }
                // Parameterize the superclass (e.g. GormService<Domain>) so inherited get()/list()/save()
                // resolve to the domain type under static compilation, not the GormEntity
                // upper bound. Only single-type-parameter bases are parameterized — a
                // base declaring zero or multiple type parameters would get a malformed generic
                // signature from a single domain argument, so those keep the previous raw form.
                GenericsType[] declaredTypeParams = superClassNode.redirect().genericsTypes
                if (declaredTypeParams != null && declaredTypeParams.length == 1) {
                    ClassNode parameterizedSuper = superClassNode.getPlainNodeReference()
                    parameterizedSuper.setGenericsTypes(
                        [new GenericsType(GrailsASTUtils.nonGeneric(domainClass))] as GenericsType[])
                    // Injection runs at CANONICALIZATION (after generics resolution), so the generic superclass
                    // signature is only emitted when the class node itself reports usesGenerics; otherwise it is
                    // written raw. Required - do not remove.
                    classNode.setUsingGenerics(true)
                    classNode.setSuperClass(parameterizedSuper)
                } else {
                    classNode.setSuperClass(GrailsASTUtils.nonGeneric(superClassNode, domainClass))
                }
                def readOnlyExpression = (ConstantExpression) annotationNode.getMember('readOnly')
                new ResourceTransform().addConstructor(classNode, domainClass, readOnlyExpression?.getValue()?.asBoolean() ?: false)
            } else if (!currentSuperClass.isDerivedFrom(superClassNode)) {
                GrailsASTUtils.error(source, classNode, "Scaffolded services (${classNode.name}) cannot extend other classes: ${currentSuperClass.getName()}", true)
            }
        }
    }

    @Override
    boolean shouldInject(URL url) {
        return url != null && SERVICE_PATTERN.matcher(url.getFile()).find()
    }
}
