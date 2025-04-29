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

package grails.plugin.json.view

import grails.plugin.json.view.internal.JsonViewsTransform
import grails.views.AbstractGroovyTemplateCompiler
import grails.views.compiler.ViewsTransform
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

/**
 * A compiler for JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@InheritConstructors
class JsonViewCompiler extends AbstractGroovyTemplateCompiler {

    @Override
    protected CompilerConfiguration configureCompiler(CompilerConfiguration configuration) {
        CompilerConfiguration compiler = super.configureCompiler(configuration)
        if(viewConfiguration.compileStatic) {
            configuration.addCompilationCustomizers(
                    new ASTTransformationCustomizer(
                            Collections.singletonMap(
                                    'extensions',
                                    'grails.plugin.json.view.internal.JsonTemplateTypeCheckingExtension'
                            ),
                            CompileStatic
                    )
            )
        }
        configuration.setScriptBaseClass(
                viewConfiguration.baseTemplateClass.name
        )
        return compiler
    }

    @Override
    protected ViewsTransform newViewsTransform() {
        return new JsonViewsTransform(viewConfiguration.extension)
    }

    static void main(String[] args) {
        run(args, JsonViewConfiguration, JsonViewCompiler)
    }
}
