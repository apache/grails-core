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
package org.apache.grails.e2e.skills

import spock.lang.Specification

/**
 * The agent skills Grails publishes must reach a build the way the guide describes.
 *
 * <p>This application declares the skills without a version, so they resolve through the Grails
 * BOM that the Grails Gradle plugin applies; {@code agent-skills-plain-build} names the BOM itself.
 * Both extract them with the SkillsJars Gradle plugin, which reads the SkillsJars layout each jar
 * holds its skill in, {@code META-INF/skills/<org>/<repo>/<skill>/}, and writes each skill to a
 * {@code skillsjars__<org>__<repo>__<skill>} directory. The test task runs both builds'
 * extractSkillsJars first.
 *
 * <p>Paths are relative to this project's directory, where the test runs.
 */
class AgentSkillsSpec extends Specification {

    private static final List<String> PUBLISHED_SKILLS = ['grails-developer', 'grails-8-upgrade']

    private static final Map<String, File> EXTRACTED_BY = [
            'a Grails application': new File('.agents/skills'),
            'a build without the Grails Gradle plugin': new File('../agent-skills-plain-build/.agents/skills'),
    ]

    private static final File SKILL_SOURCES = new File('../../.agents/skills')

    void 'every published skill is extracted by #consumer and nothing else is'() {
        expect:
        (EXTRACTED_BY[consumer].list() as Set) == (PUBLISHED_SKILLS.collect { extractedName(it) } as Set)

        where:
        consumer << EXTRACTED_BY.keySet()
    }

    void '#consumer extracts the #skill skill unchanged'() {
        given:
        File extracted = new File(EXTRACTED_BY[consumer], "${extractedName(skill)}/SKILL.md")

        expect: 'exactly what was published'
        extracted.getText('UTF-8') == new File(SKILL_SOURCES, "${skill}/SKILL.md").getText('UTF-8')

        and: 'declaring the name agents identify it by'
        frontmatterName(extracted) == skill

        where:
        [consumer, skill] << [EXTRACTED_BY.keySet(), PUBLISHED_SKILLS].combinations()
    }

    // The directory the SkillsJars plugin writes a skill of this repository to
    private static String extractedName(String skill) {
        "skillsjars__apache__grails-core__${skill}"
    }

    private static String frontmatterName(File skill) {
        List<String> lines = skill.readLines('UTF-8')
        assert lines && lines.first() == '---' : "${skill} does not start with frontmatter"
        int end = lines.subList(1, lines.size()).indexOf('---') + 1
        assert end > 0 : "${skill} does not close its frontmatter"
        String name = lines.subList(1, end).find { it.startsWith('name:') }
        name?.substring('name:'.length())?.trim()
    }
}
