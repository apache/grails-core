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
package grails.doc.gradle

import javax.inject.Inject

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

/**
 * Gradle task for generating a gdoc-based HTML user guide.
 *
 * <p>The guide is rendered in a forked worker process: AsciidoctorJ boots a JRuby runtime,
 * whose burst of allocation would otherwise land in the Gradle daemon on top of everything
 * else the build is holding.</p>
 *
 * <p>A process-isolation worker is pooled, so it is stopped when the build session ends, not
 * when this task's action returns - it can still be resident while the aggregate groovydoc
 * runs. It is out of the daemon, which is the point, and it is not carried over into the next
 * build. A worker rather than a plain forked JVM because the guide is build logic: it runs on
 * the Groovy that Gradle embeds, which a worker inherits and a bare JVM would have to pin.</p>
 */
@CacheableTask
class PublishGuideTask extends DefaultTask {

    @Optional
    @Input
    final Property<String> language

    @Optional
    @Input
    final Property<String> sourceRepo

    @Optional
    @Input
    final MapProperty<String, Object> properties

    @Internal
    // Used to relativize file paths in getRelativizedPropertiesWithFilePaths()
    final DirectoryProperty rootProjectDir

    @Internal
    // Properties in this map contain file paths. @internal allows to exclude it from the cache key, instead the getRelativizedPropertiesWithFilePaths() is considered as @Input to enable cache relocatability
    final MapProperty<String, File> propertiesWithFilePaths

    @Optional
    @Input
    final Property<Boolean> asciidoc

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    final ConfigurableFileCollection propertiesFiles

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty sourceDir

    @Optional
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty resourcesDir

    /**
     * Fully qualified names of extra Radeox macros to register. Names rather than instances,
     * because the guide is rendered in a separate process.
     */
    @Optional
    @Input
    final ListProperty<String> macros

    @OutputDirectory
    final DirectoryProperty targetDir

    /**
     * Maximum heap of the worker process the guide is rendered in. The English guide settles
     * under a gigabyte resident; the default leaves room for it to grow. A
     * {@code guideMaxHeapSize} project property beats whatever is set here - see
     * {@link #resolveMaxHeapSize} - so a guide that will not fit can be got moving from the
     * command line.
     */
    @Internal
    final Property<String> maxHeapSize

    /**
     * Whether Ant's own INFO messages are printed. Gradle routed them to its hidden INFO level
     * when the guide ran in the daemon; nothing routes them from a worker process, so they are
     * off unless the build asked for INFO logging.
     */
    @Internal
    final Property<Boolean> verboseAnt

    private final WorkerExecutor workerExecutor
    private final Provider<String> maxHeapSizeOverride

    @Inject
    PublishGuideTask(ObjectFactory objects, Project project, WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor
        maxHeapSizeOverride = project.providers.gradleProperty('guideMaxHeapSize')
        language = objects.property(String).convention(null as String)
        sourceRepo = objects.property(String)
        properties = objects.mapProperty(String, Object).convention([:])
        rootProjectDir = objects.directoryProperty().convention(project.rootProject.layout.projectDirectory)
        propertiesWithFilePaths = objects.mapProperty(String, File).convention([:])
        asciidoc = objects.property(Boolean).convention(true)
        propertiesFiles = objects.fileCollection()
        sourceDir = objects.directoryProperty().convention(project.layout.projectDirectory.dir('src'))
        resourcesDir = objects.directoryProperty().convention(project.layout.projectDirectory.dir('resources'))
        macros = objects.listProperty(String).convention([])
        targetDir = objects.directoryProperty().convention(project.layout.buildDirectory.dir('docs'))
        maxHeapSize = objects.property(String).convention('1500m')
        verboseAnt = objects.property(Boolean).convention(project.provider { logger.infoEnabled })
        group = 'documentation'
    }

    @Optional
    @Input
    Map<String, String> getRelativizedPropertiesWithFilePaths() {
        return propertiesWithFilePaths.get().collectEntries { key, file ->
            return [key, rootProjectDir.get().getAsFile().toPath().relativize(file.toPath()).toString()]
        }
    }

    /**
     * A {@code guideMaxHeapSize} project property beats whatever the build script set, the
     * same way {@code groovydocMaxHeapSize} does for groovydoc. A convention would be the
     * other way round: it only applies while nothing has been set explicitly.
     */
    protected String resolveMaxHeapSize() {
        maxHeapSizeOverride.getOrElse(maxHeapSize.get())
    }

    @TaskAction
    void publishGuide() {
        // Everything is read into locals first. Both the fork options and the work parameters
        // have members of their own named like this task's properties - maxHeapSize,
        // properties - and inside the configuration closures those would win.
        String workerHeap = resolveMaxHeapSize()
        String languageValue = this.language.getOrNull()
        String sourceRepoValue = this.sourceRepo.getOrNull()
        Boolean asciidocValue = this.asciidoc.get()
        Map<String, Object> engineProperties = this.properties.get()
        Map<String, File> filePathProperties = this.propertiesWithFilePaths.get()
        FileCollection propertiesFileValues = this.propertiesFiles
        Directory sourceDirValue = this.sourceDir.get()
        Directory resourcesDirValue = this.resourcesDir.get()
        Directory targetDirValue = this.targetDir.get()
        List<String> macroNames = this.macros.get()
        Boolean verboseAntValue = this.verboseAnt.get()

        WorkQueue queue = workerExecutor.processIsolation { spec ->
            spec.forkOptions { options -> options.setMaxHeapSize(workerHeap) }
        }
        queue.submit(PublishGuideWorkAction) { PublishGuideWorkParameters params ->
            params.language.set(languageValue)
            params.sourceRepo.set(sourceRepoValue)
            params.asciidoc.set(asciidocValue)
            params.properties.set(engineProperties)
            params.propertiesWithFilePaths.set(filePathProperties)
            params.propertiesFiles.from(propertiesFileValues)
            params.sourceDir.set(sourceDirValue)
            params.resourcesDir.set(resourcesDirValue)
            params.targetDir.set(targetDirValue)
            params.macroClassNames.set(macroNames)
            params.verboseAnt.set(verboseAntValue)
        }
    }
}

