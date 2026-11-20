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
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.pack.PackFeature
import org.apache.grails.forge.core.state.BaselineIndex.BaselineFile
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest.RequestedFeature
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.io.OutputHandler
import org.grails.forge.template.DefaultTemplateRenderer
import org.grails.forge.template.Template
import org.grails.forge.options.Options
import org.grails.forge.util.VersionInfo
import java.nio.charset.StandardCharsets

/**
 * Writes the state of a project while it is generated. Every file of the application is rendered once,
 * here: the bytes that are written to the application are the bytes the baseline keeps and the index
 * has the digest of, also for a template that would not render the same a second time. A file is
 * binary in the index when its template says so or when its content is not text in UTF-8.
 *
 * <p>The baseline holds what the generator rendered. A file that is not written because the directory
 * already has one of its name, as {@code --inplace} allows, is in the baseline as the generator would
 * have written it: the file that is there is to the baseline what a file the user changed is.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectStateRecorder {

    private final ApplicationType applicationType
    private final Project project
    private final GeneratorContext generatorContext
    private final Map<String, Rendered> files = new TreeMap<>()

    ProjectStateRecorder(ApplicationType applicationType, Project project, GeneratorContext generatorContext) {
        this.applicationType = applicationType
        this.project = project
        this.generatorContext = generatorContext
    }

    /**
     * Before anything is written: the state has to be able to name every file of the application, and
     * there must be no state where it is written, which would be that of another generation.
     *
     * @param templates the files of the application
     * @param outputHandler what the application is written to
     * @throws IllegalArgumentException for an application whose state cannot be written
     */
    void check(Collection<Template> templates, OutputHandler outputHandler) {
        if ([ProjectState.MANIFEST, ProjectState.LOCK, ProjectState.BASELINE_INDEX].any { String file -> outputHandler.exists(file) }) {
            throw new IllegalArgumentException("Cannot create the project because the directory already has the state of a generated project in ${ProjectState.DIRECTORY}")
        }
        if (templates.size() > BaselineIndex.MAX_FILES) {
            throw new IllegalArgumentException("The features write ${templates.size()} files, and the state of a project can name ${BaselineIndex.MAX_FILES}")
        }
        for (Template template : templates) {
            String path = path(template)
            if (ProjectState.isStatePath(path)) {
                throw new IllegalArgumentException("The features write the file ${path}, and ${ProjectState.DIRECTORY} is where the state of the project is kept")
            }
            if (!BaselineIndex.isBaselinePath(path)) {
                throw new IllegalArgumentException("The features write the file ${path}, which is not a path the state of the project can name")
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
        if (rendered.content.length > ProjectState.MAX_BASELINE_FILE) {
            throw new IllegalArgumentException("The file ${rendered.path} has ${rendered.content.length} bytes, and a file of the baseline of a project can have ${ProjectState.MAX_BASELINE_FILE}")
        }
        files.put(rendered.path, rendered)
        // written under the path the template has, as it was before there was a state
        return new Rendered(template.getPath(), content, template.isBinary(), rendered.executable)
    }

    /**
     * @return the files of the state for the files that were recorded: the content of the baseline,
     * what tells Git to keep its bytes, its index, the lock and the manifest. They are written whether or not there is a file of the
     * name already.
     */
    List<Template> state() {
        List<Template> state = []
        List<BaselineFile> index = []
        for (Rendered file : files.values()) {
            state << new Rendered("${ProjectState.BASELINE_FILES}/${file.path}".toString(), file.content, file.binary, false)
            index << new BaselineFile(file.path, StateYaml.sha256(file.content), file.binary ? BaselineFile.BINARY : BaselineFile.TEXT, file.executable)
        }
        byte[] indexBytes = new BaselineIndex(index).toBytes()
        // what the application has: an option that was not given gives way to the features
        Options ofApplication = generatorContext.getApplicationOptions()
        GenerationOptions options = new GenerationOptions(generatorContext.getJdkVersion(), ofApplication.getServletImpl(),
                ofApplication.getGormImpl(), ofApplication.getDevelopmentReloading())
        String grailsVersion = VersionInfo.getGrailsVersion()
        byte[] manifestBytes = new ProjectManifest(
                grailsVersion,
                applicationType,
                project.getName(),
                project.getPackageName(),
                options,
                ProjectManifest.TRACKED,
                generatorContext.getRequestedFeatures().toSorted().unique().collect { String name -> new RequestedFeature(name) },
                generatorContext.getExcludedFeatures().toSorted().unique().collect { String name -> new RequestedFeature(name) },
                [:]).toBytes()
        List<Feature> applied = new ArrayList<>(generatorContext.getFeatures().getFeatures())
        applied.sort(FeatureContext.FEATURE_ORDER)
        byte[] lockBytes = new ProjectLock(
                StateYaml.sha256(manifestBytes),
                ProjectLock.GENERATOR,
                VersionInfo.getVersion(),
                grailsVersion,
                PackDescriptor.TEMPLATE_API,
                applicationType,
                options,
                generatorContext.getOperatingSystem(),
                ProjectLock.LINE_ENDINGS_OF_TEMPLATES,
                applied.collect { Feature feature -> locked(feature) },
                [:],
                StateYaml.sha256(indexBytes)).toBytes()
        if (manifestBytes.length > ProjectState.MAX_MANIFEST || lockBytes.length > ProjectState.MAX_LOCK || indexBytes.length > ProjectState.MAX_INDEX) {
            throw new IllegalArgumentException('The application has more features or files than the state of a project can name')
        }
        state << new Rendered(ProjectState.ATTRIBUTES, '* -text\n'.getBytes(StandardCharsets.UTF_8), false, false)
        state << new Rendered(ProjectState.BASELINE_INDEX, indexBytes, false, false)
        state << new Rendered(ProjectState.LOCK, lockBytes, false, false)
        state << new Rendered(ProjectState.MANIFEST, manifestBytes, false, false)
        return state
    }

    private static LockedFeature locked(Feature feature) {
        if (!(feature instanceof PackFeature)) {
            return new LockedFeature(feature.getName(), LockedFeature.FRAMEWORK)
        }
        Pack pack = ((PackFeature) feature).getPack()
        if (pack.isBundled()) {
            return new LockedFeature(feature.getName(), LockedFeature.BUNDLED_PACK)
        }
        PackArtifact artifact = pack.getArtifact()
        if (artifact == null) {
            // the state is committed, and where a pack of a directory was read from is a path of the
            // machine it was generated on: the lock says no more of it than that the feature is of a pack
            return new LockedFeature(feature.getName(), LockedFeature.PACK)
        }
        // a pack that was taken by the coordinates of its plugin: which artifact, and its very bytes.
        // The repository is said when it is one anybody can ask, not a directory of this machine
        int version = artifact.coordinates.lastIndexOf(':')
        return new LockedFeature(feature.getName(), LockedFeature.PACK,
                artifact.getRecordableRepository(),
                artifact.coordinates.substring(0, version), artifact.coordinates.substring(version + 1), artifact.sha256)
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
