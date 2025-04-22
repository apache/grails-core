/*
 * Copyright 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.compiler.web;

import java.net.URL;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer;
import grails.compiler.ast.AstTransformer;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.plugins.web.controllers.api.ControllersDomainBindingApi;
import org.grails.web.databinding.DefaultASTDatabindingHelper;

/**
 * Adds binding methods to domain classes.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@AstTransformer
public class ControllerDomainTransformer extends AbstractGrailsArtefactTransformer {

    @Override
    public String getArtefactType() {
        return DomainClassArtefactHandler.TYPE;
    }

    @Override
    public Class<?> getInstanceImplementation() {
        return ControllersDomainBindingApi.class;
    }

    @Override
    protected boolean isCandidateInstanceMethod(ClassNode classNode, MethodNode declaredMethod) {
        return false; // don't include instance methods
    }

    @Override
    public Class<?> getStaticImplementation() {
        return null;  // no static methods
    }

    @Override
    protected boolean requiresAutowiring() {
        return false;
    }

    public boolean shouldInject(URL url) {
        return GrailsResourceUtils.isDomainClass(url);
    }

    @Override
    public void performInjection(final SourceUnit source, final GeneratorContext context, final ClassNode classNode) {
        super.performInjection(source,  context, classNode);
        new DefaultASTDatabindingHelper().injectDatabindingCode(source, context, classNode);
    }
}
