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
package org.grails.cli.compiler.autoconfigure;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.grails.cli.compiler.AstUtils;
import org.grails.cli.compiler.CompilerAutoConfiguration;
import org.grails.cli.compiler.DependencyCustomizer;
import org.grails.cli.groovy.EnableGroovyTemplates;
import org.grails.cli.groovy.GroovyTemplate;

/**
 * {@link CompilerAutoConfiguration} for Groovy Templates (outside MVC).
 *
 * @author Dave Syer
 * @since 1.1.0
 */
public class GroovyTemplatesCompilerAutoConfiguration extends CompilerAutoConfiguration {

    @Override
    public boolean matches(ClassNode classNode) {
        return AstUtils.hasAtLeastOneAnnotation(classNode, "EnableGroovyTemplates");
    }

    @Override
    public void applyDependencies(DependencyCustomizer dependencies) {
        dependencies.ifAnyMissingClasses("groovy.text.TemplateEngine").add("groovy-templates");
    }

    @Override
    public void applyImports(ImportCustomizer imports) {
        imports.addStarImports("groovy.text");
        imports.addImports(EnableGroovyTemplates.class.getCanonicalName());
        imports.addStaticImport(GroovyTemplate.class.getName(), "template");
    }

}
