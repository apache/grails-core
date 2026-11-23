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
import java.nio.file.InvalidPathException
import java.nio.file.Path
import org.apache.grails.forge.core.ForgeEnvironment
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile
import org.grails.forge.util.VersionInfo

/**
 * Checks the feature pack of a directory the way Forge reads it, without generating an application:
 * for the author of a pack, before it is published. The Grails Gradle plugin runs it, in a JVM of its
 * own, on the pack of a plugin.
 *
 * <p>A pack passes when {@link PackLoader#fromDirectory} reads it, when its descriptor says it is for
 * this Grails and this Forge, and when each of its templates can be compiled and can be rendered for
 * the applications of {@link PackModel#samples}. Rendering a template runs it, as generating an
 * application does: this is for a pack whose author runs the check. Nothing is written anywhere.</p>
 *
 * <p>What a template does only for an application the samples are not like, such as one with a feature
 * the pack does not require, is not run and so not checked. A descriptor that cannot be read hides what
 * is wrong with the templates: the files of a pack are the ones its descriptor names, so there are none
 * to check until it can be read, and the problems of the descriptor are all that is given. Neither are
 * the checks made that need the application a pack is used for, which {@link PackFeature} makes when
 * one is generated.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackCheck {

    /** The result of {@link #run} for a pack nothing is wrong with */
    public static final int VALID = 0

    /** The result of {@link #run} for a pack with problems, which are what it wrote */
    public static final int PROBLEMS = 1

    /** The result of {@link #run} when the pack could not be checked */
    public static final int NOT_CHECKED = 2

    /**
     * What {@link #run} writes for a pack nothing is wrong with, before the numbers of templates and of
     * applications they were rendered for: a check that ended without it did not get to its end.
     */
    public static final String CHECKED = 'feature pack checked: '

    private static final int MAX_NAME = 120

    private PackCheck() {
    }

    /**
     * @param directory the directory of the pack, which holds its {@code feature.yml}
     * @return what is wrong with the pack, each problem on one line and with its place in the descriptor
     * where it has one; nothing for a pack that can be used
     */
    static List<String> check(Path directory) {
        return examine(directory).problems
    }

    private static Examined examine(Path directory) {
        Pack pack
        try {
            pack = new PackLoader().fromDirectory(directory)
        } catch (PackDescriptorException e) {
            return new Examined(e.problems.collect { String problem -> line(problem) }, 0, 0)
        }
        PackDescriptor descriptor = pack.descriptor
        List<String> problems = []
        compatibility('grails', 'Grails', descriptor.grailsCompatibility, VersionInfo.getGrailsVersion(), problems)
        compatibility('forge', 'Forge', descriptor.forgeCompatibility, VersionInfo.getVersion(), problems)
        Map<String, Map<String, Object>> samples = PackModel.samples(descriptor)
        PackPages pages = null
        Set<String> checked = new HashSet<>()
        for (int index = 0; index < descriptor.files.size() && problems.size() < PackDescriptorParser.MAX_PROBLEMS; index++) {
            PackFile file = descriptor.files[index]
            if (!Pack.isTemplate(file.from) || !checked.add(file.from)) {
                continue
            }
            pages = pages ?: new PackPages(PackPages.classLoader)
            String source = new String(pack.content(file.from), StandardCharsets.UTF_8)
            String name = file.from.length() > MAX_NAME ? file.from.substring(0, MAX_NAME) + '...' : file.from
            // the first application a template fails for says enough about it
            for (Map.Entry<String, Map<String, Object>> sample : samples.entrySet()) {
                try {
                    // compiled for the first application, and rendered for each
                    pages.render(descriptor.name, source, name, descriptor.templateApi, sample.value, PackLoader.MAX_FILE_BYTES)
                } catch (IllegalArgumentException e) {
                    problems << line("files[${index}].from: ${e.message} (for an application with ${sample.key})")
                    break
                }
            }
        }
        return new Examined(problems, checked.size(), samples.size())
    }

    private static final class Examined {

        final List<String> problems
        final int templates
        final int applications

        Examined(List<String> problems, int templates, int applications) {
            this.problems = problems
            this.templates = templates
            this.applications = applications
        }
    }

    private static void compatibility(String key, String of, String range, String version, List<String> problems) {
        PackVersionRange versions = PackVersionRange.parse(range)
        if (!versions.contains(version)) {
            problems << line("compatibility.${key}: the pack is for ${of} ${versions}, and it is checked with ${of} ${version}, " +
                    'which would not use it')
        }
    }

    private static String line(String problem) {
        return problem.replaceAll(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]+/, ' ').trim()
    }

    /**
     * Checks a pack and says what is wrong with it, a problem on a line. For a pack nothing is wrong
     * with it writes one line, {@link #CHECKED} and then {@code templates=} and {@code applications=}
     * with the numbers of templates and of applications each was rendered for, so that a check that was
     * ended before it was done, by a template for one, is not taken for one that found nothing.
     *
     * @param args the directory of the pack, and nothing else
     * @param out where the problems go, or the line of a pack without any, and nothing else
     * @param err where it is said why a pack could not be checked
     * @return {@link #VALID}, {@link #PROBLEMS} or {@link #NOT_CHECKED}
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length != 1 || !args[0]) {
            err.println('Give the directory of the feature pack to check, and nothing else')
            return NOT_CHECKED
        }
        Examined examined
        try {
            examined = examine(Path.of(args[0]))
        } catch (InvalidPathException ignored) {
            err.println('The directory of the feature pack is not a path')
            return NOT_CHECKED
        } catch (VirtualMachineError e) {
            throw e
        } catch (Throwable e) {
            err.println("The feature pack could not be checked: ${line(e.toString())}")
            return NOT_CHECKED
        }
        if (examined.problems) {
            examined.problems.each { String problem -> out.println(problem) }
            return PROBLEMS
        }
        out.println("${CHECKED}templates=${examined.templates} applications=${examined.applications}")
        return VALID
    }

    /**
     * Checks the pack of the directory that is the one argument, writes what {@link #run} writes to the
     * standard output, and ends the JVM with {@link #VALID}, {@link #PROBLEMS} or
     * {@link #NOT_CHECKED}. What a template writes to the standard output goes to the standard error,
     * so that what the check says is all there is to read.
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
