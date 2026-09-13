/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.compiler.gorm

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.springframework.core.Ordered

import grails.compiler.ast.AstTransformer
import grails.compiler.ast.GrailsArtefactClassInjector
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.io.support.GrailsResourceUtils

/**
 * Transforms GORM entities making the GORM API available to Java.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@AstTransformer
@CompileStatic
class GormTransformer implements GrailsArtefactClassInjector, Ordered {

    @Override
    int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE
    }

    @Override
    String[] getArtefactTypes() {
        return [DomainClassArtefactHandler.TYPE] as String[]
    }

    boolean shouldInject(URL url) {
        return GrailsResourceUtils.isDomainClass(url)
    }

    static Collection<String> getKnownEntityNames() {
        return AstUtils.getKnownEntityNames()
    }

    @Override
    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (GrailsASTUtils.hasAnnotation(classNode, Canonical)) {
            GrailsASTUtils.error(source, classNode, 'Class [' + classNode.getName() + '] is marked with @groovy.transform.Canonical which is not supported for GORM entities.', true)
        }
        final GormEntityTransformation transformation = new GormEntityTransformation()

        transformation.visit(classNode, source)
    }

    @Override
    void performInjection(SourceUnit source, ClassNode classNode) {
        if (GrailsASTUtils.hasAnnotation(classNode, Canonical)) {
            GrailsASTUtils.error(source, classNode, 'Class [' + classNode.getName() + '] is marked with @groovy.transform.Canonical which is not supported for GORM entities.', true)
        }
        new GormEntityTransformation().visit(classNode, source)
    }

    @Override
    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        performInjection(source, classNode)
    }

}
