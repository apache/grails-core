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
package org.grails.compiler.web.converters;

import java.net.URL;

import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.compiler.injection.AbstractGrailsArtefactTransformer;
import org.grails.plugins.converters.api.ConvertersApi;

import grails.compiler.ast.AstTransformer;

import org.grails.io.support.GrailsResourceUtils;

/**
 * Adds the asType method to domain classes.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@AstTransformer
public class ConvertersDomainTransformer extends AbstractGrailsArtefactTransformer{

    @Override
    public String getArtefactType() {
        return DomainClassArtefactHandler.TYPE;
    }

    @Override
    public Class<?> getInstanceImplementation() {
        return ConvertersApi.class;
    }

    @Override
    public Class<?> getStaticImplementation() {
        return null;
    }

    @Override
    protected boolean requiresAutowiring() {
        return false;
    }

    public boolean shouldInject(URL url) {
        return GrailsResourceUtils.isDomainClass(url);
    }
}
