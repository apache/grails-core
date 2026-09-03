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

import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class JmhCompare {

    static void main(String[] args) {
        int exit = run(args, new GitHubComments())
        if (exit != 0) {
            System.exit(exit)
        }
    }

    static int run(String[] args, CommentPoster poster) {
        return run(args, poster, System.getenv())
    }

    static int run(String[] args, CommentPoster poster, Map<String, String> environment) {
        try {
            Map<String, String> options = parse(args)
            if (options.containsKey('post-file')) {
                return postFile(options, poster, environment)
            }
            double threshold = options.containsKey('threshold') ? Double.parseDouble(options.get('threshold')) : .10D
            if (!Double.isFinite(threshold) || threshold <= 0D) {
                throw new IllegalArgumentException('--threshold must be greater than zero')
            }
            String headPath = options.get('head')
            if (!headPath) {
                throw new IllegalArgumentException('--head is required')
            }
            Map<String, Map<String, Benchmark>> head = JmhResults.readShards(headPath)
            String basePath = options.get('base')
            String report = basePath && Files.exists(Path.of(basePath))
                    ? ReportRenderer.render(BenchmarkComparator.compareShards(head, JmhResults.readShards(basePath), threshold,
                    options.getOrDefault('expected-shards', '').split(',').findAll { String value -> !value.trim().isEmpty() }))
                    : ReportRenderer.headOnly(JmhResults.poolShards(head))
            System.out.println(report)
            if (options.containsKey('output')) {
                Files.writeString(Path.of(options.get('output')), report + '\n', StandardCharsets.UTF_8)
            }
            postWhenConfigured(report, options, poster, environment)
            return 0
        } catch (Exception error) {
            System.err.println("error: ${error.message}")
            return 2
        }
    }

    private static int postFile(Map<String, String> options, CommentPoster poster, Map<String, String> environment) {
        if (options.containsKey('head')) {
            throw new IllegalArgumentException('--post-file cannot be used with --head')
        }
        String report = Files.readString(Path.of(options.get('post-file')), StandardCharsets.UTF_8)
        postWhenConfigured(report, options, poster, environment)
        return 0
    }

    private static void postWhenConfigured(String report, Map<String, String> options, CommentPoster poster, Map<String, String> environment) {
        String pr = options.getOrDefault('pr-number', '').trim()
        String repo = options.get('repo')
        String token = environment.get('GITHUB_TOKEN')
        if (!pr || pr == 'null' || !repo || !token) {
            if (options.containsKey('post-file')) {
                System.err.println('warning: --post-file requires --repo, --pr-number, and GITHUB_TOKEN; skipping comment post')
            }
            return
        }
        try {
            poster.post(report, repo, pr, token)
        } catch (Exception error) {
            System.err.println("warning: unable to post JMH report: ${error.message}")
        }
    }

    private static Map<String, String> parse(String[] args) {
        Set<String> values = ['head', 'base', 'threshold', 'repo', 'pr-number', 'expected-shards', 'output', 'post-file'] as Set<String>
        Map<String, String> options = new LinkedHashMap<>()
        for (int index = 0; index < args.length; index++) {
            String option = args[index]
            if (!option.startsWith('--')) {
                throw new IllegalArgumentException("unknown option: ${option}")
            }
            String key = option.substring(2)
            if (!values.contains(key) || index + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for --${key}")
            }
            options.put(key, args[++index])
        }
        return options
    }
}
