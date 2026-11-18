/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.grails.benchmarks.report

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

class GoldenReportSpec extends Specification {
    @TempDir
    Path temporaryDirectory

    @Unroll
    def "#name report exactly matches the golden fixture"() {
        given:
        Path output = temporaryDirectory.resolve("${name}.md")
        String[] arguments = argumentsFor(head, base, output, expectedShards)

        when:
        int result = JmhCompare.run(arguments, Mock(CommentPoster))

        then:
        result == 0
        normalize(Files.readString(output)) == normalize(resource(expected))

        where:
        name      | head                        | base                        | expected                 | expectedShards
        'broad'   | 'jmh-golden/broad-head.json' | 'jmh-golden/broad-base.json' | 'jmh-golden/expected-broad.md'   | null
        'numbers' | 'jmh-golden/numbers-head.json' | 'jmh-golden/numbers-base.json' | 'jmh-golden/expected-numbers.md' | null
        'edges'   | 'jmh-golden/edges-head.json' | 'jmh-golden/edges-base.json' | 'jmh-golden/expected-edges.md'   | null
        'norulers' | 'jmh-golden/norulers-head.json' | 'jmh-golden/norulers-base.json' | 'jmh-golden/expected-norulers.md' | null
        'shards'  | 'jmh-golden/shards-head'     | 'jmh-golden/shards-base'     | 'jmh-golden/expected-shards.md'  | 'shard-a.json,shard-b.json,shard-c.json'
        'headonly' | 'jmh-golden/broad-head.json' | null                         | 'jmh-golden/expected-headonly.md' | null
    }

    private String[] argumentsFor(String head, String base, Path output, String expectedShards) {
        List<String> arguments = ['--head', resourcePath(head).toString(), '--output', output.toString()]
        if (base != null) {
            arguments.addAll(['--base', resourcePath(base).toString()])
        }
        if (expectedShards != null) {
            arguments.addAll(['--expected-shards', expectedShards])
        }
        arguments as String[]
    }

    private Path resourcePath(String name) {
        Path.of(getClass().getClassLoader().getResource(name).toURI())
    }

    private String resource(String name) {
        Files.readString(resourcePath(name))
    }

    private static String normalize(String text) {
        text.replace("\r\n", "\n").replace("\r", "\n")
    }
}
