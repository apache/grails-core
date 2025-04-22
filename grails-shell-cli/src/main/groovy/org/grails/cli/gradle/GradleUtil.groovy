/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.cli.gradle

import grails.build.logging.GrailsConsole
import grails.io.support.SystemOutErrCapturer
import grails.io.support.SystemStreamsRedirector
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultCancellationTokenSource
import org.grails.build.logging.GrailsConsoleErrorPrintStream
import org.grails.build.logging.GrailsConsolePrintStream
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.ProjectContext

/**
 * Utility methods for interacting with Gradle
 *
 * @since 3.0
 * @author Graeme Rocher
 * @author Lari Hotari
 */
@CompileStatic
class GradleUtil {
    private static final boolean DEFAULT_SUPPRESS_OUTPUT = true

    public static ProjectConnection openGradleConnection(File baseDir) {
        GradleConnector gradleConnector = GradleConnector.newConnector().forProjectDirectory(baseDir)
        if (System.getenv("GRAILS_GRADLE_HOME")) {
            gradleConnector.useInstallation(new File(System.getenv("GRAILS_GRADLE_HOME")))
        } else {
            def userHome = System.getProperty("user.home")
            if (userHome) {
                File gradleFile = new File(baseDir, "gradle.properties")
                if (gradleFile.exists() && gradleFile.canRead()) {
                    Properties gradleProperties = new Properties()
                    gradleProperties.load(gradleFile.newInputStream())
                    String gradleWrapperVersion = gradleProperties.getProperty("gradleWrapperVersion")

                    File sdkManGradle = new File("$userHome/.sdkman/candidates/gradle/$gradleWrapperVersion")
                    if (sdkManGradle.exists() && sdkManGradle.isDirectory()) {
                        gradleConnector.useInstallation(sdkManGradle)
                    }
                }
            }
        }

        gradleConnector.connect()
    }

    public static <T> T withProjectConnection(File baseDir, boolean suppressOutput = DEFAULT_SUPPRESS_OUTPUT,
                                              @ClosureParams(value = SimpleType.class, options = "org.gradle.tooling.ProjectConnection") Closure<T> closure) {
        ProjectConnection projectConnection = openGradleConnection(baseDir)
        try {
            if (suppressOutput) {
                SystemOutErrCapturer.withNullOutput {
                    closure(projectConnection)
                }
            } else {
                SystemStreamsRedirector.withOriginalIO {
                    closure(projectConnection)
                }
            }
        } finally {
            projectConnection.close();
        }
    }

    public static void runBuildWithConsoleOutput(ExecutionContext context,
                                                 @ClosureParams(value = SimpleType.class, options = "org.gradle.tooling.BuildLauncher") Closure<?> buildLauncherCustomizationClosure) {
        withProjectConnection(context.getBaseDir(), DEFAULT_SUPPRESS_OUTPUT) { ProjectConnection projectConnection ->
            BuildLauncher launcher = projectConnection.newBuild()
            setupConsoleOutput(context, launcher)
            wireCancellationSupport(context, launcher)
            buildLauncherCustomizationClosure.call(launcher)
            launcher.run()
        }
    }

    public static LongRunningOperation setupConsoleOutput(ProjectContext context, LongRunningOperation operation) {
        GrailsConsole grailsConsole = context.console
        operation.colorOutput = grailsConsole.ansiEnabled
        operation.standardOutput = new GrailsConsolePrintStream( grailsConsole.out )
        operation.standardError = new GrailsConsoleErrorPrintStream( grailsConsole.err )
        operation
    }

    public static <T> T runBuildActionWithConsoleOutput(ProjectContext context, BuildAction<T> buildAction) {
        // workaround for GROOVY-7211, static type checking problem when default parameters are used
        runBuildActionWithConsoleOutput(context, buildAction, null)
    }

    public static <T> T runBuildActionWithConsoleOutput(ProjectContext context, BuildAction<T> buildAction,
                                                        @ClosureParams(value = FromString.class, options = "org.gradle.tooling.BuildActionExecuter<T>") Closure<?> buildActionExecuterCustomizationClosure) {
        withProjectConnection(context.getBaseDir(), DEFAULT_SUPPRESS_OUTPUT) { ProjectConnection projectConnection ->
            runBuildActionWithConsoleOutput(projectConnection, context, buildAction, buildActionExecuterCustomizationClosure)
        }
    }

    public static <T> T runBuildActionWithConsoleOutput(ProjectConnection connection, ProjectContext context, BuildAction<T> buildAction) {
        // workaround for GROOVY-7211, static type checking problem when default parameters are used
        runBuildActionWithConsoleOutput(connection, context, buildAction, null)
    }

    public static <T> T runBuildActionWithConsoleOutput(ProjectConnection connection, ProjectContext context, BuildAction<T> buildAction, @ClosureParams(value=FromString.class, options="org.gradle.tooling.BuildActionExecuter<T>") Closure<?> buildActionExecuterCustomizationClosure) {
        BuildActionExecuter<T> buildActionExecuter = connection.action(buildAction)
        setupConsoleOutput(context, buildActionExecuter)
        buildActionExecuterCustomizationClosure?.call(buildActionExecuter)
        return buildActionExecuter.run()
    }
    
    public static wireCancellationSupport(ExecutionContext context, BuildLauncher buildLauncher) {
        DefaultCancellationTokenSource cancellationTokenSource = new DefaultCancellationTokenSource()
        buildLauncher.withCancellationToken(cancellationTokenSource.token())
        context.addCancelledListener({
            cancellationTokenSource.cancel()
        })
    }
}
