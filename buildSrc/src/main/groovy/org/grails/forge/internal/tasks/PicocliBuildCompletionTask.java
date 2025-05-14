/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.internal.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

@CacheableTask
public abstract class PicocliBuildCompletionTask extends DefaultTask {
    @Input
    public abstract Property<String> getMainClass();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void execute() {
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(getClasspath());
            spec.getMainClass().set("picocli.AutoComplete");
            spec.jvmArgs("-Dpicocli.autocomplete.systemExitOnError");
            spec.args(
                    getMainClass().get(),
                    "--completionScript=" + getOutputDirectory().file("grails_forge_cli_completion").get().getAsFile().getAbsolutePath(),
                    "--force",
                    "--factory=org.grails.forge.cli.GrailsPicocliFactory"
            );
        });
    }

}
