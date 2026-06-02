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
package org.grails.compiler.logging;

import java.lang.reflect.Modifier;
import java.net.URL;

import groovy.util.logging.Slf4j;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;

import grails.compiler.ast.AllArtefactClassInjector;
import grails.compiler.ast.AstTransformer;

/**
 * Adds a log field to all artifacts.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@AstTransformer
public class LoggingTransformer implements AllArtefactClassInjector {

    private static final ClassNode LOGGER_CLASSNODE = ClassHelper.make("org.slf4j.Logger");
    private static final ClassNode LOGGER_FACTORY_CLASSNODE = ClassHelper.make("org.slf4j.LoggerFactory");

    @Override
    public void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode);
    }

    @Override
    public void performInjection(SourceUnit source, ClassNode classNode) {
        performInjectionOnAnnotatedClass(source, classNode);
    }

    @Override
    public void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
        if (classNode.getNodeMetaData(Slf4j.class) != null) return;
        String packageName = Slf4j.class.getPackage().getName();

        // if already annotated skip
        for (AnnotationNode annotationNode : classNode.getAnnotations()) {
            if (annotationNode.getClassNode().getPackageName().equals(packageName)) {
                return;
            }
        }

        FieldNode logField = classNode.getField("log");
        if (logField != null) {
            if (!Modifier.isPrivate(logField.getModifiers())) {
                return;
            }
        }

        if (classNode.getSuperClass().getName().equals("grails.boot.config.GrailsAutoConfiguration")) {
            return;
        }

        // Groovy 5: an @Slf4j added during an AST transform is not processed, so inject the log field manually.
        MethodCallExpression getLoggerCall = new MethodCallExpression(
                new ClassExpression(LOGGER_FACTORY_CLASSNODE),
                "getLogger",
                new ClassExpression(classNode)
        );
        getLoggerCall.setMethodTarget(LOGGER_FACTORY_CLASSNODE.getMethod("getLogger", new Parameter[]{new Parameter(ClassHelper.CLASS_Type, "clazz")}));

        logField = new FieldNode(
                "log",
                Modifier.PRIVATE | Modifier.FINAL | Modifier.STATIC,
                LOGGER_CLASSNODE.getPlainNodeReference(),
                classNode,
                getLoggerCall
        );

        classNode.addField(logField);
        classNode.putNodeMetaData(Slf4j.class, logField);
    }

    public boolean shouldInject(URL url) {
        return true; // Add log property to all artifact types
    }
}
