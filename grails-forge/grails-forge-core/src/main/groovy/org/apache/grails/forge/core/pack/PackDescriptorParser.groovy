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
import java.lang.reflect.Modifier
import java.text.Normalizer
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.grails.forge.core.pack.PackDescriptor.PackBuildPlugin
import org.apache.grails.forge.core.pack.PackDescriptor.PackDependency
import org.apache.grails.forge.core.pack.PackDescriptor.PackFile
import org.grails.forge.application.ApplicationType
import org.grails.forge.build.dependencies.Scope
import org.grails.forge.feature.Category
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.events.AliasEvent
import org.yaml.snakeyaml.events.CollectionEndEvent
import org.yaml.snakeyaml.events.CollectionStartEvent
import org.yaml.snakeyaml.events.Event
import org.yaml.snakeyaml.events.MappingStartEvent
import org.yaml.snakeyaml.events.NodeEvent
import org.yaml.snakeyaml.events.ScalarEvent
import org.yaml.snakeyaml.nodes.NodeId
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

/**
 * Reads and checks the descriptor of a feature pack.
 *
 * <p>A descriptor is not trusted: it is read before anyone has decided to use the pack. Reading it runs
 * nothing of the pack. The YAML is loaded as plain maps, lists and single values, without anchors,
 * aliases or tags, and within limits on its size, its depth and the length of its keys, so that reading
 * it takes time and memory in proportion to its size. A value without
 * quotes is text unless it is {@code true}, {@code false}, empty or a number written in decimal digits,
 * so {@code yes}, {@code 010}, {@code 1:30} and {@code 2024-01-01} stay the text they are, and so does
 * a number of more digits than a number keeps.</p>
 *
 * <p>Every value that ends up in a generated file as data, such as coordinates, a version or a build
 * property, is held to the characters such a value has, so that it cannot carry code or a second line
 * into that file. The bodies of build extensions, the readme and the templates are the content of the
 * pack and are not restricted that way.</p>
 *
 * <p>A descriptor is rejected as a whole when it has a key this version of Forge does not read, a value
 * of the wrong type, or a value that cannot be used, such as a file that would be written outside the
 * application. What makes a descriptor costly to read is refused while its text is scanned, one problem
 * at a time, and so is what SnakeYAML cannot load. Then the form of the keys is checked, and when that
 * is right every other problem is reported, each with its place, up to {@link #MAX_PROBLEMS}.</p>
 *
 * @since 9.0
 */
@CompileStatic
class PackDescriptorParser {

    /** The scopes a pack can declare a dependency in, by their Gradle configuration name */
    public static final Map<String, Scope> SCOPES = Collections.unmodifiableMap([
            implementation: Scope.IMPLEMENTATION,
            compileOnly: Scope.COMPILE_ONLY,
            runtimeOnly: Scope.RUNTIME_ONLY,
            developmentOnly: Scope.DEVELOPMENT_ONLY,
            testImplementation: Scope.TEST_IMPLEMENTATION,
            testCompileOnly: Scope.TEST_COMPILE_ONLY,
            testRuntimeOnly: Scope.TEST_RUNTIME_ONLY,
    ] as Map<String, Scope>)

    /** The placeholders the destination of a file can use */
    public static final Set<String> DESTINATION_TOKENS = Collections.unmodifiableSet(
            ['packageName', 'packagePath', 'className', 'naturalName', 'propertyName', 'name'] as Set<String>)

    /** The most characters a descriptor can have */
    public static final int MAX_LENGTH = 256 * 1024

    /** The most problems of one descriptor that are reported */
    public static final int MAX_PROBLEMS = 50

    private static final Set<String> KEYS = ['format', 'kind', 'name', 'templateApi', 'compatibility', 'title',
                                             'description', 'category', 'documentation', 'applicationTypes', 'requires',
                                             'conflicts', 'oneOf', 'dependencies', 'platforms', 'buildPlugins', 'build',
                                             'configuration', 'files', 'readme', 'instructions'] as Set<String>
    private static final String FEATURE_KIND = 'feature'
    private static final String MARKUP = '@|'
    private static final String ENVIRONMENTS = 'environments'
    private static final int MAX_DEPTH = 20
    private static final int MAX_LINE = 200
    private static final int MAX_PARAGRAPH = 2000
    private static final int MAX_TEXT = 20000
    private static final int MAX_STEP = 255
    private static final int MAX_STEPS = 32
    private static final int MAX_ECHO = 80
    private static final int MAX_KEY_ECHO = 40

    private static final Pattern NAME = Pattern.compile(/[a-z][a-z0-9]*(-[a-z0-9]+)*/)
    private static final String COORDINATE_PART = /[A-Za-z0-9][A-Za-z0-9_.\-]*/
    private static final Pattern COORDINATES = Pattern.compile(COORDINATE_PART + ':' + COORDINATE_PART)
    private static final Pattern EXCLUSION = Pattern.compile(COORDINATE_PART + '(:' + COORDINATE_PART + ')?')
    private static final Pattern FEATURE_ID = Pattern.compile('(' + COORDINATE_PART + ':' + COORDINATE_PART + '#)?' + NAME.pattern())
    private static final Pattern GROUP_ID = Pattern.compile(COORDINATE_PART + '(:' + COORDINATE_PART + ')?')
    private static final Pattern VERSION = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.+\-]*/)
    private static final Pattern VERSION_RANGE = Pattern.compile(/[A-Za-z0-9_.+\-\[\](), ]+/)
    private static final Pattern PLUGIN_ID = Pattern.compile(/[A-Za-z0-9_\-]+(\.[A-Za-z0-9_\-]+)*/)
    private static final Pattern PROPERTY_NAME = Pattern.compile(/[A-Za-z0-9_.\-]+/)
    private static final Pattern PROPERTY_VALUE = Pattern.compile(/([\x21-\x5B\x5D-\x7E]([\x20-\x5B\x5D-\x7E]*[\x21-\x5B\x5D-\x7E])?)?/)
    private static final Pattern IDENTIFIER = Pattern.compile(/[A-Za-z_]\w*/)
    private static final Pattern ENVIRONMENT = Pattern.compile(/[A-Za-z][A-Za-z0-9_\-]{0,49}/)
    private static final Pattern URL = Pattern.compile(/https?:\/\/[A-Za-z0-9\-._~:\/?#\[\]@!&()*+,;=%]+/)
    private static final Pattern TOKEN = Pattern.compile(/\{([^{}]*)\}/)
    // what is refused in every path and replaced in every message: control, format and private-use
    // characters, unpaired surrogates, and line and paragraph separators
    private static final String UNSAFE = /\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}/
    private static final Pattern UNSAFE_CHARACTER = Pattern.compile('[' + UNSAFE + ']')
    // text people read keeps the format characters scripts and emoji need, but not the ones that
    // reorder the text around them or that are never shown
    private static final String UNREADABLE = /\p{Cc}\p{Cs}\p{Co}\p{Zl}\p{Zp}\x{202A}-\x{202E}\x{2066}-\x{2069}\x{E0000}-\x{E007F}/
    private static final Pattern UNREADABLE_IN_LINE = Pattern.compile('[' + UNREADABLE + ']')
    private static final Pattern UNREADABLE_IN_TEXT = Pattern.compile('[' + UNREADABLE + '&&[^\\n\\t]]')
    private static final Pattern PATH_STEP = Pattern.compile('(?!~)[^' + UNSAFE + /\\:<>"|?*]+/)
    private static final Pattern SHORT_NAME = Pattern.compile(/.*~[0-9].*/)
    private static final Pattern RESERVED_STEP = Pattern.compile(
            /(?i)\.git|(con|prn|aux|nul|com[0-9\xB9\xB2\xB3]|lpt[0-9\xB9\xB2\xB3]|conin\$|conout\$|clock\$) *(\..*)?/)

    /**
     * @param yaml the text of the descriptor
     * @param source where it came from, for messages, which show at most 200 characters of it
     * @return the descriptor
     * @throws PackDescriptorException when the descriptor cannot be used
     */
    PackDescriptor parse(String yaml, String source) {
        return read(yaml, shown(source ?: 'of an unknown origin', MAX_LINE))
    }

    private static PackDescriptor read(String yaml, String source) {
        Map<String, Object> root = load(yaml, source)
        List<String> problems = new Problems()
        checkTree(root, '', problems)
        failOn(problems, source)
        unknownKeys(root, KEYS.toList(), '', problems)
        requireNumber(root, 'format', PackDescriptor.FORMAT, problems)
        requireNumber(root, 'templateApi', PackDescriptor.TEMPLATE_API, problems)
        String kind = requiredText(root, 'kind', '', MAX_LINE, problems)
        if (kind != null && kind != FEATURE_KIND) {
            problems << "kind: must be '${FEATURE_KIND}', the only kind this version of Forge reads, but is ${describe(kind)}".toString()
        }
        String name = matching(requiredText(root, 'name', '', MAX_LINE, problems), NAME, 'name',
                'must be lower case letters and digits in words joined by hyphens, starting with a letter', problems)
        Map<String, Object> compatibility = requiredMap(root, 'compatibility', problems)
        unknownKeys(compatibility, ['grails', 'forge'], 'compatibility.', problems)
        String grails = null
        String forge = null
        if (root.get('compatibility') instanceof Map) {
            grails = versionRange(compatibility, 'grails', problems)
            forge = versionRange(compatibility, 'forge', problems)
        }
        Set<String> categories = categories()
        String category = text(root, 'category', '', MAX_LINE, problems) ?: Category.OTHER
        if (!categories.contains(category)) {
            problems << "category: must be one of ${categories.toSorted()}, but is ${describe(category)}".toString()
        }
        List<String> requires = featureIds(root, 'requires', name, problems)
        List<String> conflicts = featureIds(root, 'conflicts', name, problems)
        for (String both : requires.intersect(conflicts)) {
            problems << "conflicts: ${describe(both)} is also required".toString()
        }
        Map<String, Object> build = map(root, 'build', '', problems)
        unknownKeys(build, ['properties', 'extensions'], 'build.', problems)

        PackDescriptor descriptor = new PackDescriptor(
                name,
                text(root, 'title', '', MAX_LINE, problems) ?: name,
                text(root, 'description', '', MAX_PARAGRAPH, problems) ?: name,
                category,
                matching(text(root, 'documentation', '', MAX_PARAGRAPH, problems), URL, 'documentation',
                        "must be an http or https address, without spaces, quotes, '<', '>' or '\$'", problems),
                grails,
                forge,
                applicationTypes(root, problems),
                requires,
                conflicts,
                matching(text(root, 'oneOf', '', MAX_LINE, problems), GROUP_ID, 'oneOf',
                        "must be the identifier of a group, such as 'database-driver' or 'com.acme:audit'", problems),
                dependencies(root, 'dependencies', true, problems),
                dependencies(root, 'platforms', false, problems),
                buildPlugins(root, problems),
                buildProperties(build, problems),
                buildExtensions(build, problems),
                configuration(root, problems),
                files(root, problems),
                document(root.get('readme'), 'readme', problems),
                document(root.get('instructions'), 'instructions', problems))
        failOn(problems, source)
        return descriptor
    }

    private static void failOn(List<String> problems, String source) {
        if (problems) {
            throw new PackDescriptorException(source, problems)
        }
    }

    private static Map<String, Object> load(String yaml, String source) {
        if (yaml == null || yaml.isBlank()) {
            throw new PackDescriptorException(source, ['It is empty'])
        }
        if (yaml.length() > MAX_LENGTH) {
            throw new PackDescriptorException(source, ["It has ${yaml.length()} characters, and a descriptor can have ${MAX_LENGTH}".toString()])
        }
        Object loaded
        try {
            LoaderOptions options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            options.setMaxAliasesForCollections(0)
            options.setAllowRecursiveKeys(false)
            options.setNestingDepthLimit(MAX_DEPTH + 1)
            options.setCodePointLimit(MAX_LENGTH)
            DumperOptions dumperOptions = new DumperOptions()
            Resolver resolver = new PlainResolver()
            Yaml reader = new Yaml(new SafeConstructor(options), new Representer(dumperOptions), dumperOptions, options, resolver)
            String refused = refusedByScan(reader, resolver, yaml)
            if (refused != null) {
                throw new PackDescriptorException(source, [refused])
            }
            loaded = reader.load(yaml)
        } catch (PackDescriptorException e) {
            throw e
        } catch (RuntimeException e) {
            // SnakeYAML reports what it cannot read with its own exceptions, and a value that does not
            // fit the type its tag names with those of the JDK
            String reason = (e.message ?: e.class.simpleName).readLines().join(' ')
            throw new PackDescriptorException(source, ['It is not YAML this version of Forge can read: ' + shown(reason, 300)])
        }
        if (!(loaded instanceof Map)) {
            throw new PackDescriptorException(source, ['It must be a mapping of keys to values'])
        }
        return (Map<String, Object>) loaded
    }

    /**
     * An anchor lets a descriptor repeat a value any number of times at the cost of a few characters,
     * and a tag lets it give a value a type or a reading other than the one it is written in. A
     * descriptor needs neither, so the first of either is refused before anything is built, and so are
     * a descriptor that nests deeper than one has reason to and a key that is not text.
     */
    private static String refusedByScan(Yaml reader, Resolver resolver, String yaml) {
        // for each mapping or list that is open: whether it is a mapping whose next entry is a key
        // (TRUE), a mapping whose next entry is a value (FALSE), or a list (null)
        Deque<Optional<Boolean>> open = new ArrayDeque<>()
        for (Event event : reader.parse(new StringReader(yaml))) {
            if (event instanceof AliasEvent) {
                return 'It repeats a value with an alias (*), which a descriptor cannot use; write the value out'
            }
            if (event instanceof NodeEvent && ((NodeEvent) event).getAnchor() != null) {
                return 'It names a value with an anchor (&), which a descriptor cannot use'
            }
            String tag = event instanceof ScalarEvent ? ((ScalarEvent) event).getTag() :
                    event instanceof CollectionStartEvent ? ((CollectionStartEvent) event).getTag() : null
            if (tag != null) {
                return "It gives a value the tag ${describe(tag)}, and a descriptor cannot use tags".toString()
            }
            if (event instanceof CollectionEndEvent) {
                open.pop()
                continue
            }
            if (!(event instanceof NodeEvent)) {
                continue
            }
            boolean key = !open.isEmpty() && open.peek().orElse(null) == Boolean.TRUE
            if (!open.isEmpty() && open.peek().isPresent()) {
                // the entry that starts here is a key or a value, and the one after it is the other
                open.push(Optional.of(!open.pop().get()))
            }
            if (key && event instanceof ScalarEvent && ((ScalarEvent) event).isPlain() &&
                    resolver.resolve(NodeId.scalar, ((ScalarEvent) event).getValue(), true) != Tag.STR) {
                // with only text for keys, a mapping can order keys that share a hash code; with
                // numbers among them it compares each key with every other
                String written = ((ScalarEvent) event).getValue()
                if (written.isEmpty()) {
                    return 'It has an entry without a key'
                }
                return "It has the key ${describe(written)} without quotes, and a key that is a number, true, false or empty is put in quotes".toString()
            }
            if (event instanceof CollectionStartEvent) {
                // the loader has the same limit on depth, but only acts on it after the whole text is
                // scanned, and it compares the keys of a mapping before anything tells it they are text
                if (key) {
                    return 'It has a mapping or a list as a key, and a key is text'
                }
                if (open.size() >= MAX_DEPTH) {
                    return "It has mappings and lists more than ${MAX_DEPTH} deep".toString()
                }
                open.push(event instanceof MappingStartEvent ? Optional.of(Boolean.TRUE) : Optional.<Boolean>empty())
            }
        }
        return null
    }

    /**
     * Every key of the descriptor is a line of text, and every single value is text, true or false, or
     * a number. Without tags the loader makes nothing else of a value, so the last check only guards
     * against a loader that does.
     */
    private static void checkTree(Object value, String at, List<String> problems) {
        if (value instanceof Map) {
            ((Map) value).each { Object key, Object each ->
                String place = at ?: 'the descriptor'
                if (!(key instanceof String)) {
                    problems << "${place}: has a key that is not text, ${describe(key)}; put it in quotes".toString()
                } else if (((String) key).isBlank() || ((String) key).length() > MAX_LINE || UNSAFE_CHARACTER.matcher((String) key).find()) {
                    problems << "${place}: has the key ${describe(key)}, and a key is one line of at most ${MAX_LINE} characters, none of them a control character".toString()
                } else if (((String) key).contains(MARKUP)) {
                    problems << "${place}: has a key with '${MARKUP}' in it, which the command line reads as the start of markup".toString()
                } else {
                    checkTree(each, at ? "${at}.${brief((String) key)}".toString() : brief((String) key), problems)
                }
            }
        } else if (value instanceof List) {
            ((List) value).eachWithIndex { Object each, int index -> checkTree(each, "${at}[${index}]".toString(), problems) }
        } else if (value instanceof String && ((String) value).contains(MARKUP)) {
            // what a descriptor says is shown on the command line, in listings and in messages
            problems << "${at}: has '${MARKUP}' in it, which the command line reads as the start of markup".toString()
        } else if (!(value == null || value instanceof String || value instanceof Boolean || value instanceof Integer ||
                value instanceof Long || value instanceof Double)) {
            problems << "${at}: must be text, true or false, or a number".toString()
        }
    }

    private static Set<ApplicationType> applicationTypes(Map<String, Object> root, List<String> problems) {
        if (!root.containsKey('applicationTypes')) {
            return EnumSet.allOf(ApplicationType)
        }
        Map<String, ApplicationType> known = ApplicationType.values().collectEntries { ApplicationType type ->
            [type.getName().replace('_', '-'), type]
        }
        Object value = root.get('applicationTypes')
        if (value == null || (value instanceof List && ((List) value).isEmpty())) {
            problems << 'applicationTypes: must name at least one application type, or be left out for all of them'
            return EnumSet.noneOf(ApplicationType)
        }
        Set<ApplicationType> types = new LinkedHashSet<>()
        texts(root, 'applicationTypes', '', problems).each { int index, String name ->
            ApplicationType type = known.get(name)
            if (type == null) {
                problems << "applicationTypes[${index}]: must be among ${known.keySet().toSorted()}, but is ${describe(name)}".toString()
            } else {
                types << type
            }
        }
        return types
    }

    private static List<String> featureIds(Map<String, Object> root, String key, String name, List<String> problems) {
        List<String> ids = []
        Object value = root.get(key)
        if (value instanceof List) {
            List<Object> entries = (List<Object>) value
            List<Object> names = new ArrayList<>(entries)
            entries.eachWithIndex { Object each, int index ->
                if (each instanceof Map) {
                    problems << "${key}[${index}]: is a mapping; this version of Forge reads only the names of features here".toString()
                    names.set(index, Skipped.ENTRY)
                }
            }
            root = [(key): names] as Map<String, Object>
        }
        texts(root, key, '', problems).each { int index, String id ->
            String at = "${key}[${index}]"
            if (matching(id, FEATURE_ID, at, "must be the name of a feature, or 'group:artifact#name' for the feature of another pack", problems) != null) {
                if (id == name) {
                    problems << "${at}: must not name the feature itself, ${describe(name)}".toString()
                }
                ids << id
            }
        }
        return ids
    }

    private static List<PackDependency> dependencies(Map<String, Object> root, String key, boolean scoped, List<String> problems) {
        List<PackDependency> dependencies = []
        Set<String> declared = new HashSet<>()
        maps(root, key, problems).each { int index, Map<String, Object> entry ->
            String at = "${key}[${index}]."
            unknownKeys(entry, scoped ? ['scope', 'coordinates', 'version', 'excludes'] : ['coordinates', 'version'], at, problems)
            String scope = scoped ? requiredText(entry, 'scope', at, MAX_LINE, problems) : null
            boolean scopeKnown = !scoped || SCOPES.containsKey(scope)
            if (scope != null && !scopeKnown) {
                problems << "${at}scope: must be one of ${SCOPES.keySet().toList()}, but is ${describe(scope)}".toString()
                scope = null
            }
            String coordinates = matching(requiredText(entry, 'coordinates', at, MAX_LINE, problems), COORDINATES, at + 'coordinates',
                    "must be 'group:artifact', with the version given separately", problems)
            List<String> parts = coordinates == null ? [null, null] as List<String> : coordinates.split(':').toList()
            if (coordinates != null && scopeKnown && !declared.add("${scope} ${coordinates}".toString())) {
                problems << "${at}coordinates: ${describe(coordinates)} is declared by an earlier entry too".toString()
            }
            List<String> excludes = []
            if (scoped) {
                texts(entry, 'excludes', at, problems).each { int excluded, String exclude ->
                    if (matching(exclude, EXCLUSION, "${at}excludes[${excluded}]", "must be 'group' or 'group:artifact'", problems) != null) {
                        excludes << exclude
                    }
                }
            }
            dependencies << new PackDependency(scope, parts[0], parts[1], version(entry, at, problems), excludes)
        }
        return dependencies
    }

    private static List<PackBuildPlugin> buildPlugins(Map<String, Object> root, List<String> problems) {
        List<PackBuildPlugin> plugins = []
        Set<String> declared = new HashSet<>()
        maps(root, 'buildPlugins', problems).each { int index, Map<String, Object> entry ->
            String at = "buildPlugins[${index}]."
            unknownKeys(entry, ['id', 'version'], at, problems)
            String id = matching(requiredText(entry, 'id', at, MAX_LINE, problems), PLUGIN_ID, at + 'id',
                    "must be the id of a Gradle plugin, such as 'com.acme.audit'", problems)
            if (id != null && !declared.add(id)) {
                problems << "${at}id: ${describe(id)} is declared by an earlier entry too".toString()
            }
            plugins << new PackBuildPlugin(id, version(entry, at, problems))
        }
        return plugins
    }

    /**
     * A build property is written to {@code gradle.properties} as it is, so its name and its value are
     * held to what that file reads back unchanged: no backslash, which continues a line or starts an
     * escape there, no space at either end, and no character outside printable ASCII.
     */
    private static Map<String, String> buildProperties(Map<String, Object> build, List<String> problems) {
        Map<String, String> properties = new LinkedHashMap<>()
        map(build, 'properties', 'build.', problems).each { String name, Object value ->
            if (!PROPERTY_NAME.matcher(name).matches()) {
                problems << "build.properties: the name of a property is letters, digits, '.', '-' and '_', but one is ${describe(name)}".toString()
                return
            }
            String at = "build.properties.${brief(name)}"
            if (value == null) {
                properties.put(name, '')
            } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
                properties.put(name, value.toString())
            } else if (!(value instanceof String)) {
                problems << "${at}: must be text, true or false, or a whole number, but is ${describe(value)}; put any other value in quotes".toString()
            } else {
                String line = withoutLastBreaks((String) value)
                if (line.length() > MAX_PARAGRAPH || !PROPERTY_VALUE.matcher(line).matches()) {
                    problems << ("${at}: must be one line of at most ${MAX_PARAGRAPH} printable ASCII characters, without a backslash " +
                            "and without a space at its start or end, but is ${describe(value)}").toString()
                } else {
                    properties.put(name, line)
                }
            }
        }
        return properties
    }

    private static Map<String, String> buildExtensions(Map<String, Object> build, List<String> problems) {
        Map<String, String> extensions = new LinkedHashMap<>()
        map(build, 'extensions', 'build.', problems).each { String name, Object value ->
            if (!name.split(/\./, -1).every { String part -> IDENTIFIER.matcher(part).matches() }) {
                problems << "build.extensions: the name of an extension is one or more identifiers joined by '.', but one is ${describe(name)}".toString()
                return
            }
            String body = document(value, "build.extensions.${brief(name)}", problems)
            if (body != null) {
                extensions.put(name, body)
            } else if (value == null) {
                problems << "build.extensions.${brief(name)}: must be text, but is empty".toString()
            }
        }
        return extensions
    }

    private static List<PackFile> files(Map<String, Object> root, List<String> problems) {
        List<PackFile> files = []
        // destinations and the directories above them, as a file system that ignores the case and the
        // composed form of letters sees them, each with the destination that was written
        Map<String, String> destinations = new HashMap<>()
        Map<String, String> directories = new HashMap<>()
        maps(root, 'files', problems).each { int index, Map<String, Object> entry ->
            String at = "files[${index}]."
            unknownKeys(entry, ['from', 'to'], at, problems)
            String from = path(entry, 'from', at, false, problems)
            String to = path(entry, 'to', at, true, problems)
            if (to != null) {
                String folded = Normalizer.normalize(to, Normalizer.Form.NFC).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT)
                List<String> above = []
                for (int slash = folded.indexOf('/'); slash >= 0; slash = folded.indexOf('/', slash + 1)) {
                    above << folded.substring(0, slash)
                }
                String other = directories.get(folded) ?: above.findResult { String directory -> destinations.get(directory) }
                if (destinations.containsKey(folded)) {
                    problems << "${at}to: ${describe(to)} is the destination of an earlier file too, at least where the case of letters does not count".toString()
                } else if (other != null) {
                    problems << "${at}to: ${describe(to)} and the earlier destination ${describe(other)} make one path both a file and a directory".toString()
                } else {
                    destinations.put(folded, to)
                    for (String directory : above) {
                        directories.putIfAbsent(directory, to)
                    }
                }
            }
            files << new PackFile(from, to)
        }
        return files
    }

    /**
     * A path of a pack is relative, has {@code /} between its at most 32 steps and stays inside its root on
     * every operating system. A step is not {@code .} or {@code ..}, does not start or end with a space or end
     * with a dot, is not a name an operating system or Git reserves, is at most 255 characters, and has
     * none of the characters a file system gives a meaning or refuses: a backslash, a colon, a wildcard,
     * a quote, an angle bracket, a bar, a brace outside a placeholder, a leading {@code ~} or the
     * {@code ~1} of a short name.
     */
    private static String path(Map<String, Object> entry, String key, String at, boolean tokens, List<String> problems) {
        Object value = entry.get(key)
        if (value instanceof Map) {
            problems << "${at}${key}: must be text, but is a mapping; put a path that starts with '{' in quotes".toString()
            return null
        }
        String path = requiredText(entry, key, at, MAX_PARAGRAPH, problems)
        if (path == null) {
            return null
        }
        String literal = path
        if (tokens) {
            Matcher used = TOKEN.matcher(path)
            while (used.find()) {
                if (!DESTINATION_TOKENS.contains(used.group(1))) {
                    problems << "${at}${key}: can use the placeholders ${DESTINATION_TOKENS.toList()}, but has ${describe('{' + used.group(1) + '}')}".toString()
                    return null
                }
            }
            literal = TOKEN.matcher(path).replaceAll('x')
        }
        if (!isPathBelowRoot(literal)) {
            problems << ("${at}${key}: must be a path below the root with '/' between its at most ${MAX_STEPS} steps, where a step is not '.' or '..', " +
                    "does not start or end with a space or end with '.', is not a name an operating system or Git reserves, " +
                    "and has no character a file system gives a meaning, but is ${describe(path)}").toString()
            return null
        }
        return path
    }

    /**
     * Whether a path, with nothing left in it to substitute, is one a pack can read or write: see the
     * rules of a path in {@link PackDescriptorParser}. What writes the file of a destination checks the
     * path with this again once its placeholders have their values, which the parser does not know.
     *
     * @param path a path without placeholders
     */
    static boolean isPathBelowRoot(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_PARAGRAPH || path.contains('{') || path.contains('}')) {
            return false
        }
        List<String> steps = path.split('/', -1).toList()
        return steps.size() <= MAX_STEPS && steps.every { String step ->
            step.length() <= MAX_STEP && PATH_STEP.matcher(step).matches() && step != '.' && step != '..' &&
                    !step.startsWith(' ') && !step.endsWith(' ') && !step.endsWith('.') && !RESERVED_STEP.matcher(step).matches() &&
                    !SHORT_NAME.matcher(step).matches()
        }
    }

    /**
     * The configuration, of which the settings under {@code environments} are those of one environment
     * each. The name of an environment becomes part of the name of a file, so it is held to a name.
     */
    private static Map<String, Object> configuration(Map<String, Object> root, List<String> problems) {
        Map<String, Object> configuration = map(root, 'configuration', '', problems)
        if (!configuration.containsKey(ENVIRONMENTS)) {
            return configuration
        }
        Object environments = configuration.get(ENVIRONMENTS)
        if (!(environments instanceof Map)) {
            problems << "configuration.${ENVIRONMENTS}: must be a mapping of the names of environments to their settings, but is ${describe(environments)}".toString()
            return configuration
        }
        ((Map<String, Object>) environments).each { String environment, Object settings ->
            if (!ENVIRONMENT.matcher(environment).matches()) {
                problems << "configuration.${ENVIRONMENTS}: the name of an environment is letters, digits, '-' and '_', starting with a letter, but one is ${describe(environment)}".toString()
            } else if (!(settings instanceof Map)) {
                problems << "configuration.${ENVIRONMENTS}.${environment}: must be a mapping of settings, but is ${describe(settings)}".toString()
            }
        }
        return configuration
    }

    private static String version(Map<String, Object> entry, String at, List<String> problems) {
        return matching(text(entry, 'version', at, MAX_LINE, problems), VERSION, at + 'version',
                "must be one version, of letters, digits, '.', '-', '+' and '_'", problems)
    }

    private static String versionRange(Map<String, Object> compatibility, String key, List<String> problems) {
        return matching(requiredText(compatibility, key, 'compatibility.', MAX_LINE, problems), VERSION_RANGE, 'compatibility.' + key,
                "must be a version or a range of versions, such as '[9.0.0,10.0.0)'", problems)
    }

    private static String matching(String value, Pattern pattern, String at, String rule, List<String> problems) {
        if (value == null) {
            return null
        }
        if (!pattern.matcher(value).matches()) {
            problems << "${at}: ${rule}, but is ${describe(value)}".toString()
            return null
        }
        return value
    }

    private static void requireNumber(Map<String, Object> root, String key, int expected, List<String> problems) {
        Object value = root.get(key)
        if (value == null) {
            problems << "${key}: is required, and ${expected} is the one this version of Forge reads".toString()
        } else if (!(value instanceof Integer) || value != expected) {
            problems << "${key}: must be the number ${expected}, the one this version of Forge reads, but is ${describe(value)}".toString()
        }
    }

    private static String requiredText(Map<String, Object> from, String key, String at, int limit, List<String> problems) {
        if (from.get(key) == null) {
            problems << "${at}${key}: is required".toString()
            return null
        }
        return text(from, key, at, limit, problems)
    }

    /**
     * One line of text of the map, without the line break a block of YAML ends with, or {@code null}
     * when the key is absent, empty or not usable.
     */
    private static String text(Map<String, Object> from, String key, String at, int limit, List<String> problems) {
        return from.get(key) == null ? null : line(from.get(key), at + key, limit, problems)
    }

    private static String line(Object value, String at, int limit, List<String> problems) {
        if (!(value instanceof String)) {
            problems << "${at}: must be text, but is ${describe(value)}${quoteHint(value)}".toString()
            return null
        }
        String line = withoutLastBreaks((String) value)
        if (line.isBlank() || line.length() > limit || UNREADABLE_IN_LINE.matcher(line).find()) {
            problems << "${at}: must be one line of text of at most ${limit} characters that is not blank and has no control characters, but is ${describe(value)}".toString()
            return null
        }
        return line
    }

    /**
     * Text of several lines, with a line feed between them, or {@code null} when the value is empty or
     * not usable.
     */
    private static String document(Object value, String at, List<String> problems) {
        if (value == null) {
            return null
        }
        if (!(value instanceof String)) {
            problems << "${at}: must be text, but is ${describe(value)}${quoteHint(value)}".toString()
            return null
        }
        String text = ((String) value).length() > MAX_TEXT * 2 ? '' : ((String) value).replace('\r\n', '\n')
        if (text.isBlank() || text.length() > MAX_TEXT || UNREADABLE_IN_TEXT.matcher(text).find()) {
            problems << "${at}: must be text of at most ${MAX_TEXT} characters that is not blank and has no control characters, but is ${describe(value)}".toString()
            return null
        }
        return text
    }

    /**
     * A text without the line breaks it ends with, which a block of YAML adds to a single line.
     */
    private static String withoutLastBreaks(String text) {
        int end = text.length()
        while (end > 0 && (text.charAt(end - 1) == (char) '\n' || text.charAt(end - 1) == (char) '\r')) {
            end--
        }
        return text.substring(0, end)
    }

    /**
     * A key as the place of a problem shows it.
     */
    private static String brief(String key) {
        return shown(key, MAX_KEY_ECHO)
    }

    private static String quoteHint(Object value) {
        return value instanceof Number || value instanceof Boolean ? '; put it in quotes' : ''
    }

    /**
     * The usable lines of text of a list, by their place in the list.
     */
    private static Map<Integer, String> texts(Map<String, Object> from, String key, String at, List<String> problems) {
        Map<Integer, String> values = new LinkedHashMap<>()
        Object value = from.get(key)
        if (value == null) {
            return values
        }
        if (!(value instanceof List)) {
            problems << "${at}${key}: must be a list, but is ${describe(value)}".toString()
            return values
        }
        Set<String> seen = new HashSet<>()
        ((List) value).eachWithIndex { Object each, int index ->
            if (Skipped.ENTRY.is(each)) {
                return
            }
            String text = line(each, "${at}${key}[${index}]", MAX_LINE, problems)
            if (text != null && !seen.add(text)) {
                problems << "${at}${key}[${index}]: ${describe(text)} is listed earlier too".toString()
            } else if (text != null) {
                values.put(index, text)
            }
        }
        return values
    }

    private static Map<String, Object> requiredMap(Map<String, Object> from, String key, List<String> problems) {
        if (from.get(key) == null) {
            problems << "${key}: is required".toString()
            return [:]
        }
        return map(from, key, '', problems)
    }

    /**
     * The map of the key, or an empty map when the key is absent, empty or not a map.
     */
    private static Map<String, Object> map(Map<String, Object> from, String key, String at, List<String> problems) {
        Object value = from.get(key)
        if (value == null) {
            return [:]
        }
        if (!(value instanceof Map)) {
            problems << "${at}${key}: must be a mapping of keys to values, but is ${describe(value)}".toString()
            return [:]
        }
        return (Map<String, Object>) value
    }

    /**
     * The mappings of a list, by their place in the list.
     */
    private static Map<Integer, Map<String, Object>> maps(Map<String, Object> from, String key, List<String> problems) {
        Map<Integer, Map<String, Object>> entries = new LinkedHashMap<>()
        Object value = from.get(key)
        if (value == null) {
            return entries
        }
        if (!(value instanceof List)) {
            problems << "${key}: must be a list of mappings, but is ${describe(value)}".toString()
            return entries
        }
        ((List) value).eachWithIndex { Object each, int index ->
            if (each instanceof Map) {
                entries.put(index, (Map<String, Object>) each)
            } else {
                problems << "${key}[${index}]: must be a mapping of keys to values, but is ${describe(each)}".toString()
            }
        }
        return entries
    }

    private static void unknownKeys(Map<String, Object> from, List<String> known, String at, List<String> problems) {
        String place = at ? at.substring(0, at.length() - 1) : 'the descriptor'
        for (String key : from.keySet()) {
            if (!known.contains(key)) {
                problems << "${place}: has the key ${describe(key)}, which this version of Forge does not read here; the keys are ${known}".toString()
            }
        }
    }

    /**
     * A value as a message shows it: never a whole map or list, never more than a few words, and
     * without characters that would act on a terminal or start a line.
     */
    private static String describe(Object value) {
        if (value == null) {
            return 'empty'
        }
        if (value instanceof Map) {
            return 'a mapping'
        }
        if (value instanceof Collection || value.class.isArray()) {
            return 'a list'
        }
        if (value instanceof String) {
            return "'${shown((String) value, MAX_ECHO)}'".toString()
        }
        if (value instanceof Boolean || value instanceof Number) {
            return "the ${value instanceof Boolean ? 'value' : 'number'} ${shown(value.toString(), MAX_ECHO)}".toString()
        }
        return 'a value of another kind'
    }

    /**
     * At most {@code limit} characters of a text, with the characters that are not safe to show replaced.
     */
    private static String shown(String text, int limit) {
        // a text is cut before it is searched, so a long one costs no more than a short one
        String start = text.length() > limit * 2 ? text.substring(0, limit * 2) : text
        String safe = UNSAFE_CHARACTER.matcher(start).replaceAll('?')
        int count = safe.codePointCount(0, safe.length())
        if (start.length() == text.length() && count <= limit) {
            return safe
        }
        return safe.substring(0, safe.offsetByCodePoints(0, Math.min(limit, count))) + '...'
    }

    private static Set<String> categories() {
        return Category.declaredFields
                .findAll { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) && it.type == String }
                .collect { (String) it.get(null) } as Set<String>
    }

    /**
     * The problems of a descriptor, of which only the first {@link #MAX_PROBLEMS} are kept. After them
     * comes one entry that says how many more there were.
     */
    @CompileStatic
    private static class Problems extends ArrayList<String> {

        // named with its class: a list reads a bare name as the property of each of its elements
        private static final int LIMIT = PackDescriptorParser.MAX_PROBLEMS
        private static final Pattern MORE = Pattern.compile(/and (\d+) more/)

        @Override
        boolean add(String problem) {
            if (size() < LIMIT) {
                return super.add(problem)
            }
            if (size() == LIMIT) {
                return super.add('and 1 more')
            }
            Matcher counted = MORE.matcher(get(LIMIT))
            counted.matches()
            set(LIMIT, "and ${Integer.parseInt(counted.group(1)) + 1} more".toString())
            return false
        }
    }

    /**
     * Stands in a list for an entry that was already reported and is not to be read again.
     */
    @CompileStatic
    private static class Skipped {

        static final Object ENTRY = new Skipped()
    }

    /**
     * Reads a value without quotes as text unless it is true, false, empty or a number in decimal
     * digits: none of the other forms YAML 1.1 knows, such as {@code yes}, octal and hexadecimal
     * numbers, numbers in base sixty, dates, the {@code <<} key that merges a mapping, {@code -0}, and
     * numbers of more digits than a {@code long} (eighteen) or a {@code double} (fifteen) keeps.
     */
    @CompileStatic
    private static class PlainResolver extends Resolver {

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, Pattern.compile(/true|false/), 'tf')
            addImplicitResolver(Tag.INT, Pattern.compile(/0|-?[1-9][0-9]{0,17}/), '-0123456789')
            // a double keeps fifteen digits, wherever the point is among them
            addImplicitResolver(Tag.FLOAT, Pattern.compile(/-?(?=[0-9.]{3,16}$)(0|[1-9][0-9]*)\.[0-9]+/), '-0123456789')
            addImplicitResolver(Tag.NULL, Pattern.compile(/~|null|/), '~n\u0000')
        }
    }
}
