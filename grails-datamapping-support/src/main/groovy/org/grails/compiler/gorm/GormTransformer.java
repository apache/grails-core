/*
 * Copyright 2011 SpringSource
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
package org.grails.compiler.gorm;

import grails.compiler.ast.AstTransformer;
import grails.compiler.ast.GrailsArtefactClassInjector;
import groovy.transform.Canonical;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.grails.compiler.injection.GrailsASTUtils;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.datastore.mapping.reflect.AstUtils;
import org.grails.io.support.GrailsResourceUtils;

import java.net.URL;
import java.util.Collection;

/**
 * Transforms GORM entities making the GORM API available to Java.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@AstTransformer
public class GormTransformer implements GrailsArtefactClassInjector{

    @Override
    public String[] getArtefactTypes() {
        return new String[] {DomainClassArtefactHandler.TYPE};
    }

    public boolean shouldInject(URL url) {
        return GrailsResourceUtils.isDomainClass(url);
    }

    public static Collection<String> getKnownEntityNames() {
        return AstUtils.getKnownEntityNames();
    }


    @Override
    public void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if(GrailsASTUtils.hasAnnotation(classNode, Canonical.class)) {
            GrailsASTUtils.error(source, classNode, "Class [" + classNode.getName() + "] is marked with @groovy.transform.Canonical which is not supported for GORM entities.", true);
        }
        final GormEntityTransformation transformation = new GormEntityTransformation();

        transformation.visit(classNode, source);
    }

    @Override
    public void performInjection(SourceUnit source, ClassNode classNode) {
        if(GrailsASTUtils.hasAnnotation(classNode, Canonical.class)) {
            GrailsASTUtils.error(source, classNode, "Class [" + classNode.getName() + "] is marked with @groovy.transform.Canonical which is not supported for GORM entities.", true);
        }
        new GormEntityTransformation().visit(classNode, source);
    }

    @Override
    public void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        performInjection(source, classNode);
    }
}
