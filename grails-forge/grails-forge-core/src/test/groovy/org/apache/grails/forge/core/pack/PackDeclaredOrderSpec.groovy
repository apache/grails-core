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

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.yaml.snakeyaml.Yaml
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The maps of the configuration whose order Grails reads as meaning are generated in the order a
 * feature pack declares them in: the first MIME type is the one Grails takes for a request that says
 * nothing of its content, and the first CORS mapping that matches is the one that counts.
 */
class PackDeclaredOrderSpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    @Unroll
    void "the MIME types and the CORS mappings of a feature pack are generated in the order the pack has them in, for an application of the type #type"() {
        given: 'a pack that has JSON before HTML, and the mapping of the API before the one of everything'
        Pack pack = pack('acme-api', '''\
configuration:
  grails:
    mime:
      types:
        json:
          - application/json
          - text/json
        html:
          - text/html
          - application/xhtml+xml
        csv: text/csv
      disable:
        accept:
          header:
            userAgents: []
    cors:
      enabled: true
      mappings:
        '/api/**':
          allowedOrigins:
            - https://example.org
          allowedMethods:
            - GET
        '/**':
          allowedOrigins:
            - '*'
''')

        when:
        Path project = create(type, 'app', [pack], ['acme-api'])
        String written = project.resolve('grails-app/conf/application.yml').toFile().text
        Map<String, Object> grails = new Yaml().load(written).grails as Map<String, Object>

        then: 'the entries are in the order of the pack'
        (grails.mime.types as Map).keySet().toList() == ['json', 'html', 'csv']
        (grails.cors.mappings as Map).keySet().toList() == ['/api/**', '/**']

        and: 'what is around them and below them is in the order every generated file has'
        (grails.mime as Map).keySet().toList() == ['disable', 'types']
        (grails.cors.mappings['/api/**'] as Map).keySet().toList() == ['allowedMethods', 'allowedOrigins']
        grails.keySet().toList() == grails.keySet().toList().sort(false, String.CASE_INSENSITIVE_ORDER)

        where:
        type << [ApplicationType.WEB, ApplicationType.REST_API]
    }

    void "the MIME types of two feature packs are those of the pack that is applied first and then those of the other, and adding the second leaves the lines of the first where they were"() {
        given:
        Pack first = pack('acme-api', 'configuration:\n  grails.mime.types.json: application/json\n  grails.mime.types.html: text/html\n')
        Pack second = pack('acme-csv', 'configuration:\n  grails.mime.types.csv: text/csv\n  grails.mime.types.atom: application/atom+xml\n')

        when:
        List<String> one = create(ApplicationType.WEB, 'one', [first], ['acme-api']).resolve('grails-app/conf/application.yml').toFile().readLines()
        List<String> both = create(ApplicationType.WEB, 'both', [first, second], ['acme-api', 'acme-csv']).resolve('grails-app/conf/application.yml').toFile().readLines()
        List<String> types = (new Yaml().load(both.join('\n')).grails.mime.types as Map).keySet().toList()

        then: 'each pack has its entries in its order'
        types.indexOf('json') < types.indexOf('html')
        types.indexOf('csv') < types.indexOf('atom')

        and: 'the file with both is the file with one and the lines of the other'
        both - ['      csv: text/csv', '      atom: application/atom+xml'] == one
        both.size() == one.size() + 2
    }

    private Pack pack(String name, String more) {
        Path pack = Files.createDirectories(directory.resolve("packs/${name}"))
        Files.writeString(pack.resolve('feature.yml'), "format: 1\nkind: feature\ntemplateApi: 1\nname: ${name}\n" +
                "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" + more.stripIndent())
        return new PackLoader().fromDirectory(pack)
    }

    private Path create(ApplicationType type, String into, List<Pack> packs, List<String> features) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Path project = Files.createDirectory(directory.resolve(into))
        Project named = NameUtils.parse('com.acme.shop')
        GeneratorContext context = generator.createGeneratorContext(type, named, new Options(), OperatingSystem.LINUX, features, ConsoleOutput.NOOP, packs)
        generator.generate(type, named, new FileSystemOutputHandler(project.toFile(), ConsoleOutput.NOOP), context)
        return project
    }
}
