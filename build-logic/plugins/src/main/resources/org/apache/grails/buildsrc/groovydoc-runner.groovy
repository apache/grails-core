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

// Runs the Groovy 'groovydoc' Ant task in a JVM of its own, launched by
// GroovydocEnhancerPlugin. Everything it needs - the framework's Groovy, groovy-ant and
// javaparser, plus the documented module's classpath - is the whole classpath of this JVM,
// so groovydoc resolves referenced types against the same Groovy the framework is built
// with. That cannot be arranged inside Gradle, whose own build logic runs on an older Groovy
// that wins every classloader lookup.
//
// Fully qualified names throughout: this script is compiled by whichever Groovy is on that
// classpath, with no imports added by Gradle.
//
// args[0] is a properties file written by the plugin:
//   arg.<name>          one attribute of the groovydoc Ant task
//   link.<n>.packages   package prefix of the nth external javadoc mapping
//   link.<n>.href       and the site it maps to, in declaration order
//   verbose             whether Ant prints its own INFO messages

Properties params = new Properties()
new File(args[0]).withInputStream { input -> params.load(input) }

Map<String, String> antArgs = [:]
params.stringPropertyNames()
        .findAll { String key -> key.startsWith('arg.') }
        .sort()
        .each { String key -> antArgs.put(key.substring('arg.'.length()), params.getProperty(key)) }

List<Map<String, String>> links = []
for (int index = 0; params.containsKey("link.${index}.packages".toString()); index++) {
    links.add([
            packages: params.getProperty("link.${index}.packages".toString()),
            href    : params.getProperty("link.${index}.href".toString())
    ])
}

groovy.ant.AntBuilder ant = new groovy.ant.AntBuilder()

// Gradle routed Ant's INFO messages to its own INFO level, hidden unless the build asked for
// it. Nothing routes them out of this JVM, so mirror that: quiet by default, and INFO when
// the build is running at INFO or finer, which is where those diagnostics used to show up.
int messageLevel = Boolean.parseBoolean(params.getProperty('verbose'))
        ? org.apache.tools.ant.Project.MSG_INFO
        : org.apache.tools.ant.Project.MSG_WARN
ant.antProject.buildListeners.each { listener ->
    if (listener instanceof org.apache.tools.ant.BuildLogger) {
        listener.messageOutputLevel = messageLevel
    }
}

ant.taskdef(name: 'groovydoc', classname: 'org.codehaus.groovy.ant.Groovydoc')
ant.groovydoc(antArgs) {
    for (Map<String, String> mapping : links) {
        link(packages: mapping.packages, href: mapping.href)
    }
}
