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

package grails.gsp.taglib.compiler;

import java.io.File;
import java.io.IOException;

import groovy.lang.Closure;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import grails.gsp.TagLib;
import org.grails.compiler.injection.ArtefactTypeAstTransformation;
import org.grails.compiler.injection.GrailsASTUtils;
import org.grails.taglib.discovery.TagLibraryAstDiscovery;
import org.grails.taglib.index.TagLibraryIndex;
import org.grails.taglib.index.TagLibraryIndexWriter;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class TagLibArtefactTypeAstTransformation extends ArtefactTypeAstTransformation {

    /**
     * System property to silence the closure-based tag deprecation warning emitted at compile time.
     * Set to {@code false} to suppress (default {@code true}).
     */
    public static final String WARN_DEPRECATED_CLOSURES_PROPERTY = "grails.taglib.warnDeprecatedClosures";

    private static final ClassNode MY_TYPE = new ClassNode(TagLib.class);
    private static final ClassNode CLOSURE_TYPE = new ClassNode(Closure.class);

    @Override
    protected String resolveArtefactType(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        addClosureTagDeprecationWarnings(sourceUnit, classNode);
        writeIndexEntry(sourceUnit, classNode);
        rewriteResolvedTagCalls(sourceUnit, classNode);
        return "TagLibrary";
    }

    /**
     * Records the namespace and tag names this tag library declares, so that a GSP compiled later can
     * resolve a tag call without loading the tag library or consulting its metaclass.
     *
     * <p>Failure to write is never fatal: the index is an optimisation, and a missing descriptor
     * degrades to the runtime resolution that applies when a tag library is registered dynamically.
     */
    protected void writeIndexEntry(SourceUnit sourceUnit, ClassNode classNode) {
        if (classNode.isAbstract() || classNode.isInterface()) {
            // Artefact handling never registers an abstract class, so describing one would record
            // tags that nothing answers to at runtime.
            return;
        }
        File targetDirectory = sourceUnit.getConfiguration() != null ?
                sourceUnit.getConfiguration().getTargetDirectory() :
                null;
        if (targetDirectory == null) {
            // In-memory compilation, as used by GSP unit tests and the shell, has nowhere to put the
            // descriptor; those callers resolve tags at runtime.
            return;
        }
        if (buildOwnsIndex(sourceUnit)) {
            // The build writes the index itself, reading the source of anything a tag library refers
            // to so that it can describe all of them. Writing a copy here as well would put a second
            // index into the class output, competing with that one for the same path when packaged,
            // and nothing would remove it when this tag library was renamed or deleted: an incremental
            // compilation does not revisit a source that has not changed, so it would simply stay.
            return;
        }
        String namespace = TagLibraryAstDiscovery.resolveNamespace(classNode);
        if (namespace == null) {
            // The namespace is only known once the tag library's initialiser runs, so recording the
            // tags would file them under the wrong namespace. Leave them to runtime resolution.
            return;
        }
        // Runtime only treats a parameter as attrs or body when it carries that name, unless the class
        // was compiled without parameter names, in which case any name is accepted. The same
        // compilation setting therefore decides which methods are dispatchable.
        boolean parameterNamesRetained = sourceUnit.getConfiguration().getParameters();
        try {
            TagLibraryIndexWriter.write(targetDirectory, classNode.getName(), namespace,
                    TagLibraryAstDiscovery.findTags(classNode, parameterNamesRetained));
        } catch (IOException | RuntimeException e) {
            GrailsASTUtils.warning(sourceUnit, classNode,
                    "Could not write the tag library index entry for [" + classNode.getName() + "]: " +
                            e.getMessage() + ". Tags in this library will be resolved at runtime.");
        }
    }

    /**
     * Whether the build writes the index itself, which the Grails Gradle plugin does and signals by
     * putting the settings it declared on the compile classpath.
     *
     * <p>Where no build does - compiling outside the Grails Gradle plugin, as this framework's own
     * build and a plain Groovy compilation do - each tag library describes itself as it compiles.
     */
    private static boolean buildOwnsIndex(SourceUnit sourceUnit) {
        ClassLoader classLoader = sourceUnit.getClassLoader();
        return classLoader != null && classLoader.getResource(TagLibraryIndex.SETTINGS_LOCATION) != null;
    }

    /**
     * Replaces calls to tags this build already knows about with direct invocations, leaving anything
     * it cannot resolve to be dispatched as before.
     */
    protected void rewriteResolvedTagCalls(SourceUnit sourceUnit, ClassNode classNode) {
        TagLibraryIndex index = TagLibraryIndex.forClassLoader(sourceUnit.getClassLoader());
        if (index.isEmpty()) {
            return;
        }
        new CompiledTagCallRewriter(sourceUnit, index, classNode).rewrite();
    }

    @Override
    protected ClassNode getAnnotationType() {
        return MY_TYPE;
    }

    protected void addClosureTagDeprecationWarnings(SourceUnit sourceUnit, ClassNode classNode) {
        if (!Boolean.parseBoolean(System.getProperty(WARN_DEPRECATED_CLOSURES_PROPERTY, "true"))) {
            return;
        }
        if (classNode.getPackageName() != null && classNode.getPackageName().startsWith("org.grails.plugins.web.taglib")) {
            return;
        }
        for (FieldNode field : classNode.getFields()) {
            if (field.isStatic()) {
                continue;
            }
            if (field.getType() != null && CLOSURE_TYPE.equals(field.getType())) {
                String message = "Closure-based tag definition [" + field.getName() + "] in TagLib [" +
                        classNode.getName() + "] is deprecated and is not resolved when a page is " +
                        "compiled, so calls to it stay dynamic. Define the tag as a method instead: " +
                        "def " + field.getName() + "(Map attrs) { ... }";
                org.grails.compiler.injection.GrailsASTUtils.warning(sourceUnit, field, message);
            }
        }
    }
}
