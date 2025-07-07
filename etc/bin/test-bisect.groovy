#!/usr/bin/env groovy
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

import java.nio.file.Path
import java.nio.file.Paths
import groovy.cli.commons.CliBuilder

/**
 * This script finds the test class that's causing test pollution in other tests.
 * To use it, gradle must be configured to run sequentially, without retries, and output a specific line
 * so the script knows the test order. The script will pass the property `testBisect` for gradle to detect when being called
 * by this script. For example:
 *
 *     if(hasProperty('testBisect')) {
 *          tasks.withType(Test).configureEach {
 *              maxParallelForks = 1
 *              forkEvery = 0
 *               develocity {
 *                   testDistribution {
 *                       enabled.set(false)
 *                       retryInSameJvm.set(true)
 *                  }
 *                   testRetry {
 *                       maxRetries = 1
 *                       maxFailures = 1
 *                       failOnPassedAfterRetry = true
 *                   }
 *               }
 *              beforeTest { testDescriptor ->
 *                  logger.info "STARTED TEST: ${testDescriptor.className} > ${testDescriptor.name}"
 *              }
 *               afterSuite { desc, result ->
 *                   if (!desc.parent) {
 *                       logger.info("FINISHED - ${result.failedTestCount == 0 && result.testCount > 1} ? 'PASSED' : 'FAILED'} - ${result.testCount} tests executed, ${result.failedTestCount} failed")
 *                   }
 *               }
 *          }
 *      }
 */
class TestPollution {

    File gradleCommandDirectory
    String taskPath
    String testName

    Path scriptDir = Paths.get(getClass()
            .protectionDomain
            .codeSource
            .location
            .toURI())
            .toAbsolutePath()
            .parent

    TestPollution(args) {
        parseArgs(args)
    }

    def parseArgs(args) {
        def cli = new CliBuilder(usage: "test-bisect.groovy -p :grails-gsp:test -n org.grails.web.taglib.FormTagLibTests")

        cli.with {
            p longOpt: 'taskPath', args: 1, argName: 'taskPath', 'The path to the gradle test task from the root of the project directory, e.g. grails-gsp:test'
            n longOpt: 'testName', args: 1, argName: 'testName', 'The test class name that passes when run by itself but fails when the other tests are run'
        }

        def options = cli.parse(args)

        if (options.'taskPath') {
            taskPath = options.'taskPath'
        }

        if (options.'testName') {
            testName = options.'testName'
        }

        if (!taskPath || !testName) {
            cli.usage()
            System.exit(-1)
        }

        gradleCommandDirectory = scriptDir.resolve('../..').toFile().canonicalFile
        if (!gradleCommandDirectory.exists()) {
            println "Unable to find gradle rootProject for the given path: $gradleCommandDirectory.toString()"
            cli.usage()
            System.exit(-1)
        }
    }

    Collection<String> findTestRunOrder() {
        LinkedHashSet<String> tests = []

        def pattern = ~/.*STARTED TEST: ([a-z0-9A-Z.]+) >.*/
        runCommand('test-run-order', "./gradlew ${taskPath} --rerun-tasks -PtestBisect --info --no-daemon --no-parallel") { String line ->
            pattern.matcher(line).each { matcher ->
                if (matcher.size() > 1) {
                    String testName = matcher[1]
                    if (testName && !tests.contains(testName)) {
                        tests << testName
                    }
                }
            }
        }

        tests
    }

    void bisectTests() {
        if (!testsPass("solo", [testName])) {
            println "The test ($testName) fails when run by itself. Pollution cannot be determined."
            println "Run the command yourself: ${createCommand(withPollutedTest([]))}"
            return
        }

        List<String> testList = (findTestRunOrder() - testName).toList()
        if (!testList) {
            println "No other tests were found. Cannot bisect."
            System.exit(1)
        }

        bisectTests(testList)
    }

    List<String> bisectTests(List<String> testList) {

        // if the test list size is > 1, split into left and right hand sides
        if (testList.size() > 1) {
            List<String> lhs = leftHandSide(testList)
            List<String> rhs = rightHandSide(testList)

            if (!testsPass("left", withPollutedTest(lhs))) {
                return bisectTests(lhs)
            }

            if (!testsPass("right", withPollutedTest(rhs))) {
                return bisectTests(rhs)
            }

            if (testsPass("full", withPollutedTest(testList))) {
                println "Unable to find any failing tests with this list, running them all appears to run without issue: \n${testList.join(" ")}"
                return null
            }

            println "Unable to find just one test that's causing problems.  Running just with the left hand or right hand side of this narrowed list passes, but the full list fails"
            println "full list (fails): ${testList.join(" ")}"
            println "left hand side (passes): ${lhs.join(" ")}"
            println "right hand side (passes): ${rhs.join(" ")}"
        } else if (!testsPass("suspected", withPollutedTest(testList))) {
            println "The test that's causing pollution: ${testList.join(" ")}" // should only be 1
            println "Here's the command to execute to see if you've fixed things:\n ${createCommand(withPollutedTest(testList))}"
            return testList
        }

        println "Not sure what's happening, got to this list of tests, but everything passes with this list: \n${testList.join(" ")}"
        []
    }

    List<String> leftHandSide(List<String> testList) {
        return testList[0..(testList.size() / 2 - 1)]
    }

    List<String> rightHandSide(List<String> testList) {
        return testList[(testList.size() / 2)..-1]
    }

    List<String> withPollutedTest(List<String> testList) {
        return [testList, testName].flatten()
    }

    Boolean testsPass(String runName, List<String> testList) {
        String command = createCommand(testList)
        return runCommand(runName, command)
    }

    String createCommand(List<String> testList) {
        def testArgs = testList.collect { "--tests ${it}" }
        "./gradlew ${taskPath} --rerun-tasks -PtestBisect --no-daemon --no-parallel --info ${testArgs.join(' ')}"
    }

    def out(prefix, message) {
        println("${prefix.padLeft(16, ' ')}: $message")
    }

    boolean runCommand(runName, command, Closure outputParser = null) {
        out runName, command
        String completedLine = ''

        String knownExecutor = null
        def executorPattern = ~/.*Gradle Test Executor ([0-9]+) .*/
        def finishPattern = ~/.*FINISHED - (PASSED|FAILED) - .*/
        def exitValue = command.execute(null, gradleCommandDirectory).with { proc ->
            proc.in.eachLine { String line ->
                // uncomment for real time output of the gradle commands
                // out(runName, line)
                def completedMatcher = finishPattern.matcher(line)
                if (completedMatcher.matches()) {
                    completedLine = line
                }


                def execMatcher = executorPattern.matcher(line)
                if (execMatcher.matches()) {
                    if (execMatcher.size() > 1) {
                        def foundExecutor = execMatcher[1]
                        if (!knownExecutor && foundExecutor) {
                            knownExecutor = foundExecutor
                        } else if (knownExecutor != foundExecutor) {
                            throw new Exception("WARNING: Found multiple Gradle Test Executors: ${knownExecutor} and ${foundExecutor}. Test pollution detection cannot work if tests are run in parallel.")
                        }
                    }
                }

                if (outputParser) {
                    outputParser.call(line)
                }
            }
            proc.waitFor()
            proc.exitValue()
        }

        if(!completedLine) {
            throw new IllegalStateException("Could not locate the completed line in the output.")
        }
        boolean failures = completedLine.contains('FAILED')

        out(runName, "exitValue=${exitValue}, failures=${failures}, testExecutor=${knownExecutor}, completedLine=${completedLine}")
        !failures && exitValue == 0
    }
}

new TestPollution(args).bisectTests()
