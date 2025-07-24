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
package org.grails.cli.boot;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import groovy.lang.Grab;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import grails.util.Environment;
import org.grails.cli.compiler.AstUtils;
import org.grails.cli.compiler.CompilerAutoConfiguration;
import org.grails.cli.compiler.DependencyCustomizer;
import org.grails.cli.compiler.GroovyCompilerConfiguration;
import org.grails.cli.compiler.autoconfigure.SpringMvcCompilerAutoConfiguration;
import org.grails.cli.compiler.dependencies.Dependency;
import org.grails.cli.compiler.dependencies.DependencyManagement;
import org.grails.cli.compiler.grape.DependencyResolutionContext;


/**
 * A {@link CompilerAutoConfiguration} for Grails Micro Service applications
 *
 * @author Graeme Rocher
 * @since 3.0
 */
public class GrailsApplicationCompilerAutoConfiguration extends CompilerAutoConfiguration {

    public static final String[] DEFAULT_IMPORTS = new String[]{
                                                        "grails.persistence",
                                                        "grails.gorm",
                                                        "grails.rest",
                                                        "grails.artefact",
                                                        "grails.web",
                                                        "grails.boot.config" };
    public static final String ENABLE_AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    public static final ClassNode ENABLE_AUTO_CONFIGURATION_CLASS_NODE = ClassHelper.make(ENABLE_AUTO_CONFIGURATION);
    ClassNode lastMatch = null;

    @Override
    public boolean matches(ClassNode classNode) {
        boolean matches = AstUtils.hasAtLeastOneAnnotation(classNode, "grails.persistence.Entity", "grails.rest.Resource", "Resource", "grails.artefact.Artefact", "grails.web.Controller");
        if(matches) lastMatch = classNode;
        return matches;
    }


    @Override
    public void applyDependencies(DependencyCustomizer dependencies) throws CompilationFailedException {
        addManagedDependencies(dependencies);
        if(lastMatch != null) {
            lastMatch.addAnnotation(createGrabAnnotation("org.apache.grails", "grails-dependencies", Environment.class.getPackage().getImplementationVersion(), null, "pom", true));
            lastMatch.addAnnotation(createGrabAnnotation("org.apache.grails", "grails-web-boot", Environment.class.getPackage().getImplementationVersion(), null, null, true));
        }
        new SpringMvcCompilerAutoConfiguration().applyDependencies(dependencies);
    }

    private void addManagedDependencies(DependencyCustomizer dependencies) {
        final List<org.eclipse.aether.graph.Dependency> current = dependencies
                .getDependencyResolutionContext().getManagedDependencies();
        final DependencyResolutionContext resolutionContext = dependencies.getDependencyResolutionContext();
        resolutionContext.addDependencyManagement(new GrailsDependencies(current));
        resolutionContext.addDependencyManagement(getAdditionalDependencies());
    }

    protected DependencyManagement getAdditionalDependencies() {
        return new GrailsDependencyVersions();
    }


    public static AnnotationNode createGrabAnnotation(String group, String module,
                                                String version, String classifier, String type, boolean transitive) {
        AnnotationNode annotationNode = new AnnotationNode(new ClassNode(Grab.class));
        annotationNode.addMember("group", new ConstantExpression(group));
        annotationNode.addMember("module", new ConstantExpression(module));
        annotationNode.addMember("version", new ConstantExpression(version));
        if (classifier != null) {
            annotationNode.addMember("classifier", new ConstantExpression(classifier));
        }
        if (type != null) {
            annotationNode.addMember("type", new ConstantExpression(type));
        }
        annotationNode.addMember("transitive", new ConstantExpression(transitive));
        annotationNode.addMember("initClass", new ConstantExpression(false));
        return annotationNode;
    }


    @Override
    public void applyImports(ImportCustomizer imports) throws CompilationFailedException {
        imports.addStarImports(DEFAULT_IMPORTS);
        new SpringMvcCompilerAutoConfiguration().applyImports(imports);
    }

    @Override
    public void applyToMainClass(GroovyClassLoader loader, GroovyCompilerConfiguration configuration, GeneratorContext generatorContext, SourceUnit source, ClassNode classNode) throws CompilationFailedException {

        // if we arrive here then there is no 'Application' class and we need to add one automatically
        ClassNode applicationClassNode = new ClassNode("Application", Modifier.PUBLIC, ClassHelper.make("grails.boot.config.GrailsAutoConfiguration"));
        AnnotationNode enableAutoAnnotation = new AnnotationNode(ENABLE_AUTO_CONFIGURATION_CLASS_NODE);
        try {
            enableAutoAnnotation.addMember("exclude", new ClassExpression( ClassHelper.make("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")) );
        } catch (Throwable e) {
            // ignore
        }
        applicationClassNode.addAnnotation(enableAutoAnnotation);
        applicationClassNode.setModule(source.getAST());
        applicationClassNode.addMethod("shouldScanDefaultPackage", Modifier.PUBLIC, ClassHelper.Boolean_TYPE, new Parameter[0], null, new ReturnStatement(new ConstantExpression(Boolean.TRUE)));
        source.getAST().getClasses().add(0, applicationClassNode);
        classNode.addAnnotation(new AnnotationNode(ClassHelper.make("org.grails.boot.internal.EnableAutoConfiguration")));
    }

    class GrailsDependencies implements DependencyManagement {

        private Map<String, Dependency> groupAndArtifactToDependency = new HashMap<String, Dependency>();

        private Map<String, String> artifactToGroupAndArtifact = new HashMap<String, String>();
        private List<Dependency> dependencies = new ArrayList<Dependency>();

        public GrailsDependencies(List<org.eclipse.aether.graph.Dependency> dependencies) {
            for (org.eclipse.aether.graph.Dependency dependency : dependencies) {
                String groupId = dependency.getArtifact().getGroupId();
                String artifactId = dependency.getArtifact().getArtifactId();
                String version = dependency.getArtifact().getVersion();

                List<Dependency.Exclusion> exclusions = new ArrayList<Dependency.Exclusion>();
                Dependency value = new Dependency(groupId, artifactId, version, exclusions);
                this.dependencies.add(value);
                groupAndArtifactToDependency.put(groupId + ":" + artifactId, value);
                artifactToGroupAndArtifact.put(artifactId, groupId + ":" + artifactId);
            }
        }

//        @Override
//        public Dependency find(String groupId, String artifactId) {
//            return groupAndArtifactToDependency.get(groupId + ":" + artifactId);
//        }


        @Override
        public List<Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public String getSpringBootVersion() {
            return find("spring-boot").getVersion();
        }

        @Override
        public Dependency find(String artifactId) {
            String groupAndArtifact = artifactToGroupAndArtifact.get(artifactId);
            if (groupAndArtifact==null) {
                return null;
            }
            return groupAndArtifactToDependency.get(groupAndArtifact);
        }

//        @Override
//        public Iterator<Dependency> iterator() {
//            return groupAndArtifactToDependency.values().iterator();
//        }
    }

}
