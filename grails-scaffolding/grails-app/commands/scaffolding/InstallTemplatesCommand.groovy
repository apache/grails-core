
/*
 * Copyright 2023 Puneet Behl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package scaffolding

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.dev.commands.GrailsApplicationCommand
import grails.dev.commands.io.FileSystemInteraction
import grails.plugin.scaffolding.CommandLineHelper
import grails.plugin.scaffolding.SkipBootstrap
import groovy.transform.CompileStatic
import org.grails.io.support.Resource
import org.grails.io.support.SpringIOUtils

/**
 * Installs scaffolding templates that use f:all to render properties
 * Usage: <code>./gradlew runCommand "-Pargs=install-templates"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class InstallTemplatesCommand implements GrailsApplicationCommand, SkipBootstrap, CommandLineHelper {

    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    final String description = 'Installs scaffolding templates that use f:all to render properties';

    @Override
    boolean handle() {

        try {
            mkdir("src/main/templates/scaffolding")
            templates("scaffolding/*").each { Resource r ->
                consoleLogger.verbose("Copying template $r.URL")
                final String path = r.URL.toString().replaceAll(/^.*?META-INF/, "src/main")
                if (path.endsWith('/')) {
                    mkdir(path)
                } else {
                    File to = new File(path)
                    SpringIOUtils.copy(r, to)
                    consoleLogger.verbose("Copied ${r.filename} to location ${to.canonicalPath}")
                }
            }
            consoleLogger.verbose("Templates installation complete")
            return SUCCESS
        } catch (e) {
            consoleLogger.error e.message, e
        }
    }

    @Override
    Iterable<Resource> templates(final String pattern) {
        Collection<Resource> resourceList = []
        resourceList.addAll(resources("src/main/templates/$pattern"))
        resourceList.addAll(resources("classpath*:META-INF/templates/$pattern"))
        resourceList.unique()
    }
}
