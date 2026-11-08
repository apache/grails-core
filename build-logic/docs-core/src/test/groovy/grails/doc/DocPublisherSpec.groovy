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
package grails.doc

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

class DocPublisherSpec extends Specification {

    @Shared
    @TempDir
    File workspace

    @Shared
    File guideDir

    @Shared
    File referenceDir

    void setupSpec() {
        File sourceDir = new File(workspace, 'src')
        File guideSourceDir = new File(sourceDir, 'guide')
        new File(guideSourceDir, 'second').mkdirs()
        new File(guideSourceDir, 'toc.yml').text = '''\
            first: First
            second:
              title: Second
              secondChild: Second Child
            third: Third
            '''.stripIndent()
        new File(guideSourceDir, 'first.adoc').text = '''\
            [[localAnchor]]
            Links: <<second>>, <<secondChild>>, <<customAnchor,an anchor>>, <<sharedAnchor,a shared anchor>>,
            <<localAnchor,a local anchor>> and <<missingAnchor,a missing anchor>>.
            '''.stripIndent()
        new File(guideSourceDir, 'second.adoc').text = '''\
            [[customAnchor]]
            An anchored paragraph.

            [[sharedAnchor]]
            The first chapter defining this anchor.
            '''.stripIndent()
        new File(guideSourceDir, 'second/secondChild.adoc').text = '''\
            [[childAnchor]]
            Links: <<customAnchor,an anchor in its chapter>>, <<third>>, <<childAnchor,an anchor on this page>>
            and <<missingAnchor,a missing anchor>>.

            Paths: link:../ref/Tags/example.html[a reference page], https://grails.apache.org[an external site]
            and image:diagram.png[a diagram].
            '''.stripIndent()
        new File(guideSourceDir, 'third.adoc').text = '''\
            [[sharedAnchor]]
            A later chapter defining the same anchor.
            '''.stripIndent()

        File referenceSourceDir = new File(sourceDir, 'ref/Tags')
        referenceSourceDir.mkdirs()
        new File(sourceDir, 'ref/Tags.adoc').text = 'See <<third>>.\n'
        new File(referenceSourceDir, 'example.adoc').text = '''\
            [[referenceAnchor]]
            Links: <<customAnchor,an anchor>>, <<secondChild>>, <<referenceAnchor,an anchor on this page>>
            and <<missingAnchor,a missing anchor>>.
            '''.stripIndent()

        File targetDir = new File(workspace, 'guide-output')
        File workDir = new File(workspace, 'work')
        workDir.mkdirs()
        Properties engineProperties = new Properties()
        engineProperties.setProperty('title', 'Publisher Guide')
        DocPublisher publisher = new DocPublisher(sourceDir, targetDir)
        publisher.asciidoc = true
        publisher.workDir = workDir
        publisher.version = '1.2.3'
        publisher.engineProperties = engineProperties
        publisher.publish()
        guideDir = new File(targetDir, 'guide')
        referenceDir = new File(targetDir, 'ref')
    }

    void 'points a cross reference on a chapter page at the chapter page that defines its target'() {
        when:
        String first = new File(guideDir, 'first.html').text

        then: 'references to another chapter lead to that chapter page'
        first.contains('href="second.html#second"')
        first.contains('href="second.html#secondChild"')
        first.contains('href="second.html#customAnchor"')

        and: 'an anchor defined in several chapters leads to the first, as in the single-page guide'
        first.contains('href="second.html#sharedAnchor"')
        !first.contains('href="third.html#sharedAnchor"')

        and: 'references within the page, and to anchors no page defines, are left alone'
        first.contains('href="#localAnchor"')
        first.contains('href="#missingAnchor"')
    }

    void 'resolves the relative paths on a sub-section page from its own directory'() {
        when:
        String second = new File(guideDir, 'second.html').text
        String secondChild = new File(guideDir, 'pages/secondChild.html').text

        then: 'the chapter page holds the content with paths relative to the guide directory'
        second.contains('href="../ref/Tags/example.html"')
        second.contains('src="../img/diagram.png"')

        and: 'the sub-section page, one directory deeper, holds the same content one more level up'
        secondChild.contains('href="../../ref/Tags/example.html"')
        secondChild.contains('src="../../img/diagram.png"')
        !secondChild.contains('href="../ref/Tags/example.html"')

        and: 'its stylesheets and scripts come from the same place as on the chapter page'
        second.contains('href="../css/main.css"')
        secondChild.contains('href="../../css/main.css"')
        secondChild.contains('src="../../js/docs.js"')

        and: 'URLs with a scheme are left alone'
        secondChild.contains('href="https://grails.apache.org"')
    }

    void 'points a cross reference on a sub-section page at the chapter page that defines its target'() {
        when:
        String secondChild = new File(guideDir, 'pages/secondChild.html').text

        then: 'references outside the sub-section lead to the chapter page, one directory up'
        secondChild.contains('href="../second.html#customAnchor"')
        secondChild.contains('href="../third.html#third"')

        and: 'references within the page, and to anchors no page defines, are left alone'
        secondChild.contains('href="#childAnchor"')
        secondChild.contains('href="#missingAnchor"')
    }

    void 'points a cross reference on a reference page at the guide chapter page that defines its target'() {
        when:
        String example = new File(referenceDir, 'Tags/example.html').text
        String usage = new File(referenceDir, 'Tags/Usage.html').text

        then: 'references to the guide lead to its chapter pages'
        example.contains('href="../../guide/second.html#customAnchor"')
        example.contains('href="../../guide/second.html#secondChild"')
        usage.contains('href="../../guide/third.html#third"')

        and: 'references within the page, and to anchors no page defines, are left alone'
        example.contains('href="#referenceAnchor"')
        example.contains('href="#missingAnchor"')
    }

    void 'keeps cross references in the single-page guide on that page'() {
        when:
        String single = new File(guideDir, 'single.html').text

        then: 'the single-page guide holds every target, so its links stay fragments'
        single.contains('href="#second"')
        single.contains('href="#customAnchor"')
        !single.contains('href="second.html#')
    }
}
