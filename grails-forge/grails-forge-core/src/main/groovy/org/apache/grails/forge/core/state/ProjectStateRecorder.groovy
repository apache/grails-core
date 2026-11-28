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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtefact
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectLock.LockedRepository
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.gradle.GradleRepository
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.Template
import org.grails.forge.options.Options
import org.grails.forge.util.VersionInfo
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Writes the state of a project while it is generated. Every file of the application is rendered once,
 * here, and before anything is written: the bytes that are written to the application are the bytes
 * the baseline keeps and the index has the digest of, also for a template that would not render the
 * same a second time. A file is binary in the index when its template says so or when its content is
 * not text in UTF-8. The state is read back from its bytes before any of it is written, so what is
 * written is what the reader takes.
 *
 * <p>The baseline holds what the generator rendered. A file that is not written because the directory
 * already has one of its name, as {@code --inplace} allows, is in the baseline as the generator would
 * have written it: the file that is there is to the baseline what a file the user changed is.
 * {@code grails-forge-cli.yml}, the record of the features of earlier versions, is not written, and
 * no feature writes a file of its name: the command line reads one as the record of a project that
 * has no state.</p>
 *
 * <p>The manifest is written last. A directory that has other files of the state and no manifest was
 * not generated to the end: generating into it again replaces what is there.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectStateRecorder {

    private static final List<String> FILES = [ProjectState.ATTRIBUTES, ProjectState.IGNORE, ProjectState.BASELINE_INDEX, ProjectState.LOCK, ProjectState.MANIFEST]

    private final ApplicationType applicationType
    private final Project project
    private final GeneratorContext generatorContext
    private final OutputHandler outputHandler
    private final Map<String, Rendered> files = new TreeMap<>()
    private long size

    ProjectStateRecorder(ApplicationType applicationType, Project project, GeneratorContext generatorContext, OutputHandler outputHandler) {
        this.applicationType = applicationType
        this.project = project
        this.generatorContext = generatorContext
        this.outputHandler = outputHandler
    }

    /**
     * Before anything is rendered or written: the state has to be able to name every file of the
     * application, and where it is written there must be no state of a project that was generated to
     * the end, and nothing in the place of its directories that is not a directory.
     *
     * @param templates the files of the application
     * @throws IllegalArgumentException for an application whose state cannot be written, with what
     * to do about it
     */
    void check(Collection<Template> templates) {
        if (outputHandler instanceof FileSystemOutputHandler) {
            Path directory = ((FileSystemOutputHandler) outputHandler).getApplicationDirectory().toPath()
            for (String each : [ProjectState.DIRECTORY, ProjectState.BASELINE, ProjectState.BASELINE_FILES]) {
                Path path = directory.resolve(each)
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Cannot create the project because ${each} in the directory is a file or a link and not a directory; " +
                            'remove it and create the project again')
                }
            }
            for (String each : FILES) {
                Path path = directory.resolve(each)
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Cannot create the project because ${each} in the directory is a directory or a link and not a file; " +
                            'remove it and create the project again')
                }
            }
        }
        if (outputHandler instanceof FileSystemOutputHandler) {
            obstacles(((FileSystemOutputHandler) outputHandler).getApplicationDirectory().toPath(), templates)
        }
        if (outputHandler.exists(ProjectState.MANIFEST)) {
            throw new IllegalArgumentException("Cannot create the project because the directory already has the state of a generated project (${ProjectState.MANIFEST}); " +
                    "to generate the project anew, remove the directory ${ProjectState.DIRECTORY} first, which discards the record of how it was generated")
        }
        if (templates.size() > StateLimits.MAX_FILES) {
            throw new IllegalArgumentException("The features write ${templates.size()} files, and the state of a project can name ${StateLimits.MAX_FILES}")
        }
        for (Template template : templates) {
            String path = path(template)
            if (ProjectState.isStatePath(path)) {
                throw new IllegalArgumentException("The features write the file ${path}, and ${ProjectState.DIRECTORY} is where the state of the project is kept")
            }
            if (BaselineIndex.folded(path) == ProjectState.EARLIER_RECORD || BaselineIndex.folded(path) == 'grails-cli.yml') {
                throw new IllegalArgumentException("The features write the file ${path}, which the command line reads as the record of a project of an earlier version")
            }
            if (!BaselineIndex.isBaselinePath(path)) {
                throw new IllegalArgumentException("The features write the file ${path}, which is not a path the state of the project can name")
            }
        }
    }

    // What can be seen before anything is written: a file where the project needs a directory, a
    // directory where it needs a file, and a directory that cannot be written to
    private void obstacles(Path directory, Collection<Template> templates) {
        Path existing = directory
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent()
        }
        if (existing != null && existing != directory && !Files.isWritable(existing)) {
            throw new IllegalArgumentException('Cannot create the project because the directory it is to be created in cannot be written to')
        }
        Set<String> checked = new HashSet<>()
        List<String> paths = templates.collect { Template template -> path(template) }
        paths << ProjectState.MANIFEST << "${ProjectState.BASELINE_FILES}/x".toString()
        for (String path : paths) {
            List<String> steps = path.split('/').toList()
            Path current = directory
            String at = ''
            for (int index = 0; index < steps.size(); index++) {
                boolean last = index == steps.size() - 1
                if (Files.exists(current) && !Files.isDirectory(current)) {
                    break
                }
                if (Files.isDirectory(current) && !Files.exists(current.resolve(steps[index]), LinkOption.NOFOLLOW_LINKS) && checked.add('w:' + at) && !Files.isWritable(current)) {
                    throw new IllegalArgumentException("Cannot create the project because ${at ?: 'the directory'}${at ? ' in the directory' : ''} cannot be written to")
                }
                current = current.resolve(steps[index])
                at = at ? at + '/' + steps[index] : steps[index]
                if (!checked.add(at) || !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    continue
                }
                if (!last && !Files.isDirectory(current)) {
                    throw new IllegalArgumentException("Cannot create the project because ${at} in the directory is a file, and the project has files in a directory of that name; " +
                            'remove or rename it and create the project again')
                }
                if (last && Files.isDirectory(current) && path != "${ProjectState.BASELINE_FILES}/x".toString()) {
                    throw new IllegalArgumentException("Cannot create the project because ${at} in the directory is a directory, and the project has a file of that name; " +
                            'remove or rename it and create the project again')
                }
            }
        }
    }

    /**
     * Renders a file of the application.
     *
     * @param template a file of the application
     * @return the file as it was rendered, to be written in place of the template
     */
    Template record(Template template) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        template.write(out)
        byte[] content = out.toByteArray()
        // a template that is not said to be binary can still be an image: what is not text is never
        // read as text, whatever the template says
        Rendered rendered = new Rendered(path(template), content, template.isBinary() || !StateYaml.isText(content), template.isExecutable())
        size += content.length
        if (content.length > StateLimits.MAX_BASELINE_FILE || size > StateLimits.MAX_BASELINE) {
            throw new IllegalArgumentException("The file ${rendered.path} has ${content.length} bytes, and a file of the baseline of a project can have " +
                    "${StateLimits.MAX_BASELINE_FILE} and all of them ${StateLimits.MAX_BASELINE}")
        }
        files.put(rendered.path, rendered)
        // written under the path the template has, as it was before there was a state
        return new Rendered(template.getPath(), content, template.isBinary(), rendered.executable)
    }

    /**
     * The files of the state for the files that were recorded: the content of the baseline, what
     * tells Git to keep and to commit it, its index, the lock and, last, the manifest. They are
     * written whether or not there is a file of the name already, after {@link #clear()}.
     *
     * @throws IllegalArgumentException for a state that could not be read back, which nothing of is
     * to be written
     */
    List<Template> state() {
        List<Template> state = []
        List<BaselineFile> index = []
        for (Rendered file : files.values()) {
            state << new Rendered("${ProjectState.BASELINE_FILES}/${BaselineIndex.copyPath(file.path)}".toString(), file.content, file.binary, false)
            index << new BaselineFile(file.path, StateYaml.sha256(file.content), file.binary ? BaselineFile.BINARY : BaselineFile.TEXT, file.executable)
        }
        byte[] indexBytes = new BaselineIndex(index).toBytes()
        // what the application has: an option that was not given gives way to the features
        Options ofApplication = generatorContext.getApplicationOptions()
        GenerationOptions options = new GenerationOptions(generatorContext.getJdkVersion(), ofApplication.getServletImpl(),
                ofApplication.getGormImpl(), ofApplication.getDevelopmentReloading(), ofApplication.isSubproject(), ofApplication.getFeatureOptions())
        String grailsVersion = VersionInfo.getGrailsVersion()
        List<Feature> applied = new ArrayList<>(generatorContext.getFeatures().getFeatures())
        applied.sort(FeatureContext.FEATURE_ORDER)
        List<LockedFeature> locked = applied.collect { Feature feature -> locked(feature) }
        // the preset the application was made from is a feature of it, the one whose pack is a preset
        Feature preset = applied.find { Feature feature -> feature instanceof PackFeature && ((PackFeature) feature).getPack().getDescriptor().getPreset() != null }
        byte[] manifestBytes = new ProjectManifest(
                grailsVersion,
                applicationType,
                project.getName(),
                project.getPackageName(),
                options,
                ProjectManifest.TRACKED,
                generatorContext.getRequestedFeatures().toSorted().unique().collect { String name -> requested(name, locked) },
                generatorContext.getExcludedFeatures().toSorted().unique().collect { String name -> new RequestedFeature(name) },
                [:],
                preset?.getName()).toBytes()
        // more repositories than the lock names are counted: they are in the build file, all of them
        List<LockedRepository> repositories = repositories(generatorContext.getRepositoryOverride())
        byte[] lockBytes = new ProjectLock(
                StateYaml.sha256(manifestBytes),
                ProjectLock.GENERATOR,
                VersionInfo.getVersion(),
                // which build of the version: two snapshots of one version generate from other sources
                VersionInfo.getGeneratorSha256(),
                grailsVersion,
                PackDescriptor.TEMPLATE_API,
                applicationType,
                options,
                ProjectLock.LINE_ENDINGS_OF_TEMPLATES,
                repositories.take(StateLimits.MAX_REPOSITORIES),
                Math.max(0, repositories.size() - StateLimits.MAX_REPOSITORIES),
                locked,
                [:],
                StateYaml.sha256(indexBytes)).toBytes()
        try {
            ProjectState.of(manifestBytes, lockBytes, indexBytes)
        } catch (ProjectStateException e) {
            throw new IllegalArgumentException("Cannot create the project because the record of how it is generated could not be read again: ${e.message}")
        }
        // every copy is committed, whatever the patterns of the application or of whoever commits leave out
        state << new Rendered(ProjectState.IGNORE, ProjectState.IGNORE_CONTENT.getBytes(StandardCharsets.UTF_8), false, false)
        state << new Rendered(ProjectState.ATTRIBUTES, '* -text\n'.getBytes(StandardCharsets.UTF_8), false, false)
        state << new Rendered(ProjectState.BASELINE_INDEX, indexBytes, false, false)
        state << new Rendered(ProjectState.LOCK, lockBytes, false, false)
        state << new Rendered(ProjectState.MANIFEST, manifestBytes, false, false)
        return state
    }

    /**
     * Removes the baseline that a generation which did not come to its end left in the directory, so
     * that the baseline has the files of this generation and no others. There is no manifest then,
     * which {@link #check(Collection)} saw to.
     */
    void clear() {
        if (!(outputHandler instanceof FileSystemOutputHandler)) {
            return
        }
        Path baseline = ((FileSystemOutputHandler) outputHandler).getApplicationDirectory().toPath().resolve(ProjectState.BASELINE)
        if (!Files.isDirectory(baseline, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        // a link in the baseline is removed, and what it leads to is left alone
        Files.walkFileTree(baseline, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure
                }
                Files.delete(directory)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private static RequestedFeature requested(String name, List<LockedFeature> locked) {
        // a feature of a pack that was taken by its coordinates was asked for by them
        LockedFeature feature = locked.find { LockedFeature each -> each.name == name }
        return new RequestedFeature(name, feature?.coordinates, feature?.version)
    }

    /**
     * @param feature a feature of an application
     * @return the feature as the lock of the application has it: where it is from, and the commands it
     * gives the command line
     * @since 9.0
     */
    static LockedFeature locked(Feature feature) {
        if (!(feature instanceof PackFeature)) {
            return new LockedFeature(feature.getName(), LockedFeature.FRAMEWORK)
        }
        Pack pack = ((PackFeature) feature).getPack()
        // the commands the feature gives the command line, which reads them from here
        List<LockedArtefact> commands = pack.getDescriptor().getArtefacts().collect { PackArtefact each -> LockedArtefact.of(each) }
        if (pack.isBundled()) {
            return new LockedFeature(feature.getName(), LockedFeature.BUNDLED_PACK, null, null, null, null, null, null, null, commands)
        }
        PackArtifact artifact = pack.getArtifact()
        if (artifact == null) {
            // the state is committed, and where a pack of a directory was read from is a path of the
            // machine it was generated on: the lock says what the pack was, by the digest of its content,
            // which is the same on every computer that has the same pack
            return new LockedFeature(feature.getName(), LockedFeature.PACK, null, null, null, null, null, null, pack.getContentSha256(), commands)
        }
        // a pack that was taken by the coordinates of its plugin: which artifact, and its very bytes.
        // The repository is said when it is an https address, as the repositories of the build are,
        // and not when it is a directory of this machine
        int version = artifact.coordinates.lastIndexOf(':')
        return new LockedFeature(feature.getName(), LockedFeature.PACK,
                // the rule of the pack for a repository that can be written into a project, and that of the state
                ProjectLock.recordableAddress(artifact.getRecordableRepository()),
                artifact.coordinates.substring(0, version), artifact.coordinates.substring(version + 1), artifact.sha256,
                // of the catalog that gave the coordinates for a name: its digest, and its address by the same rule
                artifact.catalogSha256 == null ? null : ProjectLock.recordableAddress(artifact.getRecordableCatalog()), artifact.catalogSha256,
                null, commands)
    }

    // the repositories that were given in place of those Grails gives a build, as the build file has them
    private static List<LockedRepository> repositories(String override) {
        List<LockedRepository> repositories = []
        for (String each : (override ?: '').split(';')*.trim().findAll { String given -> !given.isEmpty() }) {
            if (each == 'mavenLocal()') {
                repositories << new LockedRepository(LockedRepository.MAVEN_LOCAL)
            } else if (each == 'mavenCentral()') {
                repositories << new LockedRepository(LockedRepository.MAVEN_CENTRAL)
            } else if (GradleRepository.URL_SCHEME_PREFIX.matcher(each).matches() && !each.toLowerCase(Locale.ROOT).startsWith('file:')) {
                repositories << new LockedRepository(LockedRepository.REMOTE, ProjectLock.recordableAddress(each))
            } else {
                repositories << new LockedRepository(LockedRepository.LOCAL)
            }
        }
        return repositories
    }

    private String path(Template template) {
        String path = DefaultTemplateRenderer.resolvePath(template.getPath(), project.getProperties())
        // a/b, a//b and a/./b are one file
        return path.split('/').findAll { String step -> !step.isEmpty() && step != '.' }.join('/')
    }

    private static final class Rendered implements Template {

        final String path
        final byte[] content
        final boolean binary
        final boolean executable

        Rendered(String path, byte[] content, boolean binary, boolean executable) {
            this.path = path
            this.content = content
            this.binary = binary
            this.executable = executable
        }

        @Override
        String getPath() {
            return path
        }

        @Override
        boolean isBinary() {
            return binary
        }

        @Override
        boolean isExecutable() {
            return executable
        }

        @Override
        void write(OutputStream outputStream) throws IOException {
            outputStream.write(content)
        }
    }
}
