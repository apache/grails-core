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
package org.grails.compiler.logging;

import grails.compiler.ast.AllArtefactClassInjector;
import grails.compiler.ast.AstTransformer;
import groovy.lang.GroovyClassLoader;
import groovy.util.logging.Slf4j;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.LogASTTransformation;
import org.grails.datastore.mapping.reflect.AstUtils;

import java.lang.reflect.Modifier;
import java.net.URL;

/**
 * Adds a log field to all artifacts.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@AstTransformer
public class LoggingTransformer implements AllArtefactClassInjector{

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
        if( classNode.getNodeMetaData(Slf4j.class) != null) return;
        String packageName = Slf4j.class.getPackage().getName();

        // if already annotated skip
        for (AnnotationNode annotationNode : classNode.getAnnotations()) {
            if(annotationNode.getClassNode().getPackageName().equals(packageName)) {
                return;
            }
        }

        FieldNode logField = classNode.getField("log");
        if(logField != null) {
            if(!Modifier.isPrivate(logField.getModifiers())) {
                return;
            }
        }

        if (classNode.getSuperClass().getName().equals("grails.boot.config.GrailsAutoConfiguration")) {
            return;
        }

        AnnotationNode annotationNode = new AnnotationNode(ClassHelper.make(Slf4j.class));
        LogASTTransformation logASTTransformation = new LogASTTransformation();
        logASTTransformation.setCompilationUnit( new CompilationUnit(new GroovyClassLoader(getClass().getClassLoader())) );
        logASTTransformation.visit(new ASTNode[]{ annotationNode, classNode}, source);
        classNode.putNodeMetaData(Slf4j.class, annotationNode);
    }

    public boolean shouldInject(URL url) {
        return true; // Add log property to all artifact types
    }
}
