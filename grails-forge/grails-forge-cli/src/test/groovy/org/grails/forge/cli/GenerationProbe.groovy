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
package org.grails.forge.cli

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * The program {@link GenerationReproducibilitySpec} starts in a virtual machine of its own: a fixed
 * list of command lines of the CLI, run one after another in the directory it is given, with what
 * each writes to the standard output kept in a file. The locale, the time zone, the character set,
 * the line separator and the directory are those of the virtual machine it is started in, which is
 * what the specification varies.
 *
 * <p>A scenario is a directory under {@code projects} and the command lines that are run in it. In a
 * command line {@code PACKS} stands for the directory of the feature packs the probe writes.</p>
 */
class GenerationProbe {

    static final String FEATURES = 'grails-quartz,asciidoctor,database-migration,grails-spring-security,openapi,postgres,views-json'
    static final String FEATURES_REORDERED = 'views-json,postgres,openapi,grails-spring-security,database-migration,asciidoctor,grails-quartz'

    /** The scenarios by name: each a list of command lines, the first entry of which is the directory under that of the scenario it is run in */
    static final Map<String, List<List<String>>> SCENARIOS = [
            'default'             : [['', 'create-app', 'com.example.shop']],
            // a name that the upper and lower case of some languages turn into other letters
            'turkish'             : [['', 'create-app', 'com.example.inventoryIi'],
                                     ['inventoryIi', 'create-controller', 'itemInfo'],
                                     ['inventoryIi', 'create-domain-class', 'com.example.stock.itemInfo'],
                                     ['inventoryIi', 'create-service', 'InvoiceIssue']],
            'features'            : [['', 'create-app', 'com.example.shop', '--features', FEATURES]],
            'features-reordered'  : [['', 'create-app', 'com.example.shop', '--features', FEATURES_REORDERED]],
            'options'             : [['', 'create-app', 'com.example.shop', '--servlet', 'undertow', '--data', 'mongodb', '--jdk', '25', '--reloading', 'none',
                                      '--exclude-features', 'mockito,scaffolding', '--features', 'cache,grails-web-console']],
            'options-reordered'   : [['', 'create-app', 'com.example.shop', '--features', 'grails-web-console,cache', '--exclude-features', 'scaffolding,mockito',
                                      '--reloading', 'none', '--jdk', '25', '--data', 'mongodb', '--servlet', 'undertow']],
            'webapp'              : [['', 'create-webapp', 'com.example.shop']],
            'rest-api'            : [['', 'create-restapi', 'com.example.shop', '--features', 'openapi,grails-quartz']],
            'plugin'              : [['', 'create-plugin', 'com.example.shop', '--features', 'asciidoctor']],
            'web-plugin'          : [['', 'create-web-plugin', 'com.example.shop']],
            'rest-api-plugin'     : [['', 'create-rest-api-plugin', 'com.example.shop']],
            'added-in-sequence'   : [['', 'create-app', 'com.example.shop'],
                                     ['shop', 'feature', 'add', 'asciidoctor'],
                                     ['shop', 'feature', 'add', 'grails-quartz'],
                                     ['shop', 'feature', 'add', 'database-migration']],
            'added-at-once'       : [['', 'create-app', 'com.example.shop'],
                                     ['shop', 'feature', 'add', 'asciidoctor,grails-quartz,database-migration']],
            'added-reordered'     : [['', 'create-app', 'com.example.shop'],
                                     ['shop', 'feature', 'add', 'database-migration,grails-quartz,asciidoctor']],
            'created-with-added'  : [['', 'create-app', 'com.example.shop', '--features', 'grails-quartz,database-migration,asciidoctor']],
            'packs'               : [['', 'create-app', 'com.example.shop', '--feature-pack-dir', 'PACKS/first', '--feature-pack-dir', 'PACKS/second',
                                      '--features', 'acme-first,acme-second,grails-quartz']],
            'packs-reordered'     : [['', 'create-app', 'com.example.shop', '--features', 'grails-quartz,acme-second,acme-first',
                                      '--feature-pack-dir', 'PACKS/second', '--feature-pack-dir', 'PACKS/first']],
            'regenerated'         : [['', 'create-app', 'com.example.shop', '--features', 'grails-quartz,openapi'],
                                     ['shop', 'project', 'regenerate', '--output', '../again']],
            'migrated'            : [['', 'create-app', 'com.example.shop', '--features', 'grails-quartz,postgres'],
                                     ['shop', 'FORGET'],
                                     ['shop', 'project', 'migrate'],
                                     ['shop', 'project', 'show']],
            'listings'            : [['', 'create-app', '--list-features'],
                                     ['', 'create-restapi', '--list-features'],
                                     ['', 'create-plugin', '--list-features'],
                                     ['', 'list-plugins'],
                                     ['', 'list-plugins', '--json'],
                                     ['', 'help', '--json'],
                                     ['', '--help'],
                                     ['', 'create-app', '--help'],
                                     ['', 'create-app', 'com.example.shop', '--features', FEATURES],
                                     ['shop', 'project', 'show'],
                                     ['shop', 'project', 'show', '--json'],
                                     ['shop', 'help', '--json'],
                                     ['shop', 'feature', 'add', 'cache,grails-web-console', '--dry-run'],
                                     ['shop', 'feature', 'add', 'grails-web-console,cache', '--diff']]
    ].asImmutable()

    /** What each of two packs writes: a dependency, lines of one block of the build, settings and a file, at the same place among the features */
    private static String descriptor(String name, String key) {
        return "format: 1\nkind: feature\ntemplateApi: 1\nname: acme-${name}\n" +
                "compatibility:\n  grails: '[1.0.0,99.0.0)'\n  forge: '[1.0.0,99.0.0)'\n" +
                "title: Acme ${name}\ndescription: Adds the ${name} part of Acme\n" +
                "dependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-${name}\n    version: 1.2.3\n" +
                "  - scope: testImplementation\n    coordinates: com.acme:acme-testing\n    version: 1.2.3\n" +
                "configuration:\n  acme.${key}: true\n  acme.shared.${name}: 1\n" +
                "buildPlugins:\n  - id: com.acme.${key}\n    version: 1.2.3\n" +
                "build:\n  properties:\n    acme${key.capitalize()}: 1\n  extensions:\n    acme: ${key} = true\n" +
                "readme: Acme ${name} is here.\n" +
                "files:\n  - from: note.txt\n    to: 'src/main/resources/acme/${name}.txt'\n" +
                "  - from: model.txt.gsp\n    to: 'src/main/resources/acme/${name}-model.txt'\n"
    }

    static void main(String[] args) {
        File root = new File(args[0]).canonicalFile
        File packs = new File(root, 'packs')
        ['first', 'second'].each { String name ->
            File pack = new File(packs, name)
            new File(pack, 'templates').mkdirs()
            new File(pack, 'feature.yml').setText(descriptor(name, name == 'first' ? 'zeta' : 'alpha'), 'UTF-8')
            new File(pack, 'templates/note.txt').setText("of ${name}\n", 'UTF-8')
            new File(pack, 'templates/model.txt.gsp').setText('${project.className}|${project.packagePath}|${features.join(",")}|café\n', 'UTF-8')
        }
        File output = new File(root, 'output')
        output.mkdirs()
        // what the virtual machine was started with, for the specification to see that it is what it asked for
        new File(root, 'environment.txt').setText([
                "locale=${Locale.getDefault().toLanguageTag()}",
                "timezone=${TimeZone.getDefault().getID()}",
                "charset=${Charset.defaultCharset().name()}",
                "lineSeparator=${System.lineSeparator().bytes.encodeHex()}"
        ].join('\n'), 'UTF-8')
        PrintStream out = System.out
        PrintStream err = System.err
        String directory = System.getProperty('user.dir')
        List<String> exits = []
        ForgeContext context = new ForgeContext()
        try {
            SCENARIOS.each { String scenario, List<List<String>> commands ->
                File projects = new File(root, "projects/${scenario}")
                projects.mkdirs()
                commands.eachWithIndex { List<String> command, int index ->
                    File at = command[0] ? new File(projects, command[0]) : projects
                    if (command[1] == 'FORGET') {
                        new File(at, '.grails').deleteDir()
                        return
                    }
                    String[] line = command.drop(1).collect { String each -> each.replace('PACKS', packs.path) } as String[]
                    ByteArrayOutputStream said = new ByteArrayOutputStream()
                    PrintStream stream = new PrintStream(said, true, StandardCharsets.UTF_8)
                    System.setOut(stream)
                    System.setErr(stream)
                    System.setProperty('user.dir', at.path)
                    int exit
                    try {
                        exit = ForgeCli.execute(context, line)
                    } finally {
                        System.setOut(out)
                        System.setErr(err)
                        System.setProperty('user.dir', directory)
                    }
                    stream.flush()
                    new File(output, "${scenario}-${index}.txt").bytes = said.toByteArray()
                    exits << "${scenario}-${index}=${exit}".toString()
                }
            }
        } finally {
            context.close()
        }
        new File(root, 'exits.txt').setText(exits.join('\n'), 'UTF-8')
        // whatever a command left running does not keep the virtual machine
        System.exit(0)
    }
}
