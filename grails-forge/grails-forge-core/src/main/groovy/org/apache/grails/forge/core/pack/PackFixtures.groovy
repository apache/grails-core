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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.regex.Pattern
import org.apache.grails.forge.core.ForgeEnvironment
import org.grails.forge.ForgeCoreConfiguration
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext

/**
 * Generates applications with the feature pack of a directory selected, each into a directory of its
 * own: the fixtures the author of a pack has built before the pack is published. The Grails Gradle
 * plugin runs it, in a JVM of its own, on the pack of a plugin, and then builds what was generated.
 *
 * <p>An application is generated as the create commands generate one: by the generator of Forge, with
 * every feature of the framework that an application of the type has, and written by the output
 * handler that writes an application to a directory. The repositories of the generated builds are
 * those of the environment variable {@code GRAILS_REPO_URL}, as they are for the create commands. One
 * file is added to an application that has none of its kind: a settings file that names it, so that it
 * can be built where it is.</p>
 *
 * <p>A fixture is a name, a type of application, and optionally the options and further features of
 * the application. Without fixtures there is one for each type of application the descriptor of the
 * pack names, with the name of the type and no options. A fixture the generator refuses, such as one
 * of a type the pack is not for, is a result and is said as one; the other fixtures are generated.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackFixtures {

    /** The result of {@link #run} when every fixture was generated */
    public static final int GENERATED = 0

    /** The result of {@link #run} when the pack has problems or a fixture was refused, which is what it wrote */
    public static final int REFUSED = 1

    /** The result of {@link #run} when the fixtures could not be generated */
    public static final int NOT_GENERATED = 2

    /** What starts the line of a fixture that was generated, before its name */
    public static final String GENERATED_FIXTURE = 'generated '

    /** What starts the line of a fixture that was refused, before its name, a colon and why */
    public static final String REFUSED_FIXTURE = 'refused '

    /** What starts the line of a problem of the pack itself, for which no fixture was generated */
    public static final String PACK_PROBLEM = 'pack: '

    /**
     * What starts the last line {@link #run} writes when it got to its end, before {@code generated=}
     * and {@code refused=} with the numbers of fixtures: a run that ended without it did not.
     */
    public static final String SUMMARY = 'feature pack fixtures: '

    /** The most fixtures of one run */
    public static final int MAX_FIXTURES = 32

    /** The most bytes of the file that lists the fixtures */
    public static final int MAX_FIXTURES_BYTES = 64 * 1024

    /** The most features a fixture can add to the feature of the pack */
    public static final int MAX_FEATURES = 50

    private static final int MAX_NAME = 60
    private static final int MAX_LINE = 2000
    private static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final Pattern UNREADABLE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/)
    private static final String PROJECT = 'com.example.shop'
    private static final List<String> SETTINGS = ['settings.gradle', 'settings.gradle.kts'].asImmutable()
    private static final String USAGE = 'Give the directory of the feature pack, the directory to generate the fixtures in and, ' +
            'optionally, the file that lists the fixtures, and nothing else'

    private PackFixtures() {
    }

    /**
     * An application to generate with the feature of the pack.
     */
    static final class Fixture {

        /** The name of the fixture, which is the name of its directory */
        final String name

        final ApplicationType applicationType

        /** The options that were given, by {@code data}, {@code servlet}, {@code reloading} and {@code jdk} */
        final Map<String, String> options

        /** The features of the application besides that of the pack */
        final List<String> features

        Fixture(String name, ApplicationType applicationType, Map<String, String> options, List<String> features) {
            this.name = name
            this.applicationType = applicationType
            this.options = Collections.unmodifiableMap(new LinkedHashMap<String, String>(options))
            this.features = Collections.unmodifiableList(new ArrayList<String>(features))
        }
    }

    /**
     * What became of a fixture.
     */
    static final class Result {

        final String name

        /** Why the generator refused the fixture, on one line, or {@code null} for one that was generated */
        final String refusal

        Result(String name, String refusal) {
            this.name = name
            this.refusal = refusal
        }

        boolean isGenerated() {
            return refusal == null
        }
    }

    /**
     * The name of a type of application as a descriptor and the create commands write it.
     */
    static String nameOf(ApplicationType type) {
        return type.getName().replace('_', '-')
    }

    /**
     * The fixtures of a pack that names none: one for each type of application the pack is for, by the
     * name of the type.
     */
    static List<Fixture> defaults(PackDescriptor descriptor) {
        return ApplicationType.values()
                .findAll { ApplicationType type -> descriptor.applicationTypes.contains(type) }
                .collect { ApplicationType type -> new Fixture(nameOf(type), type, [:], []) }
    }

    /**
     * Reads the fixtures of a list: a fixture on a line, as its name, its type of application, and
     * then any of {@code data=}, {@code servlet=}, {@code reloading=}, {@code jdk=} and
     * {@code features=}, the features separated by commas, all separated by spaces. An empty line and
     * a line that starts with {@code #} are no fixtures.
     *
     * @throws IllegalArgumentException for a list that cannot be read as fixtures, with the line
     */
    static List<Fixture> parse(String text) {
        List<Fixture> fixtures = []
        Set<String> names = new HashSet<>()
        List<String> lines = text.readLines()
        for (int index = 0; index < lines.size(); index++) {
            String line = lines[index].trim()
            if (line.isEmpty() || line.startsWith('#')) {
                continue
            }
            String at = "Line ${index + 1} of the fixtures"
            if (fixtures.size() == MAX_FIXTURES) {
                throw new IllegalArgumentException("There are more than ${MAX_FIXTURES} fixtures")
            }
            List<String> words = line.split(/\s+/).toList()
            String name = words[0]
            if (name.length() > MAX_NAME || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("${at}: the name of a fixture is lower case letters, digits and single hyphens, starts " +
                        "with a letter and has at most ${MAX_NAME} characters, and this is ${FeatureSelectionException.shown(name)}")
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("${at}: there are two fixtures of the name ${name}")
            }
            if (words.size() < 2) {
                throw new IllegalArgumentException("${at}: the fixture ${name} has no type of application")
            }
            ApplicationType type = ApplicationType.values().find { ApplicationType each -> nameOf(each) == words[1] }
            if (type == null) {
                throw new IllegalArgumentException("${at}: the type of application of the fixture ${name} is one of " +
                        "${ApplicationType.values().collect { ApplicationType each -> nameOf(each) }}, and this is ${FeatureSelectionException.shown(words[1])}")
            }
            Map<String, String> options = new LinkedHashMap<>()
            List<String> features = []
            for (String word : words.drop(2)) {
                int equals = word.indexOf('=')
                String key = equals < 0 ? word : word.substring(0, equals)
                String value = equals < 0 ? '' : word.substring(equals + 1)
                if (key == 'features') {
                    features.addAll(value.split(',').findAll { String feature -> !feature.isEmpty() })
                    if (features.size() > MAX_FEATURES) {
                        throw new IllegalArgumentException("${at}: the fixture ${name} has more than ${MAX_FEATURES} features")
                    }
                } else if (key in ['data', 'servlet', 'reloading', 'jdk'] && !value.isEmpty()) {
                    if (options.put(key, value) != null) {
                        throw new IllegalArgumentException("${at}: the fixture ${name} has ${key} twice")
                    }
                } else {
                    throw new IllegalArgumentException("${at}: the fixture ${name} can have data=, servlet=, reloading=, jdk= and features=, " +
                            "each with a value, and this is ${FeatureSelectionException.shown(word)}")
                }
            }
            // read here, so that a value there is not is said before anything is generated
            optionsOf(name, options)
            fixtures << new Fixture(name, type, options, features)
        }
        if (fixtures.isEmpty()) {
            throw new IllegalArgumentException('The list of fixtures names no fixture')
        }
        return fixtures
    }

    /**
     * Generates the fixtures, each in the directory of its name below the output directory.
     *
     * @param generator the generator of Forge
     * @param pack the pack whose feature every fixture has
     * @param output the directory the directories of the fixtures are made in
     * @param fixtures the fixtures
     * @param console where the generator says what it says
     * @return what became of each fixture, in their order
     * @throws IllegalStateException when the directory of a fixture is there already
     * @throws IOException when an application could not be written
     */
    static List<Result> generate(ProjectGenerator generator, Pack pack, Path output, List<Fixture> fixtures, ConsoleOutput console) {
        for (Fixture fixture : fixtures) {
            if (Files.exists(output.resolve(fixture.name), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("The directory of the fixture ${fixture.name} is there already, and is not written over")
            }
        }
        Project project = NameUtils.parse(PROJECT)
        List<Result> results = []
        for (Fixture fixture : fixtures) {
            results << new Result(fixture.name, generate(generator, pack, project, output.resolve(fixture.name), fixture, console))
        }
        return results
    }

    // why the fixture was refused, or null
    private static String generate(ProjectGenerator generator, Pack pack, Project project, Path directory, Fixture fixture,
                                   ConsoleOutput console) {
        PackDescriptor descriptor = pack.descriptor
        if (!descriptor.applicationTypes.contains(fixture.applicationType)) {
            return line("The feature pack ${descriptor.name} is not for an application of the type ${nameOf(fixture.applicationType)}: " +
                    "its descriptor names ${descriptor.applicationTypes.collect { ApplicationType type -> nameOf(type) }}")
        }
        try {
            OperatingSystem operatingSystem = operatingSystem()
            Options options = optionsOf(fixture.name, fixture.options).withOperatingSystem(operatingSystem)
            List<String> features = [descriptor.name]
            features.addAll(fixture.features)
            GeneratorContext context = generator.createGeneratorContext(fixture.applicationType, project, options, operatingSystem,
                    features, console, [pack], Collections.<String>emptyList())
            if (!context.getFeatures().contains(descriptor.name)) {
                return line("The application was made without the feature ${descriptor.name} of the pack")
            }
            generator.generate(fixture.applicationType, project, new FileSystemOutputHandler(directory.toFile(), console), context)
            standAlone(directory, project)
            return null
        } catch (IllegalArgumentException e) {
            // a selection that cannot be had, a pack that cannot be used so, a template that fails:
            // nothing of an application is written before all of it was rendered
            return line(e.message ?: e.class.simpleName)
        }
    }

    /**
     * An application is generated without a settings file unless a feature adds one, and is then the
     * build of its directory, named after it. A fixture is in a directory of the name of the fixture,
     * usually below the build of the plugin, whose settings file Gradle would find and refuse the
     * application for. So a fixture without a settings file gets one, which says the name the
     * application was generated with and nothing else.
     */
    private static void standAlone(Path directory, Project project) {
        if (SETTINGS.every { String name -> !Files.exists(directory.resolve(name), LinkOption.NOFOLLOW_LINKS) }) {
            Files.writeString(directory.resolve(SETTINGS[0]), '// Written for the fixture, which is built where it is: the application was generated ' +
                    "without a settings file.\nrootProject.name = '${project.getName()}'\n".toString(), StandardCharsets.UTF_8)
        }
    }

    // as the create commands make them: an option that is given is a choice, and one that is not has a default
    private static Options optionsOf(String fixture, Map<String, String> given) {
        GormImpl data = null
        ServletImpl servlet = null
        DevelopmentReloading reloading = null
        JdkVersion jdk = JdkVersion.DEFAULT_OPTION
        Set<OptionName> names = EnumSet.noneOf(OptionName)
        for (Map.Entry<String, String> option : given.entrySet()) {
            String value = option.value
            switch (option.key) {
                case 'data':
                    data = GormImpl.parse(value)
                    check(fixture, option.key, value, data, GormImpl.values()*.name())
                    names.add(OptionName.DATA)
                    break
                case 'servlet':
                    servlet = ServletImpl.values().find { ServletImpl each -> each.name().equalsIgnoreCase(value) }
                    check(fixture, option.key, value, servlet, ServletImpl.values()*.name())
                    names.add(OptionName.SERVLET)
                    break
                case 'reloading':
                    reloading = DevelopmentReloading.values().find { DevelopmentReloading each -> each.name().equalsIgnoreCase(value) }
                    check(fixture, option.key, value, reloading, DevelopmentReloading.values()*.name())
                    names.add(OptionName.RELOADING)
                    break
                default:
                    jdk = JdkVersion.values().find { JdkVersion each -> String.valueOf(each.majorVersion()) == value }
                    check(fixture, option.key, value, jdk, JdkVersion.values().collect { JdkVersion each -> String.valueOf(each.majorVersion()) })
            }
        }
        return new Options(reloading, data, servlet, jdk, OperatingSystem.DEFAULT).withGiven(names)
    }

    private static void check(String fixture, String option, String value, Object found, List<String> values) {
        if (found == null) {
            throw new IllegalArgumentException("The fixture ${fixture} has ${option}=${FeatureSelectionException.shown(value)}, and " +
                    "${option} is one of ${values.collect { String each -> each.toLowerCase(Locale.ROOT) }}")
        }
    }

    // of the machine the fixtures are generated and built on, as the create commands take it
    private static OperatingSystem operatingSystem() {
        String name = System.getProperty('os.name', '').toLowerCase(Locale.ENGLISH)
        if (name.contains('mac') || name.contains('darwin')) {
            return System.getProperty('os.arch', '').toLowerCase(Locale.ENGLISH) == 'aarch64' ? OperatingSystem.MACOS_ARCH64 : OperatingSystem.MACOS
        }
        if (name.contains('linux')) {
            return OperatingSystem.LINUX
        }
        if (name.contains('win')) {
            return OperatingSystem.WINDOWS
        }
        return name.contains('sunos') || name.contains('solaris') ? OperatingSystem.SOLARIS : null
    }

    // one line, of bounded length, without what acts on a terminal or is markup of a command line
    private static String line(String text) {
        String first = text.readLines().find { String each -> !each.trim().isEmpty() } ?: ''
        String cut = first.length() > MAX_LINE * 2 ? first.substring(0, MAX_LINE * 2) : first
        String safe = UNREADABLE.matcher(cut).replaceAll(' ').replace('@|', '@ |').trim()
        int count = safe.codePointCount(0, safe.length())
        return first.length() == cut.length() && count <= MAX_LINE ? safe : safe.substring(0, safe.offsetByCodePoints(0, Math.min(MAX_LINE, count))) + '...'
    }

    /**
     * Generates the fixtures of a pack and says what became of each, a fixture on a line:
     * {@link #GENERATED_FIXTURE} and its name, or {@link #REFUSED_FIXTURE}, its name, a colon and why.
     * A pack that cannot be read has its problems said instead, each after {@link #PACK_PROBLEM}. The
     * last line is {@link #SUMMARY} and then {@code generated=} and {@code refused=} with the numbers
     * of fixtures, so that a run that was ended before it was done, by a template for one, is not taken
     * for one that generated what it was asked for.
     *
     * @param args the directory of the pack, the directory to generate in, which need not be there,
     * and optionally the file that lists the fixtures as {@link #parse} reads them
     * @param out where the lines of the fixtures go, and nothing else
     * @param err where it is said why the fixtures could not be generated, and what the generator says
     * @return {@link #GENERATED}, {@link #REFUSED} or {@link #NOT_GENERATED}
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length < 2 || args.length > 3 || args.any { String arg -> !arg }) {
            err.println(USAGE)
            return NOT_GENERATED
        }
        List<String> written = []
        List<Result> results = []
        try {
            Path directory = Path.of(args[0])
            Path output = Path.of(args[1])
            List<Fixture> listed = args.length == 3 ? parse(fixturesOf(Path.of(args[2]))) : null
            Pack pack = null
            try {
                pack = new PackLoader().fromDirectory(directory)
            } catch (PackDescriptorException e) {
                e.problems.each { String problem -> written << PACK_PROBLEM + line(problem) }
            }
            if (pack != null) {
                new AnnotationConfigApplicationContext(ForgeCoreConfiguration).withCloseable { AnnotationConfigApplicationContext context ->
                    results = generate(context.getBean(ProjectGenerator), pack, output, listed ?: defaults(pack.descriptor), console(err))
                }
            }
        } catch (InvalidPathException ignored) {
            err.println('A directory or the file of the fixtures is not a path')
            return NOT_GENERATED
        } catch (VirtualMachineError e) {
            throw e
        } catch (Throwable e) {
            err.println("The fixtures of the feature pack could not be generated: ${line(e instanceof IllegalArgumentException || e instanceof IllegalStateException ? (e.message ?: e.toString()) : e.toString())}")
            return NOT_GENERATED
        }
        for (Result result : results) {
            written << (result.generated ? GENERATED_FIXTURE + result.name : "${REFUSED_FIXTURE}${result.name}: ${result.refusal}".toString())
        }
        int generated = results.count { Result result -> result.generated } as int
        written.each { String each -> out.println(each) }
        out.println("${SUMMARY}generated=${generated} refused=${results.size() - generated}")
        return generated > 0 && generated == results.size() ? GENERATED : REFUSED
    }

    private static String fixturesOf(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException('The file of the fixtures is not a file')
        }
        byte[] bytes = Files.newInputStream(file).withCloseable { InputStream input -> input.readNBytes(MAX_FIXTURES_BYTES + 1) }
        if (bytes.length > MAX_FIXTURES_BYTES) {
            throw new IllegalArgumentException("The file of the fixtures has more than ${MAX_FIXTURES_BYTES} bytes")
        }
        return new String(bytes, StandardCharsets.UTF_8)
    }

    private static ConsoleOutput console(PrintStream err) {
        return new ConsoleOutput() {

            @Override
            void out(String message) {
                err.println(message)
            }

            @Override
            void err(String message) {
                err.println(message)
            }

            @Override
            void warning(String message) {
                err.println(message)
            }

            @Override
            boolean showStacktrace() {
                return false
            }

            @Override
            boolean verbose() {
                return false
            }
        }
    }

    /**
     * Generates the fixtures of the pack as {@link #run} does, writes what it writes to the standard
     * output, and ends the JVM with {@link #GENERATED}, {@link #REFUSED} or {@link #NOT_GENERATED}.
     * What a template or the generator writes to the standard output goes to the standard error, so
     * that what became of the fixtures is all there is to read.
     */
    static void main(String[] args) {
        // Forge's own environment, not the one of the pack's project, which the Gradle build that starts
        // this may have set
        ForgeEnvironment.useProduction()
        PrintStream out = System.out
        System.setOut(System.err)
        int result = run(args, out, System.err)
        out.flush()
        System.exit(result)
    }
}
