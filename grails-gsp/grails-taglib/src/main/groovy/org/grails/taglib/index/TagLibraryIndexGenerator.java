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
package org.grails.taglib.index;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;

import org.grails.taglib.discovery.TagLibraryAstDiscovery;

/**
 * Writes the tag library index for a source set.
 *
 * <p>Sources are parsed to the point where the syntax tree is complete and no further, so a tag
 * library is never loaded or executed to find out what it declares. Reading the tree rather than the
 * text means the answer follows Groovy's own understanding of the source.
 *
 * <p>The index is rewritten in full each time rather than added to. A tag library that has been
 * renamed or deleted therefore disappears from it, where an index accumulated as each class compiled
 * would keep describing tags that no longer exist until the build directory was cleaned.
 *
 * <p>Invoked in a forked process by the build, with the source set's own compile classpath, because
 * the rules it applies belong to the framework rather than to the build tooling.
 *
 * @since 8.0.0
 */
public final class TagLibraryIndexGenerator {

    private static final String TAG_LIB_ANNOTATION = "grails.gsp.TagLib";
    private static final String ARTEFACT_ANNOTATION = "grails.artefact.Artefact";
    private static final String TAG_LIB_ARTEFACT = "TagLib";

    private static final String NAMESPACE_FIELD = "namespace";

    private TagLibraryIndexGenerator() {
    }

    /**
     * @param args the output directory, whether parameter names are retained, the source encoding, how
     *        many source directories follow, those directories, and then the source roots a type this
     *        project declares may be resolved from
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: <outputDir> <parameterNamesRetained> " +
                    "<sourceEncoding> <sourceDirCount> <sourceDir>... <resolutionRoot>...");
        }
        File outputDir = new File(args[0]);
        boolean parameterNamesRetained = Boolean.parseBoolean(args[1]);
        String encoding = args[2].isEmpty() ? "UTF-8" : args[2];
        int sourceDirCount = Integer.parseInt(args[3]);
        List<File> sourceDirs = new ArrayList<>(sourceDirCount);
        List<File> resolutionRoots = new ArrayList<>();
        for (int i = 4; i < args.length; i++) {
            (i - 4 < sourceDirCount ? sourceDirs : resolutionRoots).add(new File(args[i]));
        }
        generate(sourceDirs, resolutionRoots, outputDir, parameterNamesRetained, encoding);
    }

    /**
     * Regenerates the index describing every tag library under a source directory.
     *
     * @param sourceDir the directory to scan for tag libraries
     * @param outputDir the directory the index is written beneath
     * @param parameterNamesRetained whether the compilation writes parameter names into class files
     * @param encoding the source encoding
     * @throws IOException if the index cannot be written
     */
    public static void generate(File sourceDir, File outputDir, boolean parameterNamesRetained,
            String encoding) throws IOException {
        generate(Collections.singletonList(sourceDir), Collections.emptyList(), outputDir,
                parameterNamesRetained, encoding);
    }

    /**
     * Regenerates the index, resolving a type this project declares from its source.
     *
     * @param sourceDirs the directories to scan for tag libraries
     * @param outputDir the directory the index is written beneath
     * @param parameterNamesRetained whether the compilation writes parameter names into class files
     * @param encoding the source encoding
     * @throws IOException if the index cannot be written
     */
    public static void generate(List<File> sourceDirs, File outputDir, boolean parameterNamesRetained,
            String encoding) throws IOException {
        generate(sourceDirs, Collections.emptyList(), outputDir, parameterNamesRetained, encoding);
    }

    /**
     * Regenerates the index describing every tag library under any of several source directories.
     *
     * <p>All of them are described in one pass. Describing them one at a time would mean either
     * erasing the previous directory's descriptors or leaving behind descriptors for tag libraries
     * that have since been renamed or deleted.
     *
     * @param sourceDirs the directories to scan for tag libraries
     * @param resolutionRoots the source roots a type this project declares may be resolved from, so
     *        that a base class, trait or parameter type it supplies is read rather than guessed
     * @param outputDir the directory the index is written beneath
     * @param parameterNamesRetained whether the compilation writes parameter names into class files
     * @param encoding the source encoding
     * @throws IOException if the index cannot be written
     */
    public static void generate(List<File> sourceDirs, List<File> resolutionRoots, File outputDir,
            boolean parameterNamesRetained, String encoding) throws IOException {
        TagLibraryIndexWriter.clear(outputDir);
        List<File> sources = new ArrayList<>();
        if (sourceDirs != null) {
            for (File sourceDir : sourceDirs) {
                if (sourceDir != null && sourceDir.isDirectory()) {
                    sources.addAll(findGroovySources(sourceDir));
                }
            }
        }
        if (sources.isEmpty()) {
            return;
        }

        List<File> roots = resolutionRoots != null ? resolutionRoots : Collections.emptyList();
        List<File> skipped = new ArrayList<>();
        // The resolver adds a collaborator's source to the compilation unit so its type can be read,
        // which puts that class in the parse output too. Only the sources this generator was pointed
        // at may be described: a helper named *TagLib under src/main/groovy would otherwise be filed
        // as a tag library of the default namespace, making its methods g tags that either collide
        // with real ones - silently disabling rewriting for that name - or resolve to a tag that does
        // not exist at runtime.
        Set<String> describable = new HashSet<>();
        for (File source : sources) {
            describable.add(source.getAbsolutePath());
        }
        for (ClassNode classNode : parse(sources, roots, parameterNamesRetained, encoding, skipped)) {
            if (!isTagLibrary(classNode) || !isRegistrable(classNode) ||
                    !wasAskedFor(classNode, describable)) {
                continue;
            }
            String namespace = TagLibraryAstDiscovery.resolveNamespace(classNode);
            if (namespace == null) {
                // Only knowable once the initialiser runs, so recording it would file the tags under
                // a guess. Left out, which leaves the tag library to runtime resolution.
                continue;
            }
            TagLibraryIndexWriter.write(outputDir, classNode.getName(), namespace,
                    TagLibraryAstDiscovery.findTags(classNode, parameterNamesRetained));
        }
        recordWhatWasMissed(outputDir, skipped, encoding);
    }

    /**
     * Records the namespaces left incomplete by whatever could not be read, so that a call to a tag of
     * one of them is never reported as a misspelling.
     */
    private static void recordWhatWasMissed(File outputDir, List<File> skipped, String encoding)
            throws IOException {
        Set<String> namespaces = new TreeSet<>();
        boolean everything = false;
        for (File source : skipped) {
            String namespace = declaredNamespace(source, encoding);
            if (namespace != null) {
                namespaces.add(namespace);
            }
            else {
                everything = true;
            }
        }
        TagLibraryIndexWriter.writeIncomplete(outputDir, namespaces, everything);
    }

    /**
     * Parses the sources far enough to describe them.
     *
     * <p>A tag library referring to something outside this directory and off the classpath given here,
     * such as a service in the same project, cannot be resolved before that project is compiled. Those
     * are parsed on their own and skipped when they still fail, rather than losing the index for every
     * other tag library alongside them. What was skipped is recorded, so that nothing in a namespace
     * missing some of its tags is reported; a build that writes the index describes it again once the
     * project has been compiled, and until then its tags resolve dynamically, exactly as a tag library
     * with no descriptor does.
     */
    private static List<ClassNode> parse(List<File> sources, List<File> resolutionRoots,
            boolean parameterNamesRetained, String encoding, List<File> skippedOut) {
        try {
            return collectClassNodes(compile(sources, resolutionRoots, parameterNamesRetained, encoding));
        } catch (Exception wholeSourceSetFailed) {
            List<ClassNode> classNodes = new ArrayList<>();
            for (File source : sources) {
                try {
                    classNodes.addAll(collectClassNodes(
                            compile(List.of(source), resolutionRoots, parameterNamesRetained, encoding)));
                } catch (Exception singleSourceFailed) {
                    skippedOut.add(source);
                }
            }
            if (!skippedOut.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (File skipped : skippedOut) {
                    names.add(skipped.getName());
                }
                System.out.println("Tag library index: could not read " + String.join(", ", names) +
                        " before compilation; their tags resolve dynamically until they are compiled.");
            }
            classNodes.sort(Comparator.comparing(ClassNode::getName));
            return classNodes;
        }
    }

    /**
     * The namespace a source that could not be described declares, read from its syntax tree.
     *
     * <p>Only ever used to record which namespace is missing some of its tags. Naming the wrong one
     * leaves the real one looking complete, which is exactly when a call to a tag that does exist gets
     * reported as one that does not, so this claims a namespace only where the source leaves no room
     * for doubt: one tag library in the file, declaring its own namespace as a constant.
     *
     * <p>Anything else - a namespace field on some other class in the file, more than one tag library,
     * a namespace inherited from a base class that may not have resolved, or none stated at all -
     * yields nothing, and every namespace is then treated as incomplete. That costs diagnostics rather
     * than inventing an error.
     *
     * @return the namespace, or {@code null} when this source does not plainly state one
     */
    private static String declaredNamespace(File source, String encoding) {
        try {
            CompilerConfiguration configuration = new CompilerConfiguration();
            configuration.setSourceEncoding(encoding);
            CompilationUnit unit = new CompilationUnit(configuration);
            unit.addSource(source);
            // Conversion builds the tree and stops before resolving anything, so a type this project
            // has not compiled yet cannot make it fail.
            unit.compile(Phases.CONVERSION);

            ClassNode candidate = null;
            for (ClassNode classNode : collectClassNodes(unit)) {
                if (!isTagLibrary(classNode)) {
                    continue;
                }
                if (candidate != null) {
                    // Which of them failed is not knowable, so neither is claimed.
                    return null;
                }
                candidate = classNode;
            }
            if (candidate == null) {
                return null;
            }

            FieldNode field = candidate.getDeclaredField(NAMESPACE_FIELD);
            if (field == null || !field.isStatic()) {
                // Either the default namespace or one inherited from a base class whose resolution is
                // the very thing in doubt. Not distinguishable here, so not claimed.
                return null;
            }
            if (!(field.getInitialExpression() instanceof ConstantExpression constant) ||
                    constant.getValue() == null) {
                return null;
            }
            String namespace = constant.getValue().toString().trim();
            return namespace.isEmpty() ? null : namespace;
        }
        catch (Exception unparseable) {
            return null;
        }
    }

    private static CompilationUnit compile(List<File> sources, List<File> resolutionRoots,
            boolean parameterNamesRetained, String encoding) {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.setParameters(parameterNamesRetained);
        configuration.setSourceEncoding(encoding);
        CompilationUnit unit = new CompilationUnit(configuration);
        if (!resolutionRoots.isEmpty()) {
            unit.setClassNodeResolver(new SourceRootClassNodeResolver(resolutionRoots));
        }
        for (File source : sources) {
            unit.addSource(source);
        }
        // Canonicalization is the last phase before bytecode, by which point traits are applied and
        // annotations resolved, and it stops short of generating or loading any class.
        unit.compile(Phases.CANONICALIZATION);
        return unit;
    }

    /**
     * Resolves a type this project declares by compiling its source alongside the tag library that
     * refers to it.
     *
     * <p>A tag library commonly refers to something the same project declares - a service it injects,
     * a base class it extends, a trait it carries - and none of those exist as classes yet when the
     * index is generated. Compiling their source too is what the Groovy compiler does for types within
     * one compilation, and is what lets a tag library be described exactly as it will be once built.
     *
     * <p>Deliberately not a stand-in class node. What is missing decides what a tag library declares:
     * a base class carries the namespace, a trait carries tags, and a parameter type decides whether a
     * method is a tag at all. Answering with a placeholder would file a tag library under the wrong
     * namespace, or leave out tags the running application has, and the index would then disagree with
     * what the application does - which is the one thing it must never do. A type that cannot be found
     * in source is left unresolved, and the tag library referring to it is skipped as before.
     */
    private static final class SourceRootClassNodeResolver extends ClassNodeResolver {

        private final List<File> roots;

        private SourceRootClassNodeResolver(List<File> roots) {
            this.roots = roots;
        }

        @Override
        public LookupResult resolveName(String name, CompilationUnit compilationUnit) {
            LookupResult onTheClasspath = super.resolveName(name, compilationUnit);
            if (onTheClasspath != null) {
                return onTheClasspath;
            }
            File source = findSource(name);
            if (source == null) {
                // Not something this project declares. Left unresolved so that resolution carries on
                // to the next candidate a star import offers, and so that a name that is simply
                // misspelled still fails rather than being quietly invented.
                return null;
            }
            return new LookupResult(compilationUnit.addSource(source), null);
        }

        private File findSource(String name) {
            String relativePath = name.replace('.', File.separatorChar) + ".groovy";
            for (File root : roots) {
                File candidate = new File(root, relativePath);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private static List<ClassNode> collectClassNodes(CompilationUnit unit) {
        List<ClassNode> classNodes = new ArrayList<>();
        unit.getAST().getModules().forEach(module -> classNodes.addAll(module.getClasses()));
        // Sorted so that the index is identical for identical sources regardless of the order the
        // file system enumerated them, keeping the build reproducible.
        classNodes.sort(Comparator.comparing(ClassNode::getName));
        return classNodes;
    }

    private static boolean isTagLibrary(ClassNode classNode) {
        for (AnnotationNode annotation : classNode.getAnnotations()) {
            String annotationName = annotation.getClassNode().getName();
            if (TAG_LIB_ANNOTATION.equals(annotationName)) {
                return true;
            }
            if (ARTEFACT_ANNOTATION.equals(annotationName)) {
                var member = annotation.getMember("value");
                if (member != null && TAG_LIB_ARTEFACT.equals(member.getText())) {
                    return true;
                }
            }
        }
        return classNode.getName().endsWith(TAG_LIB_ARTEFACT);
    }

    /**
     * Whether a class can be registered as a tag library at all.
     *
     * <p>An abstract class cannot: artefact handling rejects one, and {@code TagLibArtefactHandler}
     * does not allow abstract artefacts. A base class shared by several tag libraries is a normal
     * thing to keep beside them, so describing it would file its methods under a namespace nothing
     * answers to - and its subclasses do not inherit them as tags either, since a tag method is read
     * from the declaring class. Traits and interfaces are covered by the same check.
     *
     * @param classNode the class to consider
     * @return true when an application could register it
     */
    private static boolean isRegistrable(ClassNode classNode) {
        return !classNode.isAbstract() && !classNode.isInterface();
    }

    /**
     * @param classNode a class the compilation produced
     * @param describable the absolute paths of the sources this generator was given
     * @return whether the class came from one of those sources rather than from a resolved collaborator
     */
    private static boolean wasAskedFor(ClassNode classNode, Set<String> describable) {
        if (classNode.getModule() == null || classNode.getModule().getContext() == null) {
            return false;
        }
        String name = classNode.getModule().getContext().getName();
        return name != null && describable.contains(new File(name).getAbsolutePath());
    }

    private static List<File> findGroovySources(File sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".groovy"))
                    .sorted()
                    .map(Path::toFile)
                    .toList();
        }
    }
}
