/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.init

import org.grails.cli.compiler.grape.DependencyResolutionContext
import org.grails.cli.compiler.grape.MavenResolverGrapeEngine
import org.grails.cli.compiler.grape.MavenResolverGrapeEngineFactory
import org.grails.cli.compiler.grape.RepositoryConfiguration

/**
 * Created by jameskleeh on 10/31/16.
 */
// TODO: This was created as an intermediate to prevent shipping all of the cli dependencies; but anywhere that this works
// grails will already have a gradle project so we should just invoke via gradle instead
class RunCommand {

    static final String DEFAULT_GRAILS_SHELL_VERSION = '7.0.0-SNAPSHOT'

    static void main(String[] args) {

        Properties props = new Properties()
        String grailsVersion
        String grailsShellVersion
        String groovyVersion
        try {
            props.load(new FileInputStream("gradle.properties"))
            grailsVersion = props.getProperty("grailsVersion")
            grailsShellVersion = props.getProperty("grailsShellVersion")
            groovyVersion = props.getProperty("groovyVersion")
        } catch (IOException e) {
            throw new RuntimeException("Could not determine grails version due to missing properties file")
        }

        if(!grailsShellVersion) {
            grailsShellVersion = DEFAULT_GRAILS_SHELL_VERSION
        }

        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(RunCommand.classLoader)

        List<RepositoryConfiguration> repositoryConfigurations = [new RepositoryConfiguration("mavenCentral", new URI("https://repo1.maven.org/maven2"), true)]

        // Only add snapshot repository when grailsVersion is not set or groovyVersion or grailsVersion ends in SNAPSHOT
        if (!grailsVersion || grailsVersion.endsWith("SNAPSHOT") || groovyVersion?.endsWith("SNAPSHOT")) {
            repositoryConfigurations.add(new RepositoryConfiguration("apacheSnapshot", new URI("https://repository.apache.org/content/groups/snapshots"), true))
        }

        MavenResolverGrapeEngine grapeEngine = MavenResolverGrapeEngineFactory.create(groovyClassLoader, repositoryConfigurations, new DependencyResolutionContext(), false)
        try {
            grapeEngine.grab([:], [group: "org.apache.grails", module: "grails-shell-cli", version: grailsVersion])
        }
        catch(dependencyResolutionException){
            // Try grails shell version from gradle.properties or default
            grapeEngine.grab([:], [group: "org.apache.grails", module: "grails-shell-cli", version: grailsShellVersion])
        }

        ClassLoader previousClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().setContextClassLoader(groovyClassLoader)

        try {
            groovyClassLoader.loadClass('org.grails.cli.GrailsCli').main(args)
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader)
        }
    }
}
