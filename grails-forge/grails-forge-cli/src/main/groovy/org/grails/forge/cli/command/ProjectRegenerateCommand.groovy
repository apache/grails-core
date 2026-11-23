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
package org.grails.forge.cli.command

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.artefact.ProjectFiles
import org.apache.grails.forge.cli.artefact.ProjectOutputHandler
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.OptionRelation
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.pack.PackArtifactException
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.apache.grails.forge.cli.pack.RecordedPackOptions
import org.apache.grails.forge.core.change.FeatureAddition
import org.apache.grails.forge.core.change.ProjectChangeException
import org.apache.grails.forge.core.change.RegeneratedProject
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectState
import org.apache.grails.forge.core.state.ProjectStateException
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.OutputFailedException
import org.grails.forge.template.Template
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.charset.StandardCharsets
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.stream.Stream

/**
 * Generates the application in the current directory again from the record the generator left under
 * {@code .grails}: into another directory, or only to say which files of the project are not what
 * the generator makes. The project itself is not changed.
 *
 * @since 9.0
 */
@CommandLine.Command(name = ProjectRegenerateCommand.NAME, description = [
        'Generates the application in the current directory again as its record says it was generated, and changes nothing in it.',
        'With --output the application is written to another directory, whose record is then that of this one. With --diff nothing is written, and the ' +
                'files of this application that are not what was generated are named: those that were added, removed and changed.'])
@Component
@Scope('prototype')
@CompileStatic
class ProjectRegenerateCommand extends BaseCommand implements Callable<Integer>, ProjectCommand, OptionRelation.Declared {

    public static final String NAME = 'regenerate'

    private static final String IGNORE_FILE = '.gitignore'
    private static final String REPOSITORY = '.git'

    // of the directory of the state, what the generator writes there; the rest is what commands leave on this computer
    private static final List<String> RECORD = [ProjectState.MANIFEST, ProjectState.LOCK, ProjectState.ATTRIBUTES, ProjectState.IGNORE]

    @CommandLine.Option(names = ['-o', '--output'], paramLabel = 'DIRECTORY',
            description = 'The directory to write the application to, which is made when it is not there. One that has anything in it is refused without --force.')
    File output

    @CommandLine.Option(names = ['--diff'],
            description = 'Write nothing, and name the files of the application that are not those it was generated with. What the .gitignore ' +
                    'it was generated with leaves out is not looked at, and neither is .git. Ends with 1 when there are such files.')
    boolean diff

    @CommandLine.Mixin
    RecordedPackOptions packOptions = new RecordedPackOptions()

    @CommandLine.Option(names = ['-f', '--force'], description = 'Whether to write into a directory that has files, and to overwrite those the application has')
    boolean overwrite

    @CommandLine.Option(names = ['--json'], description = 'With --diff: write the files that are not those the application was generated with as one JSON ' +
            'document for a tool to read, and nothing else, with the outcome of the comparison and, when there is none, the reason.')
    boolean json

    @Override
    List<OptionRelation> getOptionRelations() {
        // what call checks first
        return [OptionRelation.exclusive(true, '--output', '--diff'), OptionRelation.requires('--force', '--output'), OptionRelation.requires('--json', '--diff')]
    }

    // asked of the Forge application when the command first needs it
    private final Supplier<ProjectGenerator> projectGeneratorSource

    @Autowired
    ProjectRegenerateCommand(ProjectGenerator projectGenerator) {
        this.projectGeneratorSource = { projectGenerator } as Supplier<ProjectGenerator>
    }

    /** On a Forge application that is started when the command first needs it. */
    ProjectRegenerateCommand(ForgeContext context) {
        this.projectGeneratorSource = { context.get().getBean(ProjectGenerator) } as Supplier<ProjectGenerator>
    }

    @Override
    Integer call() throws Exception {
        if ((output == null) == !diff) {
            throw new CommandLine.ParameterException(spec.commandLine(), 'Give --output with the directory to write the application to, or --diff to name the files ' +
                    'of the application that are not as generated: one of the two')
        }
        if (overwrite && diff) {
            throw new CommandLine.ParameterException(spec.commandLine(), '--force is for --output: --diff writes nothing')
        }
        if (json && !diff) {
            throw new CommandLine.ParameterException(spec.commandLine(), '--json writes what --diff says as one JSON document: give it with --diff')
        }
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        if (json) {
            return document(current)
        }
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            err(search.none('project regenerate'))
            return 1
        }
        Path directory = search.project.directory
        if (!hasRecord(directory)) {
            err(ProjectShowCommand.noRecord(spec))
            return 1
        }
        try {
            // looked at before the application is generated, which takes a moment
            Path target = diff ? null : target(current, directory)
            List<Pack> packs = packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), { String line -> out("@|blue ||@ ${GradleRunner.line(line)}") } as Consumer<String>,
                    { String line -> warning(GradleRunner.line(line)) } as Consumer<String>)
            RegeneratedProject regenerated = new FeatureAddition(projectGeneratorSource.get()).regenerate(directory, getOperatingSystem(), packs)
            return diff ? compare(directory, regenerated) : write(directory, target, regenerated)
        } catch (ProjectStateException | ProjectChangeException | IllegalArgumentException | PackArtifactException e) {
            // a record that another version wrote is moved to this one by a command that is named
            err(GradleRunner.line(ProjectMigrateCommand.withMove(spec, directory, e)))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
    }

    private static boolean hasRecord(Path directory) {
        return ProjectState.exists(directory) || Files.exists(directory.resolve(ProjectState.LOCK), LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(directory.resolve(ProjectState.BASELINE_INDEX), LinkOption.NOFOLLOW_LINKS)
    }

    /**
     * What {@code --diff} says, as one JSON document and nothing else. The exit code is the one the
     * command ends with without {@code --json}: 0 when every file is as generated, and 1 otherwise.
     */
    private Integer document(Path current) {
        ConsoleProject.Search search = ConsoleProject.search(current)
        if (search.project == null) {
            return written(ChangeDocument.ofRegenerate(null).noProject(search.untrusted, search.none('project regenerate')))
        }
        Path directory = search.project.directory
        ChangeDocument document = ChangeDocument.ofRegenerate(directory)
        if (!hasRecord(directory)) {
            return written(document.ended(ChangeDocument.INVALID_RECORD, ChangeDocument.NO_RECORD, ProjectShowCommand.noRecord(spec)))
        }
        RegeneratedProject regenerated
        try {
            // the document is all that is written: which jar of a pack is fetched again is not said
            List<Pack> packs = packOptions.packs(directory, createPackArtifacts(), grailsRepoUrl(), { String line -> } as Consumer<String>,
                    { String line -> } as Consumer<String>)
            regenerated = new FeatureAddition(projectGeneratorSource.get()).regenerate(directory, getOperatingSystem(), packs)
        } catch (Exception e) {
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return written(document.ended(e, ProjectMigrateCommand.withMove(spec, directory, e)))
        }
        try {
            return written(document.compared(differences(directory, regenerated)))
        } catch (IOException e) {
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return written(document.ended(ChangeDocument.FAILED, ProjectChangeException.UNREADABLE_FILE, "The files of the project cannot be read: ${unread(directory, e)}"))
        }
    }

    private int written(ChangeDocument document) {
        // written as it is: a document is not markup of the command line
        spec.commandLine().getOut().println(document.toJson())
        return document.outcome == ChangeDocument.NO_CHANGES ? 0 : 1
    }

    /**
     * @return what fetches the jar of a plugin the application has the feature pack of
     */
    protected PackArtifacts createPackArtifacts() {
        return new PackArtifacts()
    }

    /**
     * @return the repositories of the build of the application, which are asked for such a jar when
     * the command names none
     */
    protected String grailsRepoUrl() {
        return System.getenv('GRAILS_REPO_URL')
    }

    // where the application is written to: no directory that has anything, unless that was asked for, and never the project
    private Path target(Path current, Path project) {
        Path target = current.resolve(output.toPath()).normalize()
        if (Files.isSymbolicLink(target) || (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("${GradleRunner.line(target)} is not a directory, so the application is not written there")
        }
        Path real = project.toRealPath()
        // of a directory that is not there yet, the one above it says where it will be
        Path there = target
        while (there != null && !Files.exists(there, LinkOption.NOFOLLOW_LINKS)) {
            there = there.getParent()
        }
        Path realTarget = there == null ? target : there.toRealPath().resolve(there.relativize(target))
        if (real.startsWith(realTarget)) {
            throw new IllegalArgumentException("${GradleRunner.line(target)} is the directory of the project or one above it: the application is generated again " +
                    'into another directory, and the project is not changed')
        }
        if (!overwrite && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            boolean empty
            try (Stream<Path> entries = Files.list(target)) {
                empty = !entries.findAny().isPresent()
            }
            if (!empty) {
                throw new IllegalArgumentException("${GradleRunner.line(target)} already exists and is not empty. Run again with -f to overwrite.")
            }
        }
        return target
    }

    private Integer write(Path project, Path target, RegeneratedProject regenerated) {
        String written = null
        int before = 0
        try {
            Files.createDirectories(target)
            // every file can be written, or none is
            Map<String, String> refusals = ProjectFiles.refusals(target.toFile(), regenerated.paths)
            if (refusals) {
                err(refusals.values().first())
                return 1
            }
            ProjectOutputHandler handler = new ProjectOutputHandler(target.toFile(), this)
            for (String path : regenerated.paths) {
                written = path
                handler.write(path, new Regenerated(path, regenerated.content(path), regenerated.isExecutable(path)))
                before++
            }
            written = null
            String unlike = unlike(project, target)
            if (unlike != null) {
                err("The application was written to ${GradleRunner.line(target)}, and its record is not that of the project: ${unlike}")
                return 1
            }
        } catch (IOException e) {
            // the files are written one by one, and those before the one that failed stay: the directory then has the record of an
            // application and not all of its files, which is said, so that it is not taken for the application
            err((written == null ? 'The record of the application that was written could not be compared with that of the project' :
                    "Cannot write ${FeatureSelectionException.shown(written)}") + ": ${e instanceof ProjectOutputHandler.Refused ? e.getMessage() : OutputFailedException.reason(e)}" +
                    (written == null ? '' : before == 0 ? '. Nothing was written' :
                            ". The application at ${GradleRunner.line(target)} is not complete: ${before} of ${regenerated.paths.size()} files were written before it"))
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
        out("@|blue ||@ Application generated again at ${GradleRunner.line(target)}: ${regenerated.paths.size()} files, and its record under " +
                "${ProjectState.DIRECTORY} is that of the project")
        return 0
    }

    /**
     * Compares what the generator writes under {@code .grails} in the two directories, file by file
     * and byte by byte, in the order of the paths.
     *
     * @return what is wrong with the first file that is not the same in both, or {@code null}
     */
    private static String unlike(Path project, Path target) throws IOException {
        Set<String> paths = new TreeSet<>(record(project))
        paths.addAll(record(target))
        for (String path : paths) {
            Path ours = resolved(project, path)
            Path theirs = resolved(target, path)
            if (!Files.isRegularFile(theirs, LinkOption.NOFOLLOW_LINKS)) {
                return "${FeatureSelectionException.shown(path)} is a file of the project only"
            }
            if (!Files.isRegularFile(ours, LinkOption.NOFOLLOW_LINKS)) {
                return "${FeatureSelectionException.shown(path)} is a file of the directory that was written to only"
            }
            // another build of the same version that generates the same files writes the same lock but for its own identity
            if (Files.mismatch(ours, theirs) >= 0 && !(path == ProjectState.LOCK && ProjectLock.sameButForTheBuild(Files.readAllBytes(ours), Files.readAllBytes(theirs)))) {
                return "${FeatureSelectionException.shown(path)} differs"
            }
        }
        return null
    }

    // the files of the record a directory has: the four beside the baseline, and everything of the baseline
    private static List<String> record(Path directory) throws IOException {
        List<String> paths = RECORD.findAll { String path -> Files.exists(resolved(directory, path), LinkOption.NOFOLLOW_LINKS) }
        Path baseline = resolved(directory, ProjectState.BASELINE)
        if (Files.isDirectory(baseline, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> files = Files.walk(baseline)) {
                files.filter { Path file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) }
                        .forEach { Path file -> paths.add(directory.relativize(file).join('/')) }
            }
        }
        return paths
    }

    private static Path resolved(Path directory, String path) {
        Path resolved = directory
        for (String step : path.split('/')) {
            resolved = resolved.resolve(step)
        }
        return resolved
    }

    private Integer compare(Path project, RegeneratedProject regenerated) {
        Map<String, String> unlike
        try {
            unlike = differences(project, regenerated)
        } catch (IOException e) {
            err("The files of the project cannot be read: ${unread(project, e)}")
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            return 1
        }
        if (unlike.isEmpty()) {
            out('@|blue ||@ Every file of the application is as it was generated, and it has no others')
            return 0
        }
        for (Map.Entry<String, String> each : unlike.entrySet()) {
            // the name of a file is not markup of this command
            spec.commandLine().getOut().println("${each.value.padRight(8)} ${each.key.replaceAll(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/, '?')}")
        }
        Map<String, Integer> counted = unlike.values().countBy { String kind -> kind }
        out("@|blue ||@ ${unlike.size()} ${unlike.size() == 1 ? 'file is' : 'files are'} not as the application was generated: " +
                ['added', 'removed', 'changed'].findAll { String kind -> counted.containsKey(kind) }.collect { String kind -> "${counted.get(kind)} ${kind}" }.join(', '))
        return 1
    }

    /**
     * The files of the project that are not those it was generated with, sorted by their paths in
     * the project: {@code added}, {@code removed} or {@code changed}. What the {@code .gitignore} the
     * application was generated with leaves out is not looked at, and neither is {@code .git}.
     *
     * @throws IOException when a directory of the project cannot be read
     */
    private static Map<String, String> differences(Path project, RegeneratedProject regenerated) throws IOException {
        byte[] ignore = regenerated.content(IGNORE_FILE)
        IgnoreRules rules = new IgnoreRules(ignore == null ? '' : new String(ignore, StandardCharsets.UTF_8))
        Set<String> has = new TreeSet<>()
        Files.walkFileTree(project, new SimpleFileVisitor<Path>() {

            @Override
            FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                String path = project.relativize(directory).join('/')
                return !path.isEmpty() && (path == REPOSITORY || rules.ignores(path, true)) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                // a link is what it is and is not followed; the file of a worktree that says where its repository is is no file of the application
                String path = project.relativize(file).join('/')
                if (path != REPOSITORY && !rules.ignores(path, false)) {
                    has.add(path)
                }
                return FileVisitResult.CONTINUE
            }
        })
        Map<String, String> unlike = new TreeMap<>()
        for (String path : regenerated.paths) {
            if (rules.ignoresWithin(path)) {
                continue
            }
            if (!has.remove(path)) {
                unlike.put(path, 'removed')
            } else if (!same(project, regenerated, path)) {
                unlike.put(path, 'changed')
            }
        }
        for (String path : has) {
            unlike.put(path, 'added')
        }
        return unlike
    }

    // What could not be read, by its path in the project where the file system names one in it: what it says besides is of writing
    private static String unread(Path project, IOException failure) {
        String named = failure instanceof FileSystemException ? ((FileSystemException) failure).getFile() : null
        try {
            Path file = named == null ? null : Path.of(named)
            if (file != null && file.startsWith(project) && file != project) {
                return "${FeatureSelectionException.shown(project.relativize(file).join('/'))} cannot be opened"
            }
        } catch (InvalidPathException ignored) {
            // said without a path, below
        }
        return failure instanceof AccessDeniedException ? 'a directory of it cannot be opened' : OutputFailedException.reason(failure)
    }

    // a file of the baseline as the state compares it, which allows a clone the line endings of its platform; another byte by byte
    private static boolean same(Path project, RegeneratedProject regenerated, String path) {
        if (regenerated.state.baseline.find(path) != null) {
            return regenerated.state.isAsGenerated(project, path)
        }
        Path file = resolved(project, path)
        try {
            if (path == ProjectState.LOCK) {
                // the lock of the record is what was generated when another build of the version, that generates the same, would write it but for its identity
                return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && ProjectLock.sameButForTheBuild(Files.readAllBytes(file), regenerated.content(path))
            }
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == regenerated.content(path).length &&
                    Arrays.equals(Files.readAllBytes(file), regenerated.content(path))
        } catch (IOException ignored) {
            return false
        }
    }

    /** A file as it was generated. */
    private static final class Regenerated implements Template {

        private final String path
        private final byte[] content
        private final boolean executable

        Regenerated(String path, byte[] content, boolean executable) {
            this.path = path
            this.content = content
            this.executable = executable
        }

        @Override
        String getPath() {
            return path
        }

        @Override
        boolean isBinary() {
            return true
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

    /**
     * What a {@code .gitignore} at the root of a project leaves out, for the patterns the generator
     * writes and the common ones: a name, a path from the root, {@code *}, {@code ?} and {@code **},
     * a trailing {@code /} for a directory, and {@code !} to take back what an earlier line leaves
     * out. The last line that matches decides, and nothing below a directory that is left out is
     * looked at, as Git does it. A pattern with a class of characters matches nothing.
     */
    static final class IgnoreRules {

        private static final int MAX_LINES = 2000
        private static final int MAX_LINE = 1000

        private final List<Pattern> patterns = []
        private final List<Boolean> negated = []
        private final List<Boolean> directories = []

        IgnoreRules(String text) {
            for (String each : text.split('\n', MAX_LINES + 1).toList().take(MAX_LINES)) {
                String line = each.endsWith('\r') ? each.substring(0, each.length() - 1) : each
                line = line.replaceAll(/[ \t]+$/, '')
                if (line.isEmpty() || line.startsWith('#') || line.length() > MAX_LINE || line.contains('[') || line.contains('\\')) {
                    continue
                }
                boolean negates = line.startsWith('!')
                String pattern = negates ? line.substring(1) : line
                boolean directory = pattern.endsWith('/')
                pattern = directory ? pattern.substring(0, pattern.length() - 1) : pattern
                boolean fromRoot = pattern.contains('/')
                pattern = pattern.startsWith('/') ? pattern.substring(1) : pattern
                if (pattern.isEmpty()) {
                    continue
                }
                patterns << Pattern.compile((fromRoot ? '' : '(?:.*/)?') + expression(pattern))
                negated << negates
                directories << directory
            }
        }

        private static String expression(String pattern) {
            StringBuilder expression = new StringBuilder()
            List<String> steps = pattern.split('/', -1).toList()
            for (int i = 0; i < steps.size(); i++) {
                String step = steps[i]
                boolean last = i == steps.size() - 1
                if (step == '**') {
                    // any number of directories, none among them; at the end, everything below
                    expression.append(last ? '.*' : '(?:.*/)?')
                    continue
                }
                for (String part : step.split(/(?<=[*?])|(?=[*?])/)) {
                    expression.append(part == '*' ? '[^/]*' : part == '?' ? '[^/]' : part.isEmpty() ? '' : Pattern.quote(part))
                }
                if (!last) {
                    expression.append('/')
                }
            }
            return expression.toString()
        }

        /**
         * @param path the path of a file or a directory, with {@code /} between its steps
         * @param directory whether it is a directory
         * @return whether the path itself is left out; what is above it is not asked
         */
        boolean ignores(String path, boolean directory) {
            return decides(path, directory) ?: false
        }

        /**
         * @param path the path of a file or a directory, with {@code /} between its steps
         * @param directory whether it is a directory
         * @return whether the last line that matches the path leaves it out, or takes it back;
         * {@code null} when no line matches it, so that a {@code .gitignore} further up decides
         * @since 9.0
         */
        Boolean decides(String path, boolean directory) {
            Boolean ignored = null
            for (int i = 0; i < patterns.size(); i++) {
                if ((directory || !directories[i]) && patterns[i].matcher(path).matches()) {
                    ignored = !negated[i]
                }
            }
            return ignored
        }

        /**
         * @param path the path of a file, with {@code /} between its steps
         * @return whether the file is left out, by itself or with a directory above it
         */
        boolean ignoresWithin(String path) {
            int from = path.indexOf('/')
            while (from >= 0) {
                if (ignores(path.substring(0, from), true)) {
                    return true
                }
                from = path.indexOf('/', from + 1)
            }
            return ignores(path, false)
        }
    }
}
