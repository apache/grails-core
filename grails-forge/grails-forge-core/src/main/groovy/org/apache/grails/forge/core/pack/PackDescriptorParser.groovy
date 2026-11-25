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
import org.apache.grails.forge.core.pack.PackDescriptor.PackLink
import org.grails.forge.application.ApplicationType
import org.grails.forge.build.dependencies.Scope
import org.grails.forge.build.gradle.BuildFileAnchor
import org.grails.forge.feature.Category
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.VersionInfo
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
 * <p>A descriptor whose {@code kind} is {@code preset} is that of a preset ({@link PackPreset}): it
 * names one type of application with {@code applicationType}, gives options with {@code options} and
 * asks for features with {@code features}, which are its {@link PackDescriptor#requires}; it has no
 * {@code applicationTypes}, {@code requires}, {@code oneOf} or {@code category}, and a feature has
 * none of the three keys of a preset. A pack Forge has in itself is a feature.</p>
 *
 * @since 9.0
 */
@CompileStatic
class PackDescriptorParser {

    /** The scope of a dependency of the build itself, which is on the class path of the build script */
    public static final String BUILDSCRIPT = 'buildscript'

    /** The scopes a pack can declare a dependency in, by their Gradle configuration name; {@link #BUILDSCRIPT} is the build itself */
    public static final Map<String, Scope> SCOPES = Collections.unmodifiableMap([
            implementation: Scope.IMPLEMENTATION,
            compileOnly: Scope.COMPILE_ONLY,
            runtimeOnly: Scope.RUNTIME_ONLY,
            developmentOnly: Scope.DEVELOPMENT_ONLY,
            testImplementation: Scope.TEST_IMPLEMENTATION,
            testCompileOnly: Scope.TEST_COMPILE_ONLY,
            testRuntimeOnly: Scope.TEST_RUNTIME_ONLY,
            testAndDevelopmentOnly: Scope.TEST_AND_DEVELOPMENT_ONLY,
            integrationTestImplementationTestFixtures: Scope.INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES,
            (BUILDSCRIPT): Scope.BUILD,
    ] as Map<String, Scope>)

    /** The scopes a pack Forge has in itself can declare a dependency in, which are those of every pack */
    public static final Map<String, Scope> SCOPES_OF_FORGE = SCOPES

    /** The places among the features a pack Forge has in itself can take, by their names in a descriptor */
    public static final Map<String, Integer> ORDERS = Collections.unmodifiableMap(FeaturePhase.values().collectEntries { FeaturePhase phase ->
        [phase.name().toLowerCase(Locale.ROOT), phase.getOrder()]
    } as Map<String, Integer>)

    /** The placeholders the destination of a file can use */
    public static final Set<String> DESTINATION_TOKENS = Collections.unmodifiableSet(
            ['packageName', 'packagePath', 'className', 'naturalName', 'propertyName', 'name'] as Set<String>)

    /** The places of the build file a pack can add text at, by their names in a descriptor */
    public static final Map<String, BuildFileAnchor> ANCHORS = Collections.unmodifiableMap([
            afterPlugins: BuildFileAnchor.AFTER_PLUGINS,
            afterDependencies: BuildFileAnchor.AFTER_DEPENDENCIES,
            end: BuildFileAnchor.END,
    ] as Map<String, BuildFileAnchor>)

    /** The most characters a descriptor can have */
    public static final int MAX_LENGTH = 256 * 1024

    /** The most problems of one descriptor that are reported */
    public static final int MAX_PROBLEMS = 50

    private static final Set<String> KEYS = ['format', 'kind', 'name', 'templateApi', 'compatibility', 'title',
                                             'description', 'category', 'documentation', 'applicationTypes', 'requires',
                                             'conflicts', 'oneOf', 'dependencies', 'platforms', 'buildPlugins', 'build',
                                             'configuration', 'files', 'readme', 'instructions', 'readmeLinks', 'requiresJdk', 'parameters', 'thirdPartyDocumentation'] as Set<String>
    // a preset names one type of application, gives options and asks for features, and is in no group or category
    private static final List<String> PRESET_KEYS = ['format', 'kind', 'name', 'templateApi', 'compatibility', 'title', 'description',
                                                     'documentation', 'thirdPartyDocumentation', 'applicationType', 'options', 'features',
                                                     'conflicts', 'dependencies', 'platforms', 'buildPlugins', 'build', 'configuration', 'files',
                                                     'readme', 'instructions']
    private static final Map<String, String> NOT_OF_A_PRESET = [
            applicationTypes: 'a preset is for one type of application, which it names with applicationType',
            requires: 'a preset asks for features with features',
            oneOf: 'a preset is in no exclusive group',
            category: 'a preset is not among the features that are listed, and has no category',
    ]
    private static final Map<String, String> ONLY_OF_A_PRESET = [
            applicationType: 'only a preset (kind: preset) names one type of application; a feature names the types it is for with applicationTypes',
            options: 'only a preset (kind: preset) gives options',
            features: 'only a preset (kind: preset) asks for features; a feature requires them with requires',
    ]
    // what only a pack Forge has in itself says: how its feature is selected without being named, and what it has of Forge itself
    private static final List<String> KEYS_OF_FORGE = ['versionsOfForge', 'guideOfGrails', 'default', 'adds', 'order', 'visible',
                                                       'reliesOn', 'dataSource', 'dataSourceOfDriver']
    private static final String GRAILS_DOCUMENTATION = 'https://grails.apache.org/docs/'
    private static final String PAGE_STEP = /[A-Za-z0-9][A-Za-z0-9\-_.]*/
    private static final Pattern GUIDE_PAGE = Pattern.compile(PAGE_STEP + '(/' + PAGE_STEP + ')*/?(#[A-Za-z0-9\\-_.]+)?')
    private static final List<String> CONDITION_KEYS = [PackCondition.FEATURE, PackCondition.GROUP, PackCondition.ANY_OF, PackCondition.ALL_OF,
                                                        PackCondition.NOT, PackCondition.APPLICATION_TYPE, PackCondition.OPTION,
                                                        PackCondition.JDK_AT_LEAST, PackCondition.PARAMETER]
    private static final int MAX_CONDITIONS = 50
    private static final int MAX_CONDITION_DEPTH = 5
    private static final int MIN_JDK = 8
    private static final int MAX_JDK = 99
    private static final String WHEN = 'when'
    private static final int MAX_ENTRIES_OF_FORGE = 20
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
    private static final Pattern ARTIFACT = Pattern.compile(COORDINATE_PART)
    private static final Pattern LINK_LABEL = Pattern.compile(/[^\[\]<>\\]+/)
    private static final Pattern EXCLUSION = Pattern.compile(COORDINATE_PART + '(:' + COORDINATE_PART + ')?')
    private static final Pattern FEATURE_ID = Pattern.compile('(' + COORDINATE_PART + ':' + COORDINATE_PART + '#)?' + NAME.pattern())
    private static final Pattern GROUP_ID = Pattern.compile(COORDINATE_PART + '(:' + COORDINATE_PART + ')?')
    private static final Pattern VERSION = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.+\-]*/)
    private static final Pattern VERSION_RANGE = Pattern.compile(/[A-Za-z0-9_.+\-\[\](), ]+/)
    private static final Pattern PLUGIN_ID = Pattern.compile(/[A-Za-z0-9_\-]+(\.[A-Za-z0-9_\-]+)*/)
    private static final Pattern PROPERTY_NAME = Pattern.compile(/[A-Za-z0-9_.\-]+/)
    private static final Pattern PROPERTY_VALUE = Pattern.compile(/([\x21-\x5B\x5D-\x7E]([\x20-\x5B\x5D-\x7E]*[\x21-\x5B\x5D-\x7E])?)?/)
    private static final Pattern IDENTIFIER = Pattern.compile(/[A-Za-z_]\w*/)
    private static final Pattern PARAMETER_NAME = Pattern.compile(/[a-z][A-Za-z0-9]{0,39}/)
    private static final Pattern IDENTIFIER_OR_CALL = Pattern.compile(/[A-Za-z_]\w*(\([A-Za-z_]\w*\))?/)
    private static final Pattern COMMENT = Pattern.compile(/[\x20-\x7E]+/)
    private static final Pattern ENVIRONMENT = Pattern.compile(/[A-Za-z][A-Za-z0-9_\-]{0,49}/)
    // without square brackets: an address is written into a link in Markdown, and with them it could
    // end the text of the link and start another address
    private static final Pattern URL = Pattern.compile(/https?:\/\/[A-Za-z0-9\-._~:\/?#@!&()*+,;=%]+/)
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
        return read(yaml, shown(source ?: 'of an unknown origin', MAX_LINE), false)
    }

    /**
     * Reads the descriptor of a pack Forge has in itself. It is the same format with these things more,
     * each of which a compiled feature of the framework does and the pack of a plugin cannot:
     * <ul>
     * <li>a dependency can say {@code versionOfForge: true} in place of a version, and has the group and
     * version Forge knows for the artifact;</li>
     * <li>a build plugin can say {@code artifactOfForge: <artifact>} in place of a version, and has the
     * version Forge knows for that artifact;</li>
     * <li>{@code versionsOfForge} maps artifacts to versions: the templates of the pack have
     * {@code versionsOfForge}, the version Forge knows for each artifact or, when it knows none, the
     * one written here;</li>
     * <li>how the feature takes part in the selection without being asked for: {@code default} (when it
     * is selected without being named), {@code adds} (the features it adds once the defaults are
     * chosen), {@code order}, {@code visible} and {@code reliesOn};</li>
     * <li>{@code dataSource}, the settings of a database driver, and {@code dataSourceOfDriver} for the
     * feature that takes them; and {@code guideOfGrails}, a page of the documentation of Grails in
     * place of a link.</li>
     * </ul>
     *
     * <p>Everything else is read for every pack alike: {@code when}, a condition ({@link PackCondition}),
     * on a dependency, a platform, a build plugin or a file, and on the entries of a
     * {@code configuration} or the parts of a build extension written as a list; {@code build.blocks},
     * {@code build.propertyComments}, {@code readmeLinks} and {@code requiresJdk};
     * {@code environmentFile} on an entry of the configuration; {@code apply} on a build plugin;
     * {@code template: false} and {@code executable: true} on a file; and every scope of
     * {@link #SCOPES}.</p>
     *
     * @param yaml the text of the descriptor
     * @param source where the descriptor came from, for messages
     * @return the descriptor
     * @throws PackDescriptorException when the descriptor is not valid
     */
    PackDescriptor parseOfForge(String yaml, String source) {
        return read(yaml, shown(source ?: 'of an unknown origin', MAX_LINE), true)
    }

    private static PackDescriptor read(String yaml, String source, boolean ofForge) {
        Map<String, Object> root = load(yaml, source)
        Problems problems = new Problems()
        checkTree(root, '', problems)
        failOn(problems, source)
        // which keys are read depends on the kind: a preset has keys a feature has not, and the reverse
        boolean preset = !ofForge && root.get('kind') == PackPreset.KIND
        // before anything with a condition: a condition can ask for a parameter, which has to be one of these. A preset declares none
        List<PackParameter> parameters = preset ? Collections.<PackParameter>emptyList() : declaredParameters(root, problems)
        problems.setDeclared(parameters)
        rootKeys(root, ofForge ? KEYS.toList() + KEYS_OF_FORGE : preset ? PRESET_KEYS : KEYS.toList(), preset, problems)
        requireNumber(root, 'format', PackDescriptor.FORMAT, problems)
        int templateApi = requireTemplateApi(root, problems)
        String kind = requiredText(root, 'kind', '', MAX_LINE, problems)
        if (kind != null && ofForge && kind != FEATURE_KIND) {
            problems << "kind: must be '${FEATURE_KIND}', the only kind of a pack Forge has in itself, but is ${describe(kind)}".toString()
        } else if (kind != null && kind != FEATURE_KIND && kind != PackPreset.KIND) {
            problems << ("kind: must be '${FEATURE_KIND}' or '${PackPreset.KIND}', the kinds this version of Forge reads, " +
                    "but is ${describe(kind)}").toString()
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
        String category = preset ? Category.OTHER : text(root, 'category', '', MAX_LINE, problems) ?: Category.OTHER
        if (!categories.contains(category)) {
            problems << "category: must be one of ${categories.toSorted()}, but is ${describe(category)}".toString()
        }
        // the features a preset asks for are selected with it, as the requirements of a feature are
        List<String> requires = featureIds(root, preset ? 'features' : 'requires', name, problems)
        List<String> conflicts = featureIds(root, 'conflicts', name, problems)
        for (String both : requires.intersect(conflicts)) {
            problems << "conflicts: ${describe(both)} is also ${preset ? 'asked for' : 'required'}".toString()
        }
        PackPreset ofPreset = preset ? presetOf(root, problems) : null
        Map<String, Object> build = map(root, 'build', '', problems)
        unknownKeys(build, ['properties', 'extensions', 'blocks', 'propertyComments'], 'build.', problems)
        String oneOf = preset ? null : matching(text(root, 'oneOf', '', MAX_LINE, problems), GROUP_ID, 'oneOf',
                "must be the identifier of a group, such as 'database-driver' or 'com.acme:audit'", problems)
        Map<String, List<PackOfForge.Part>> extensionParts = new LinkedHashMap<>()
        List<PackOfForge.Settings> settings = []

        PackDescriptor descriptor = new PackDescriptor(
                name,
                text(root, 'title', '', MAX_LINE, problems) ?: name,
                text(root, 'description', '', MAX_PARAGRAPH, problems) ?: name,
                category,
                documentation(root, ofForge, problems),
                grails,
                forge,
                ofPreset != null ? EnumSet.of(ofPreset.applicationType) : preset ? EnumSet.noneOf(ApplicationType) : applicationTypes(root, problems),
                requires,
                conflicts,
                oneOf,
                dependencies(root, 'dependencies', true, ofForge, problems),
                dependencies(root, 'platforms', false, false, problems),
                buildPlugins(root, ofForge, problems),
                buildProperties(build, problems),
                buildExtensions(build, extensionParts, problems),
                configuration(root, settings, problems),
                files(root, problems),
                document(root.get('readme'), 'readme', problems),
                document(root.get('instructions'), 'instructions', problems),
                matching(text(root, 'thirdPartyDocumentation', '', MAX_PARAGRAPH, problems), URL, 'thirdPartyDocumentation',
                        "must be an http or https address, without spaces, quotes, '<', '>' or '\$'", problems),
                ofForge ? versionsOfForge(root, problems) : [:] as Map<String, String>,
                buildBlocks(build, problems),
                readmeLinks(root, problems),
                templateApi,
                // what was refused above as a key of the packs of Forge is not read here as well
                readOfForge(ofForge ? root : root.findAll { String key, Object value -> !KEYS_OF_FORGE.contains(key) } as Map<String, Object>,
                        build, name, oneOf, extensionParts, settings, problems),
                ofPreset,
                parameters)
        failOn(problems, source)
        return descriptor
    }

    /**
     * The options the feature takes: each with a name a template can read it by, a type, the value it has
     * when it is not given, which is one it can have, and what it is for.
     */
    private static List<PackParameter> declaredParameters(Map<String, Object> root, List<String> problems) {
        List<PackParameter> parameters = []
        Map<Integer, Map<String, Object>> entries = maps(root, 'parameters', problems)
        if (entries.size() > PackParameter.MAX) {
            problems << "parameters: can have ${PackParameter.MAX} parameters, but has ${entries.size()}".toString()
            return parameters
        }
        Set<String> names = new HashSet<>()
        entries.each { int index, Map<String, Object> entry ->
            String at = "parameters[${index}]."
            unknownKeys(entry, ['name', 'type', 'default', 'choices', 'description'], at, problems)
            String name = matching(requiredText(entry, 'name', at, MAX_LINE, problems), PARAMETER_NAME, at + 'name',
                    'must be a name a template can read, of at most 40 letters and digits, starting with a lower case letter', problems)
            if (name != null && !names.add(name)) {
                problems << "${at}name: ${describe(name)} is declared by an earlier entry too".toString()
                name = null
            }
            String type = requiredText(entry, 'type', at, MAX_LINE, problems)
            if (type != null && !PackParameter.TYPES.contains(type)) {
                problems << "${at}type: must be one of ${PackParameter.TYPES}, but is ${describe(type)}".toString()
                type = null
            }
            List<String> choices = []
            if (type == PackParameter.CHOICE) {
                texts(entry, 'choices', at, problems).each { int choice, String value ->
                    if (!PackParameter.isText(value)) {
                        problems << "${at}choices[${choice}]: must be one line of at most ${PackParameter.MAX_VALUE} characters, but is ${describe(value)}".toString()
                    } else {
                        choices << value
                    }
                }
                if (choices.size() < 2 || choices.size() > MAX_ENTRIES_OF_FORGE) {
                    problems << "${at}choices: must list 2 to ${MAX_ENTRIES_OF_FORGE} values a choice can have".toString()
                    type = null
                }
            } else if (entry.containsKey('choices')) {
                problems << "${at}choices: is for a parameter of the type ${PackParameter.CHOICE}".toString()
            }
            String description = text(entry, 'description', at, MAX_PARAGRAPH, problems)
            Object written = entry.get('default')
            Object byDefault = null
            if (written == null) {
                problems << "${at}default: is required: the value the parameter has when none is given".toString()
            } else if (type == PackParameter.BOOLEAN) {
                if (written instanceof Boolean) {
                    byDefault = written
                } else {
                    problems << "${at}default: must be true or false, but is ${describe(written)}".toString()
                }
            } else if (type != null) {
                if (!(written instanceof String)) {
                    problems << "${at}default: must be text, but is ${describe(written)}${quoteHint(written)}".toString()
                } else if (type == PackParameter.CHOICE ? !choices.contains(written) : !PackParameter.isText((String) written)) {
                    problems << (type == PackParameter.CHOICE ? "${at}default: must be one of ${choices}, but is ${describe(written)}" :
                            "${at}default: must be one line of text of at most ${PackParameter.MAX_VALUE} characters that is not blank and has no " +
                                    "control characters, but is ${describe(written)}").toString()
                } else {
                    byDefault = written
                }
            }
            if (name != null && type != null && byDefault != null) {
                parameters << new PackParameter(name, type, byDefault, choices, description)
            }
        }
        return parameters
    }

    /**
     * The keys of the descriptor itself. A key that only the other kind has is said to be of that kind,
     * so that a feature written with a key of a preset, or the reverse, is told what to write instead.
     */
    private static void rootKeys(Map<String, Object> root, List<String> known, boolean preset, List<String> problems) {
        Map<String, String> ofTheOtherKind = preset ? NOT_OF_A_PRESET : ONLY_OF_A_PRESET
        Map<String, Object> others = new LinkedHashMap<>()
        root.each { String key, Object value ->
            if (!known.contains(key) && ofTheOtherKind.containsKey(key)) {
                problems << "${key}: ${ofTheOtherKind.get(key)}".toString()
            } else {
                others.put(key, value)
            }
        }
        unknownKeys(others, known, '', problems)
    }

    /**
     * What a preset says that a feature does not: the one type of application it is for, which is
     * required, and the options it gives, each with a value the create commands take for it.
     */
    private static PackPreset presetOf(Map<String, Object> root, List<String> problems) {
        Map<String, ApplicationType> known = ApplicationType.values().collectEntries { ApplicationType type ->
            [PackPreset.nameOf(type), type]
        }
        String typeName = requiredText(root, 'applicationType', '', MAX_LINE, problems)
        ApplicationType type = typeName == null ? null : known.get(typeName)
        if (typeName != null && type == null) {
            problems << "applicationType: must be one of ${known.keySet().toSorted()}, but is ${describe(typeName)}".toString()
        }
        Map<String, Object> options = map(root, 'options', '', problems)
        unknownKeys(options, PackPreset.OPTIONS, 'options.', problems)
        GormImpl data = option(options, 'data', GormImpl.values().toList(), problems)
        ServletImpl servlet = option(options, 'servlet', ServletImpl.values().toList(), problems)
        DevelopmentReloading reloading = option(options, 'reloading', DevelopmentReloading.values().toList(), problems)
        JdkVersion jdk = null
        if (options.get('jdk') != null) {
            Object value = options.get('jdk')
            jdk = JdkVersion.values().find { JdkVersion each -> value instanceof Integer && each.majorVersion() == value }
            if (jdk == null) {
                problems << ("options.jdk: must be one of ${JdkVersion.values().collect { JdkVersion each -> each.majorVersion() }}, " +
                        "the versions of Java this version of Forge generates for, but is ${describe(value)}").toString()
            }
        }
        return type == null ? null : new PackPreset(type, data, servlet, reloading, jdk)
    }

    // the value of an option as the create commands take it: the name of the value in lower case
    private static <T extends Enum<T>> T option(Map<String, Object> options, String key, List<T> values, List<String> problems) {
        String value = text(options, key, 'options.', MAX_LINE, problems)
        if (value == null) {
            return null
        }
        List<String> names = []
        for (T each : values) {
            String written = each.name().toLowerCase(Locale.ROOT)
            if (written == value) {
                return each
            }
            names << written
        }
        problems << "options.${key}: must be one of ${names}, as --${key} takes it, but is ${describe(value)}".toString()
        return null
    }

    /**
     * The template API the templates of the pack are written against, which has to be one this version
     * of Forge renders: a template of a later one would refer to names there are not.
     */
    private static int requireTemplateApi(Map<String, Object> root, List<String> problems) {
        Object value = root.get('templateApi')
        if (value == null) {
            problems << "templateApi: is required, and this version of Forge renders the template APIs ${PackModel.APIS}".toString()
        } else if (!(value instanceof Integer) || !PackModel.APIS.contains(value)) {
            problems << ("templateApi: must be one of the template APIs this version of Forge renders, ${PackModel.APIS}, " +
                    "but is ${describe(value)}").toString()
        } else {
            return (Integer) value
        }
        return PackDescriptor.TEMPLATE_API
    }

    /**
     * The link to the documentation of the feature. A pack of Forge can name a page of the guide of
     * Grails in place of a link, and the link is then to that page in the documentation of the version
     * of Grails this Forge generates for, as it was for the compiled feature.
     */
    private static String documentation(Map<String, Object> root, boolean ofForge, List<String> problems) {
        String link = matching(text(root, 'documentation', '', MAX_PARAGRAPH, problems), URL, 'documentation',
                "must be an http or https address, without spaces, quotes, '<', '>' or '\$'", problems)
        if (!ofForge || !root.containsKey('guideOfGrails')) {
            return link
        }
        if (root.get('guideOfGrails') == null) {
            problems << 'guideOfGrails: is there without a page'
            return link
        }
        String page = matching(text(root, 'guideOfGrails', '', MAX_LINE, problems), GUIDE_PAGE, 'guideOfGrails',
                "must be a page of the documentation of Grails, such as 'guide/cache.html' or 'guide/REST.html#openApi'", problems)
        if (root.get('documentation') != null) {
            problems << 'guideOfGrails: is in place of documentation, and both are given'
        }
        return page == null ? link : GRAILS_DOCUMENTATION + VersionInfo.getDocumentationVersion() + '/' + page
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
                // by its full name too: a feature of another pack of that name is never beside this one
                if (PackFeature.nameOf(id) == name) {
                    problems << "${at}: must not name the feature itself, ${describe(name)}".toString()
                }
                ids << id
            }
        }
        return ids
    }

    private static List<PackDependency> dependencies(Map<String, Object> root, String key, boolean scoped, boolean ofForge,
                                                     List<String> problems) {
        List<PackDependency> dependencies = []
        Set<String> declared = new HashSet<>()
        maps(root, key, problems).each { int index, Map<String, Object> entry ->
            String at = "${key}[${index}]."
            List<String> keys = scoped ? ['scope', 'coordinates', 'version', 'excludes', 'capabilities'] : ['coordinates', 'version']
            unknownKeys(entry, ofForge ? keys + ['versionOfForge', WHEN] : keys + [WHEN], at, problems)
            Map<String, Scope> scopes = SCOPES
            boolean versionOfForge = ofForge && entry.containsKey('versionOfForge')
            if (versionOfForge && (entry.get('versionOfForge') != Boolean.TRUE || entry.get('version') != null)) {
                problems << "${at}versionOfForge: must be true, and then there is no version".toString()
            }
            String scope = scoped ? requiredText(entry, 'scope', at, MAX_LINE, problems) : null
            boolean scopeKnown = !scoped || scopes.containsKey(scope)
            if (scope != null && !scopeKnown) {
                problems << "${at}scope: must be one of ${scopes.keySet().toList()}, but is ${describe(scope)}".toString()
                scope = null
            }
            String coordinates = matching(requiredText(entry, 'coordinates', at, MAX_LINE, problems), COORDINATES, at + 'coordinates',
                    "must be 'group:artifact', with the version given separately", problems)
            List<String> parts = coordinates == null ? [null, null] as List<String> : coordinates.split(':').toList()
            if (coordinates != null && scopeKnown && !declared.add("${scope} ${coordinates}".toString())) {
                problems << "${at}coordinates: ${describe(coordinates)} is declared by an earlier entry too".toString()
            }
            List<String> excludes = []
            List<String> capabilities = []
            if (scoped) {
                texts(entry, 'excludes', at, problems).each { int excluded, String exclude ->
                    if (matching(exclude, EXCLUSION, "${at}excludes[${excluded}]", "must be 'group' or 'group:artifact'", problems) != null) {
                        excludes << exclude
                    }
                }
                // a capability selects a variant of the dependency, as the Jakarta EE variant of a library that has one
                texts(entry, 'capabilities', at, problems).each { int required, String capability ->
                    if (matching(capability, COORDINATES, "${at}capabilities[${required}]", "must be the capability 'group:name'", problems) != null) {
                        capabilities << capability
                    }
                }
            }
            dependencies << new PackDependency(scope, parts[0], parts[1], version(entry, at, problems), excludes, versionOfForge,
                    when(entry, at, problems), capabilities)
        }
        return dependencies
    }

    private static List<PackBuildPlugin> buildPlugins(Map<String, Object> root, boolean ofForge, List<String> problems) {
        List<PackBuildPlugin> plugins = []
        Set<String> declared = new HashSet<>()
        maps(root, 'buildPlugins', problems).each { int index, Map<String, Object> entry ->
            String at = "buildPlugins[${index}]."
            unknownKeys(entry, ofForge ? ['id', 'version', 'artifactOfForge', 'apply', WHEN] : ['id', 'version', 'apply', WHEN], at, problems)
            String id = matching(requiredText(entry, 'id', at, MAX_LINE, problems), PLUGIN_ID, at + 'id',
                    "must be the id of a Gradle plugin, such as 'com.acme.audit'", problems)
            if (id != null && !declared.add(id)) {
                problems << "${at}id: ${describe(id)} is declared by an earlier entry too".toString()
            }
            String artifact = !ofForge ? null : matching(text(entry, 'artifactOfForge', at, MAX_LINE, problems), ARTIFACT, at + 'artifactOfForge',
                    'must be the artifact Forge knows the version of the plugin by', problems)
            if (ofForge && entry.containsKey('artifactOfForge') && entry.get('artifactOfForge') == null) {
                problems << "${at}artifactOfForge: is there without an artifact".toString()
            }
            if (artifact != null && entry.get('version') != null) {
                problems << "${at}artifactOfForge: has the version Forge knows, and then there is no version".toString()
            }
            plugins << new PackBuildPlugin(id, version(entry, at, problems), artifact, flag(entry, 'apply', true, at, problems),
                    when(entry, at, problems))
        }
        return plugins
    }

    /**
     * The artifacts a pack of Forge has the versions of in its templates, each with the version to use
     * when Forge knows none.
     */
    private static Map<String, String> versionsOfForge(Map<String, Object> root, List<String> problems) {
        Map<String, String> versions = new LinkedHashMap<>()
        Map<String, Object> declared = map(root, 'versionsOfForge', '', problems)
        if (declared.size() > MAX_ENTRIES_OF_FORGE) {
            problems << "versionsOfForge: can name ${MAX_ENTRIES_OF_FORGE} artifacts, but names ${declared.size()}".toString()
            return versions
        }
        declared.each { String artifact, Object value ->
            if (!ARTIFACT.matcher(artifact).matches()) {
                problems << "versionsOfForge: is a mapping of artifacts to versions, but one artifact is ${describe(artifact)}".toString()
                return
            }
            String at = "versionsOfForge.${brief(artifact)}"
            String otherwise = matching(requiredText(declared, artifact, 'versionsOfForge.', MAX_LINE, problems),
                    VERSION, at, "must be one version, of letters, digits, '.', '-', '+' and '_', for when Forge knows none", problems)
            if (otherwise != null) {
                versions.put(artifact, otherwise)
            }
        }
        return versions
    }

    /**
     * The text a pack adds to the build file as it is, by the name of the place.
     */
    private static Map<String, String> buildBlocks(Map<String, Object> build, List<String> problems) {
        Map<String, String> blocks = new LinkedHashMap<>()
        map(build, 'blocks', 'build.', problems).each { String anchor, Object value ->
            if (!ANCHORS.containsKey(anchor)) {
                problems << "build.blocks: has text for ${ANCHORS.keySet().toList()}, but one place is ${describe(anchor)}".toString()
                return
            }
            String text = document(value, "build.blocks.${anchor}", problems)
            if (text != null) {
                blocks.put(anchor, text)
            } else if (value == null) {
                problems << "build.blocks.${anchor}: must be text, but is empty".toString()
            }
        }
        return blocks
    }

    /**
     * The links a pack adds to the readme. A label is written between the brackets of a
     * Markdown link, so it has none of its own.
     */
    private static List<PackLink> readmeLinks(Map<String, Object> root, List<String> problems) {
        List<PackLink> links = []
        Map<Integer, Map<String, Object>> entries = maps(root, 'readmeLinks', problems)
        if (entries.size() > MAX_ENTRIES_OF_FORGE) {
            problems << "readmeLinks: can have ${MAX_ENTRIES_OF_FORGE} links, but has ${entries.size()}".toString()
            return links
        }
        entries.each { int index, Map<String, Object> entry ->
            String at = "readmeLinks[${index}]."
            unknownKeys(entry, ['label', 'href'], at, problems)
            String label = matching(requiredText(entry, 'label', at, MAX_LINE, problems), LINK_LABEL, at + 'label',
                    "must be text without '[', ']', '<', '>' or a backslash", problems)
            String href = matching(requiredText(entry, 'href', at, MAX_PARAGRAPH, problems), URL, at + 'href',
                    "must be an http or https address, without spaces, quotes, '<', '>' or '\$'", problems)
            if (label != null && href != null) {
                links << new PackLink(label, href)
            }
        }
        return links
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

    /**
     * The build extensions. The name can have a call in it, as
     * {@code tasks.withType(Test).configureEach} has, and the body can be a list of parts, each with a
     * condition: those go to {@code parts}.
     */
    private static Map<String, String> buildExtensions(Map<String, Object> build, Map<String, List<PackOfForge.Part>> parts,
                                                       List<String> problems) {
        Map<String, String> extensions = new LinkedHashMap<>()
        Pattern step = IDENTIFIER_OR_CALL
        map(build, 'extensions', 'build.', problems).each { String name, Object value ->
            if (!name.split(/\./, -1).every { String part -> step.matcher(part).matches() }) {
                problems << "build.extensions: the name of an extension is one or more identifiers joined by '.', but one is ${describe(name)}".toString()
                return
            }
            if (value instanceof List) {
                parts.put(name, extensionParts((List<Object>) value, "build.extensions.${brief(name)}", problems))
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
            unknownKeys(entry, ['from', 'to', 'template', 'executable', WHEN], at, problems)
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
            files << new PackFile(from, to, !flag(entry, 'template', false, at, problems),
                    flag(entry, 'executable', true, at, problems), when(entry, at, problems))
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
    private static Map<String, Object> configuration(Map<String, Object> root, List<PackOfForge.Settings> conditional,
                                                     List<String> problems) {
        if (root.get('configuration') instanceof List) {
            // entries that are there when a condition holds, or go to the file of one environment
            maps(root, 'configuration', problems).each { int index, Map<String, Object> entry ->
                String at = "configuration[${index}]."
                unknownKeys(entry, [WHEN, 'environmentFile', 'values'], at, problems)
                String environment = matching(text(entry, 'environmentFile', at, MAX_LINE, problems), ENVIRONMENT, at + 'environmentFile',
                        "must be the name of an environment: letters, digits, '-' and '_', starting with a letter", problems)
                if (entry.get('values') == null) {
                    problems << "${at}values: is required".toString()
                }
                conditional << new PackOfForge.Settings(when(entry, at, problems), environment, settings(entry, 'values', at, problems))
            }
            return [:]
        }
        return settings(root, 'configuration', '', problems)
    }

    /**
     * A mapping of settings, of which those under {@code environments} are of one environment each.
     */
    private static Map<String, Object> settings(Map<String, Object> from, String key, String at, List<String> problems) {
        Map<String, Object> configuration = map(from, key, at, problems)
        if (!configuration.containsKey(ENVIRONMENTS)) {
            return configuration
        }
        Object environments = configuration.get(ENVIRONMENTS)
        if (!(environments instanceof Map)) {
            problems << "${at}${key}.${ENVIRONMENTS}: must be a mapping of the names of environments to their settings, but is ${describe(environments)}".toString()
            return configuration
        }
        ((Map<String, Object>) environments).each { String environment, Object settings ->
            if (!ENVIRONMENT.matcher(environment).matches()) {
                problems << "${at}${key}.${ENVIRONMENTS}: the name of an environment is letters, digits, '-' and '_', starting with a letter, but one is ${describe(environment)}".toString()
            } else if (!(settings instanceof Map)) {
                problems << "${at}${key}.${ENVIRONMENTS}.${environment}: must be a mapping of settings, but is ${describe(settings)}".toString()
            }
        }
        return configuration
    }

    /**
     * What a descriptor says of what its feature gives beside other features, and what only a pack Forge
     * has in itself says of how its feature is selected: of the pack of a plugin the keys of the
     * selection were refused before, and are not in {@code root}.
     */
    @SuppressWarnings('ParameterCount')
    private static PackOfForge readOfForge(Map<String, Object> root, Map<String, Object> build, String name, String oneOf,
                                           Map<String, List<PackOfForge.Part>> extensionParts, List<PackOfForge.Settings> conditional,
                                           List<String> problems) {
        PackCondition byDefault = null
        if (root.get('default') == Boolean.TRUE) {
            byDefault = PackCondition.ALWAYS
        } else if (root.get('default') instanceof Map) {
            byDefault = condition(root.get('default'), 'default', problems)
        } else if (root.containsKey('default')) {
            problems << "default: must be true, for a feature every application has, or the condition the feature is selected on, but is ${describe(root.get('default'))}".toString()
        }
        int order = FeaturePhase.DEFAULT.getOrder()
        String phase = text(root, 'order', '', MAX_LINE, problems)
        if (phase != null && !ORDERS.containsKey(phase)) {
            problems << "order: must be one of ${ORDERS.keySet().toList()}, but is ${describe(phase)}".toString()
        } else if (phase != null) {
            order = ORDERS.get(phase)
        }
        boolean visible = true
        if (root.get('visible') instanceof Boolean) {
            visible = (Boolean) root.get('visible')
        } else if (root.containsKey('visible')) {
            problems << "visible: must be true or false, but is ${describe(root.get('visible'))}".toString()
        }
        Integer requiresJdk = jdk(root.get('requiresJdk'))
        if (requiresJdk == null && root.containsKey('requiresJdk')) {
            problems << "requiresJdk: must be the number of a JDK, such as 25, but is ${describe(root.get('requiresJdk'))}".toString()
        }
        Map<String, Object> dataSource = null
        if (root.containsKey('dataSource')) {
            dataSource = settings(root, 'dataSource', '', problems)
            if (dataSource.isEmpty() && (root.get('dataSource') == null || root.get('dataSource') instanceof Map)) {
                problems << 'dataSource: must be the settings of the data source, as a configuration is written'
            }
            if (oneOf != PackFeature.DATABASE_DRIVERS) {
                problems << "dataSource: is for a feature of the group ${PackFeature.DATABASE_DRIVERS}, which oneOf does not name".toString()
            }
        }
        List<PackOfForge.Addition> reliesOn = additions(root, 'reliesOn', name, problems)
        reliesOn.eachWithIndex { PackOfForge.Addition each, int index ->
            if (!each.when.isOfApplicationType()) {
                problems << "reliesOn[${index}].when: can ask for the type of the application only".toString()
            }
        }
        List<String> comments = []
        texts(build, 'propertyComments', 'build.', problems).each { int index, String comment ->
            if (matching(comment, COMMENT, "build.propertyComments[${index}]", 'must be one line of printable ASCII characters', problems) != null) {
                comments << comment
            }
        }
        if (comments.size() > MAX_ENTRIES_OF_FORGE) {
            problems << "build.propertyComments: can have ${MAX_ENTRIES_OF_FORGE} lines, but has ${comments.size()}".toString()
        }
        PackOfForge read = new PackOfForge(byDefault, additions(root, 'adds', name, problems), order, visible, reliesOn, requiresJdk, dataSource,
                flag(root, 'dataSourceOfDriver', true, '', problems), comments, extensionParts, conditional)
        // a descriptor that says none of it has the one value of that
        return read == PackOfForge.NONE ? PackOfForge.NONE : read
    }

    /**
     * The features a descriptor names with a condition each: an entry is the name of a feature, or a
     * mapping of {@code feature} and {@code when}.
     */
    private static List<PackOfForge.Addition> additions(Map<String, Object> root, String key, String name, List<String> problems) {
        List<PackOfForge.Addition> additions = []
        Object value = root.get(key)
        if (value == null) {
            return additions
        }
        if (!(value instanceof List)) {
            problems << "${key}: must be a list, but is ${describe(value)}".toString()
            return additions
        }
        if (((List) value).size() > MAX_ENTRIES_OF_FORGE) {
            problems << "${key}: can name ${MAX_ENTRIES_OF_FORGE} features, but names ${((List) value).size()}".toString()
            return additions
        }
        Set<String> seen = new HashSet<>()
        ((List<Object>) value).eachWithIndex { Object each, int index ->
            String at = "${key}[${index}]"
            String feature = null
            PackCondition holds = PackCondition.ALWAYS
            if (each instanceof Map) {
                Map<String, Object> entry = (Map<String, Object>) each
                unknownKeys(entry, [PackCondition.FEATURE, WHEN], at + '.', problems)
                feature = requiredText(entry, PackCondition.FEATURE, at + '.', MAX_LINE, problems)
                holds = when(entry, at + '.', problems)
            } else {
                feature = line(each, at, MAX_LINE, problems)
            }
            feature = matching(feature, NAME, at, 'must name a feature of Forge', problems)
            if (feature == name) {
                problems << "${at}: must not name the feature itself, ${describe(name)}".toString()
            } else if (feature != null && !seen.add(feature)) {
                problems << "${at}: ${describe(feature)} is listed earlier too".toString()
            } else if (feature != null) {
                additions << new PackOfForge.Addition(feature, holds)
            }
        }
        return additions
    }

    private static List<PackOfForge.Part> extensionParts(List<Object> value, String at, List<String> problems) {
        List<PackOfForge.Part> parts = []
        if (value.isEmpty() || value.size() > MAX_ENTRIES_OF_FORGE) {
            problems << "${at}: must have 1 to ${MAX_ENTRIES_OF_FORGE} parts, but has ${value.size()}".toString()
            return parts
        }
        value.eachWithIndex { Object each, int index ->
            String place = "${at}[${index}]"
            if (!(each instanceof Map)) {
                problems << "${place}: must be a mapping of text and when, but is ${describe(each)}".toString()
                return
            }
            Map<String, Object> entry = (Map<String, Object>) each
            unknownKeys(entry, ['text', WHEN], place + '.', problems)
            String text = document(entry.get('text'), place + '.text', problems)
            if (entry.get('text') == null) {
                problems << "${place}.text: is required".toString()
            }
            PackCondition holds = when(entry, place + '.', problems)
            if (text != null) {
                parts << new PackOfForge.Part(text, holds)
            }
        }
        return parts
    }

    /**
     * A key that says one thing by being there: it can only have the one value.
     */
    private static boolean flag(Map<String, Object> entry, String key, boolean only, String at, List<String> problems) {
        if (!entry.containsKey(key)) {
            return false
        }
        if (!Objects.equals(entry.get(key), only)) {
            problems << "${at}${key}: must be ${only} or be left out, but is ${describe(entry.get(key))}".toString()
            return false
        }
        return true
    }

    /**
     * The condition of an entry, which always holds when the entry has none.
     */
    private static PackCondition when(Map<String, Object> entry, String at, List<String> problems) {
        if (!entry.containsKey(WHEN)) {
            return PackCondition.ALWAYS
        }
        return condition(entry.get(WHEN), at + WHEN, problems) ?: PackCondition.ALWAYS
    }

    /**
     * Reads a condition, see {@link PackCondition}. It has at most {@link #MAX_CONDITIONS} conditions in
     * all, nested at most {@link #MAX_CONDITION_DEPTH} deep.
     *
     * @return the condition, or {@code null} when it cannot be used, which is then a problem
     */
    private static PackCondition condition(Object value, String at, List<String> problems) {
        int[] left = [MAX_CONDITIONS] as int[]
        PackCondition condition = condition(value, at, 1, left, problems)
        if (left[0] < 0) {
            problems << "${at}: has more than ${MAX_CONDITIONS} conditions".toString()
            return null
        }
        return condition
    }

    @SuppressWarnings(['CyclomaticComplexity', 'MethodSize'])
    private static PackCondition condition(Object value, String at, int depth, int[] left, List<String> problems) {
        if (--left[0] < 0) {
            return null
        }
        if (!(value instanceof Map) || ((Map) value).size() != 1) {
            problems << "${at}: must be a condition, a mapping with one of the keys ${CONDITION_KEYS}, but is ${describe(value)}".toString()
            return null
        }
        if (depth > MAX_CONDITION_DEPTH) {
            problems << "${at}: has conditions more than ${MAX_CONDITION_DEPTH} deep".toString()
            return null
        }
        String key = (String) ((Map) value).keySet().first()
        Object asked = ((Map) value).get(key)
        String place = "${at}.${brief(key)}"
        switch (key) {
            case PackCondition.FEATURE:
                String feature = matching(line(asked, place, MAX_LINE, problems), NAME, place, 'must name a feature of Forge', problems)
                return feature == null ? null : PackCondition.feature(feature)
            case PackCondition.GROUP:
                String group = matching(line(asked, place, MAX_LINE, problems), GROUP_ID, place, 'must be the identifier of an exclusive group', problems)
                return group == null ? null : new PackCondition(PackCondition.GROUP, null, [group], [])
            case PackCondition.NOT:
                PackCondition negated = operand(asked, place, depth, left, problems)
                return negated == null ? null : new PackCondition(PackCondition.NOT, null, [], [negated])
            case PackCondition.ANY_OF:
            case PackCondition.ALL_OF:
                if (!(asked instanceof List) || ((List) asked).isEmpty()) {
                    problems << "${place}: must be a list of conditions and names of features, but is ${describe(asked)}".toString()
                    return null
                }
                List<PackCondition> conditions = []
                boolean usable = true
                ((List<Object>) asked).eachWithIndex { Object each, int index ->
                    PackCondition operand = left[0] < 0 ? null : operand(each, "${place}[${index}]", depth, left, problems)
                    usable = usable && operand != null
                    if (operand != null) {
                        conditions << operand
                    }
                }
                return usable ? new PackCondition(key, null, [], conditions) : null
            case PackCondition.APPLICATION_TYPE:
                List<String> known = ApplicationType.values().collect { ApplicationType type -> PackCondition.typeName(type) }
                List<String> types = oneOrMore(asked, place, known, problems)
                return types == null ? null : new PackCondition(key, null, types, [])
            case PackCondition.OPTION:
                if (!(asked instanceof Map) || ((Map) asked).size() != 1 || !PackCondition.OPTIONS.containsKey(((Map) asked).keySet().first())) {
                    problems << "${place}: must be one of the options ${PackCondition.OPTIONS.keySet().toList()} with a value or a list of values, but is ${describe(asked)}".toString()
                    return null
                }
                String option = (String) ((Map) asked).keySet().first()
                List<String> values = oneOrMore(((Map) asked).get(option), "${place}.${option}", PackCondition.OPTIONS.get(option), problems)
                return values == null ? null : new PackCondition(key, option, values, [])
            case PackCondition.PARAMETER:
                return parameterCondition(asked, place, problems)
            case PackCondition.JDK_AT_LEAST:
                Integer jdk = jdk(asked)
                if (jdk == null) {
                    problems << "${place}: must be the number of a JDK, such as 25, but is ${describe(asked)}".toString()
                    return null
                }
                return new PackCondition(key, null, [jdk.toString()], [])
            default:
                problems << "${at}: has the key ${describe(key)}, and a condition has one of the keys ${CONDITION_KEYS}".toString()
                return null
        }
    }

    // a parameter the pack declares, with a value it can have or a list of them
    private static PackCondition parameterCondition(Object asked, String place, List<String> problems) {
        List<PackParameter> declared = problems instanceof Problems ? ((Problems) problems).getDeclared() : Collections.<PackParameter>emptyList()
        String name = asked instanceof Map && ((Map) asked).size() == 1 ? String.valueOf(((Map) asked).keySet().first()) : null
        PackParameter parameter = declared.find { PackParameter each -> each.name == name }
        if (parameter == null) {
            problems << ("${place}: must be one of the parameters the pack declares, ${declared*.name}, with a value or a list of values, " +
                    "but is ${describe(asked)}").toString()
            return null
        }
        Object value = ((Map) asked).get(name)
        List<Object> given = value instanceof List ? (List<Object>) value : [value]
        List<String> values = []
        for (Object each : given) {
            String written = each instanceof Boolean || each instanceof String ? each.toString() : null
            if (written == null || parameter.refusal(written) != null) {
                problems << "${place}.${brief(name)}: ${parameter.refusal(written ?: '')}, or a list of such values, but is ${describe(value)}".toString()
                return null
            }
            values << written
        }
        if (values.isEmpty() || values.size() > MAX_ENTRIES_OF_FORGE) {
            problems << "${place}.${brief(name)}: must be a value or a list of 1 to ${MAX_ENTRIES_OF_FORGE} values, but is ${describe(value)}".toString()
            return null
        }
        return new PackCondition(PackCondition.PARAMETER, name, values, [])
    }

    private static Integer jdk(Object value) {
        if (value instanceof Integer) {
            int number = (Integer) value
            return number >= MIN_JDK && number <= MAX_JDK ? (Integer) number : null
        }
        return null
    }

    // what anyOf, allOf and not are said of: the name of a feature, or a condition
    private static PackCondition operand(Object value, String at, int depth, int[] left, List<String> problems) {
        if (value instanceof String) {
            if (--left[0] < 0) {
                return null
            }
            String feature = matching(line(value, at, MAX_LINE, problems), NAME, at, 'must name a feature of Forge', problems)
            return feature == null ? null : PackCondition.feature(feature)
        }
        return condition(value, at, depth + 1, left, problems)
    }

    // one value or a list of values, each of the known ones
    private static List<String> oneOrMore(Object value, String at, List<String> known, List<String> problems) {
        List<Object> given = value instanceof List ? (List<Object>) value : [value]
        if (given.isEmpty() || given.size() > known.size() || !given.every { Object each -> each instanceof String && known.contains(each) }) {
            problems << "${at}: must be one of ${known}, or a list of them, but is ${describe(value)}".toString()
            return null
        }
        return given.collect { Object each -> (String) each }
    }

    private static String version(Map<String, Object> entry, String at, List<String> problems) {
        return matching(text(entry, 'version', at, MAX_LINE, problems), VERSION, at + 'version',
                "must be one version, of letters, digits, '.', '-', '+' and '_'", problems)
    }

    private static String versionRange(Map<String, Object> compatibility, String key, List<String> problems) {
        String range = matching(requiredText(compatibility, key, 'compatibility.', MAX_LINE, problems), VERSION_RANGE, 'compatibility.' + key,
                "must be a version or a range of versions, such as '[9.0.0,10.0.0)'", problems)
        if (range != null) {
            try {
                PackVersionRange.parse(range)
            } catch (IllegalArgumentException e) {
                problems << "compatibility.${key}: ${e.message}, but is ${describe(range)}".toString()
                return null
            }
        }
        return range
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
     * @param text a text that is read from a file nobody has checked
     * @return whether it has a character that a name, a key or an address cannot have: a control
     * character, a format character, which is not shown, half of a pair, a character of private use
     * or a line or paragraph separator
     * @since 9.0
     */
    static boolean hasUnsafeCharacter(String text) {
        return UNSAFE_CHARACTER.matcher(text).find()
    }

    /**
     * @param step one step of a path
     * @return whether a file or a directory of that name cannot be made on Windows, whatever comes
     * after its first dot: the name of a device, such as {@code aux} or {@code com1}
     * @since 9.0
     */
    static boolean isDeviceName(String step) {
        return !step.equalsIgnoreCase('.git') && RESERVED_STEP.matcher(step).matches()
    }

    /**
     * At most {@code limit} characters of a text, with the characters that are not safe to show replaced.
     */
    static String shown(String text, int limit) {
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

        // the parameters the descriptor declares, which its conditions are read against
        private List<PackParameter> declaredParameters = []

        List<PackParameter> getDeclared() {
            return this.@declaredParameters
        }

        void setDeclared(List<PackParameter> declared) {
            this.@declaredParameters = declared
        }
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
