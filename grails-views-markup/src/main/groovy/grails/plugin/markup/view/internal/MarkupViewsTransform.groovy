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

package grails.plugin.markup.view.internal

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.GroovyASTTransformation

import grails.compiler.traits.TraitInjector
import grails.plugin.markup.view.MarkupViewTemplate
import grails.views.compiler.ViewsTransform
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.core.io.support.GrailsFactoriesLoader

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
@CompileStatic
@InheritConstructors
class MarkupViewsTransform extends ViewsTransform {
    @Override
    protected List<TraitInjector> findTraitInjectors() {
        def injectors = super.findTraitInjectors()

        injectors += GrailsFactoriesLoader.loadFactories(TraitInjector).findAll() { TraitInjector ti ->
            ti.artefactTypes.contains(MarkupViewTemplate.TYPE)
        }
        return injectors
    }

    @Override
    int priority() {
        GroovyTransformOrder.VIEWS_MARKUP_ORDER
    }
}
