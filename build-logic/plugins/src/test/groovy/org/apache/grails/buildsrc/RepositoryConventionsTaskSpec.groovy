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
package org.apache.grails.buildsrc

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class RepositoryConventionsTaskSpec extends Specification {

    private static final String SHA = '0123456789abcdef0123456789abcdef01234567'

    @TempDir
    Path testProjectDir

    def "validateRepositoryConventions passes valid conventions, runs RAT, and is included by aggregateViolations"() {
        given:
        writeBuild(true)
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")
        writeProperties('src/main/resources/messages.properties', '''escaped\\=key=one
continued\\
 key=one
''')
        testProjectDir.resolve('build/reports/code-analysis').toFile().mkdirs()

        when:
        def result = run('aggregateViolations')

        then:
        result.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
        result.task(':rat').outcome == TaskOutcome.SUCCESS
        testProjectDir.resolve('build/rat-ran').toFile().exists()
        testProjectDir.resolve('build/reports/violations/REPOSITORY_CONVENTIONS.md').toFile().text.contains('No violations found!')
    }

    def "validateRepositoryConventions reports skill metadata and AGENTS inventory failures"() {
        given:
        writeBuild()
        writeSkill('expected-name', 'different-name', false)
        writeSkill('duplicate-name', 'different-name')
        writeAgents('.agents/skills/missing/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("skill front matter is missing 'license'")
        result.output.contains("skill name 'different-name' does not match directory 'expected-name'")
        result.output.contains("skill name 'different-name' duplicates .agents/skills/duplicate-name/SKILL.md")
        result.output.contains("missing canonical skill path '.agents/skills/expected-name/SKILL.md'")
        result.output.contains("skill path '.agents/skills/missing/SKILL.md' does not exist")
    }

    def "validateRepositoryConventions parses quoted, inline, nested, and composite action uses"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('valid.yml', """jobs:
  reusable:
    "uses": actions/reusable@${SHA}
  build:
    steps:
      - { "uses": actions/checkout@${SHA} }
      - uses: docker://alpine@sha256:${'a' * 64}
      - uses: ./.github/actions/sample
""")
        writeCompositeAction('sample', """runs:
  using: composite
  steps:
    - uses: actions/setup-java@${SHA}
""", 'yaml')

        when:
        def result = run('validateRepositoryConventions')

        then:
        result.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
    }

    def "validateRepositoryConventions ignores non-semantic uses fields"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('valid.yml', """jobs:
  reusable:
    uses: actions/reusable@${SHA}
  build:
    env:
      uses: mutable-value
    steps:
      - uses: actions/checkout@${SHA}
        with:
          uses: mutable-value
        env:
          uses: mutable-value
steps:
  - uses: actions/cache@${SHA}
""")
        writeCompositeAction('sample', """inputs:
  uses:
    description: An ordinary action input
runs:
  using: composite
  steps:
    - uses: actions/setup-java@${SHA}
""")

        when:
        def result = run('validateRepositoryConventions')

        then:
        result.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
    }

    def "validateRepositoryConventions accepts immutable Docker action and workflow container images"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('valid.yml', """jobs:
  string-container:
    container: alpine@sha256:${'a' * 64}
    env:
      image: mongo:8
    services:
      redis:
        image: redis@sha256:${'b' * 64}
  map-container:
    container:
      image: mongo@sha256:${'c' * 64}
  build:
    steps:
      - uses: ./.github/actions/docker
      - uses: ./.github/actions/build
""")
        writeCompositeAction('docker', """runs:
  using: docker
  image: docker://alpine@sha256:${'d' * 64}
""")
        writeActionManifest('.github/actions/build', '''runs:
  using: docker
  image: Dockerfile
''')

        when:
        def result = run('validateRepositoryConventions')

        then:
        result.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
    }

    def "validateRepositoryConventions rejects mutable and malformed Docker action and workflow container images"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('invalid.yml', '''jobs:
  string-container:
    container: alpine:3
    services:
      redis:
        image: redis:7
  map-container:
    container:
      image: mongo:8
  malformed-container:
    container: []
    services:
      postgres:
        image: [postgres]
  build:
    steps:
      - uses: ./.github/actions/docker
      - uses: ./.github/actions/build
      - uses: ./.github/actions/malformed
''')
        writeCompositeAction('docker', '''runs:
  using: Docker
  image: docker://alpine:3
''')
        writeActionManifest('.github/actions/build', '''runs:
  using: docker
  image: Docker://redis:7
''')
        writeCompositeAction('malformed', '''runs:
  using: docker
  image: [Dockerfile]
''')

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains(".github/workflows/invalid.yml:\$.jobs.string-container.container: container image 'alpine:3' must use an immutable sha256 digest")
        result.output.contains(".github/workflows/invalid.yml:\$.jobs.string-container.services.redis.image: container image 'redis:7' must use an immutable sha256 digest")
        result.output.contains(".github/workflows/invalid.yml:\$.jobs.map-container.container.image: container image 'mongo:8' must use an immutable sha256 digest")
        result.output.contains('.github/workflows/invalid.yml:$.jobs.malformed-container.container: container image must be a string')
        result.output.contains('.github/workflows/invalid.yml:$.jobs.malformed-container.services.postgres.image: container image must be a string')
        result.output.contains(".github/actions/docker/action.yml:\$.runs.image: Docker action image 'docker://alpine:3' must use an immutable sha256 digest")
        result.output.contains(".github/actions/build/action.yml:\$.runs.image: Docker action image 'Docker://redis:7' must use an immutable sha256 digest")
        result.output.contains('.github/actions/malformed/action.yml:$.runs.image: Docker action image must be a string')
    }

    def "validateRepositoryConventions rejects mutable action and Docker references across workflows and composite actions"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('one.yml', """jobs:
  reusable:
    uses: actions/reusable@v4
  build:
    steps:
      - { "uses": actions/checkout@v4 }
      - uses: ./local-action
      - uses: docker://alpine:3
      - uses: docker://registry@invalid/alpine@sha256:${'b' * 64}
      - uses: actions/checkout@${SHA}
      - uses: actions/cache@${SHA.toUpperCase()}
""")
        writeWorkflow('two.yml', "uses: actions/checkout@${'f' * 40}")
        writeCompositeAction('sample', '''runs:
  using: composite
  steps:
    - uses: actions/setup-java@v4
''', 'yaml')
        writeCompositeAction('legacy', '''runs:
  using: composite
  steps:
    - uses: actions/setup-node@v4
''')

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("action 'actions/reusable' uses 'v4'")
        result.output.contains("action 'actions/checkout' uses 'v4'")
        result.output.contains("action 'actions/cache' uses '${SHA.toUpperCase()}'")
        result.output.contains("Docker action 'docker://alpine:3' must use an immutable sha256 digest")
        result.output.contains("Docker action 'docker://registry@invalid/alpine@sha256:${'b' * 64}' must use an immutable sha256 digest")
        result.output.contains(".github/actions/sample/action.yaml:\$.runs.steps[0].uses: action 'actions/setup-java' uses 'v4'")
        result.output.contains(".github/actions/legacy/action.yml:\$.runs.steps[0].uses: action 'actions/setup-node' uses 'v4'")
        result.output.contains("action 'actions/checkout' uses ${'f' * 40}, inconsistent with ${SHA}")
        !result.output.contains('local-action')
    }

    def "validateRepositoryConventions reports malformed YAML with its file"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('broken.yaml', 'jobs: [')

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains('.github/workflows/broken.yaml: malformed YAML:')
    }

    def "validateRepositoryConventions rejects duplicate uses keys as malformed YAML"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflowContent('duplicate.yml', """steps:
  - uses: actions/checkout@${SHA}
    uses: actions/setup-java@v4
""")

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains('.github/workflows/duplicate.yml: malformed YAML:')
        result.output.contains('found duplicate key uses')
    }

    def "validateRepositoryConventions scans local composite manifests outside .github actions"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', 'uses: ./tools/actions/sample')
        writeWorkflow('build-path.yml', 'uses: ./.github/actions/build')
        writeActionManifest('tools/actions/sample', '''runs:
  using: composite
  steps:
    - uses: actions/setup-java@v4
''')
        writeActionManifest('.github/actions/build', '''runs:
  using: composite
  steps:
    - uses: actions/setup-node@v4
''')

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("tools/actions/sample/action.yml:\$.runs.steps[0].uses: action 'actions/setup-java' uses 'v4'")
        result.output.contains(".github/actions/build/action.yml:\$.runs.steps[0].uses: action 'actions/setup-node' uses 'v4'")
        !result.output.contains('./tools/actions/sample')
    }

    def "validateRepositoryConventions rejects local action paths outside the repository"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('escape.yml', 'uses: ./../outside')

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("local action './../outside' resolves outside the repository")
    }

    def "validateRepositoryConventions sanitizes decoded property keys in reports and exceptions"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")
        writeProperties('src/main/resources/messages.properties', '''injected\\r\\n|forged=one
injected\\r\\n|forged=two
''')

        when:
        def result = runAndFail('validateRepositoryConventions')
        def report = testProjectDir.resolve('build/reports/violations/REPOSITORY_CONVENTIONS.md').toFile().text

        then:
        result.output.contains('injected\\r\\n\\|forged')
        report.contains('injected\\r\\n\\|forged')
        !report.contains('\r')
    }

    def "validateRepositoryConventions requires skill front matter to begin on line one"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        def skill = testProjectDir.resolve('.agents/skills/sample/SKILL.md').toFile()
        skill.text = "<!-- license header -->\n${skill.text}"
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("skill front matter is missing 'name'")
        result.output.contains("skill front matter is missing 'description'")
        result.output.contains("skill front matter is missing 'license'")
    }

    def "validateRepositoryConventions accepts quoted and block scalar skill metadata"() {
        given:
        writeBuild()
        writeSkillContent('sample', '''---
name: "sample"
description: |-
  Test skill
  with multiple lines
license: 'Apache-2.0'
---
''')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")

        when:
        def result = run('validateRepositoryConventions')

        then:
        result.task(':validateRepositoryConventions').outcome == TaskOutcome.SUCCESS
    }

    def "validateRepositoryConventions rejects nested skill metadata fields"() {
        given:
        writeBuild()
        writeSkillContent('sample', '''---
metadata:
  name: sample
  description: Test skill
  license: Apache-2.0
---
''')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("skill front matter is missing 'name'")
        result.output.contains("skill front matter is missing 'description'")
        result.output.contains("skill front matter is missing 'license'")
    }

    def "validateRepositoryConventions rejects malformed and duplicate skill front matter"() {
        given:
        writeBuild()
        writeSkillContent('malformed', '''---
name: [
---
''')
        writeSkillContent('duplicate', '''---
name: duplicate
name: duplicate-again
description: Test skill
license: Apache-2.0
---
''')
        writeAgents('.agents/skills/malformed/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains('.agents/skills/malformed/SKILL.md: malformed skill front matter:')
        result.output.contains('.agents/skills/duplicate/SKILL.md: malformed skill front matter:')
        result.output.contains('found duplicate key name')
    }

    def "validateRepositoryConventions rejects non-string metadata and non-mapping front matter"() {
        given:
        writeBuild()
        writeSkillContent('typed', '''---
name: [typed]
description: Test skill
license: Apache-2.0
---
''')
        writeSkillContent('root', '''---
- name: root
---
''')
        writeAgents('.agents/skills/typed/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains(".agents/skills/typed/SKILL.md: skill front matter field 'name' must be a string")
        result.output.contains('.agents/skills/root/SKILL.md: skill front matter must be a YAML mapping')
    }

    def "validateRepositoryConventions detects duplicate logical message keys and ignores generated trees"() {
        given:
        writeBuild()
        writeSkill('sample', 'sample')
        writeAgents('.agents/skills/sample/SKILL.md')
        writeWorkflow('valid.yml', "uses: actions/checkout@${SHA}")
        writeProperties('src/main/resources/messages.properties', '''# message=ignored
message=one
message=two
escaped\\=key=one
escaped\\=key=two
continued\\
 key=one
continuedkey=two
''')
        writeProperties('build/generated/messages.properties', '''message=one
message=two
''')

        when:
        def result = runAndFail('validateRepositoryConventions')

        then:
        result.output.contains("duplicate message key 'message'")
        result.output.contains("duplicate message key 'escaped=key'")
        result.output.contains("duplicate message key 'continuedkey'")
        !result.output.contains('build/generated/messages.properties')
    }

    private void writeBuild(boolean includeRat = false) {
        testProjectDir.resolve('settings.gradle').toFile().text = ''
        testProjectDir.resolve('build.gradle').toFile().text = '''plugins {
    id 'org.apache.grails.gradle.grails-violation-aggregation'
}
''' + (includeRat ? '''
tasks.register('rat') {
    doLast {
        file('build').mkdirs()
        file('build/rat-ran').text = 'ran'
    }
}
''' : '')
    }

    private void writeSkill(String directory, String name, boolean withLicense = true) {
        writeSkillContent(directory, """---
name: ${name}
description: Test skill
${withLicense ? 'license: Apache-2.0' : ''}
---
""")
    }

    private void writeSkillContent(String directory, String content) {
        def file = testProjectDir.resolve(".agents/skills/${directory}/SKILL.md").toFile()
        file.parentFile.mkdirs()
        file.text = content
    }

    private void writeAgents(String path) {
        testProjectDir.resolve('AGENTS.md').toFile().text = "Read `${path}`.\n"
    }

    private void writeWorkflow(String name, String uses) {
        writeWorkflowContent(name, "steps:\n  - ${uses}\n")
    }

    private void writeWorkflowContent(String name, String content) {
        def file = testProjectDir.resolve(".github/workflows/${name}").toFile()
        file.parentFile.mkdirs()
        file.text = content
    }

    private void writeCompositeAction(String name, String content, String extension = 'yml') {
        writeActionManifest(".github/actions/${name}", content, extension)
    }

    private void writeActionManifest(String directory, String content, String extension = 'yml') {
        def file = testProjectDir.resolve("${directory}/action.${extension}").toFile()
        file.parentFile.mkdirs()
        file.text = content
    }

    private void writeProperties(String path, String content) {
        def file = testProjectDir.resolve(path).toFile()
        file.parentFile.mkdirs()
        file.text = content
    }

    private def run(String task) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
                .build()
    }

    private def runAndFail(String task) {
        GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()
    }
}
