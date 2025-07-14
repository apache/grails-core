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
package org.grails.cli.command.run;

import org.grails.cli.compiler.GroovyCompilerScope;
import org.grails.cli.compiler.RepositoryConfigurationFactory;
import org.grails.cli.compiler.grape.RepositoryConfiguration;
import org.grails.cli.profile.Command;
import org.grails.cli.profile.CommandDescription;
import org.grails.cli.profile.ExecutionContext;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * {@link org.springframework.boot.cli.command.Command;} to 'run' a groovy script or scripts.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @see SpringApplicationRunner
 * @since 1.0.0
 */
public class RunCommand implements Command {

    public static final String NAME = "run";

    private final Object monitor = new Object();

    private SpringApplicationRunner runner;

    public RunCommand() {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public CommandDescription getDescription() {
        CommandDescription description = new CommandDescription();
        description.setName(NAME);
        description.setDescription("Run a grails groovy script");
        description.setUsage("run [SCRIPT NAME]");
        return description;
    }

    @Override
    public synchronized boolean handle(ExecutionContext executionContext) {
        synchronized (this.monitor) {
            String[] sources = executionContext.getCommandLine().getRemainingArgs().toArray(new String[0]);
            List<RepositoryConfiguration> repositoryConfiguration = RepositoryConfigurationFactory
                    .createDefaultRepositoryConfiguration();
            repositoryConfiguration.add(0,
                    new RepositoryConfiguration("local", new File("repository").toURI(), true));

            SpringApplicationRunnerConfiguration configuration = new SpringApplicationRunnerConfigurationAdapter(
                    repositoryConfiguration);

            try {
                this.runner = new SpringApplicationRunner(configuration, sources);
                this.runner.compileAndRun();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return true;
        }
    }

    static class SpringApplicationRunnerConfigurationAdapter implements SpringApplicationRunnerConfiguration {

        private final List<RepositoryConfiguration> repositoryConfiguration;

        public SpringApplicationRunnerConfigurationAdapter(List<RepositoryConfiguration> repositoryConfiguration) {
            this.repositoryConfiguration = repositoryConfiguration;
        }

        @Override
        public boolean isWatchForFileChanges() {
            return true;
        }

        @Override
        public Level getLogLevel() {
            return Level.INFO;
        }

        @Override
        public GroovyCompilerScope getScope() {
            return GroovyCompilerScope.DEFAULT;
        }

        @Override
        public boolean isGuessImports() {
            return true;
        }

        @Override
        public boolean isGuessDependencies() {
            return true;
        }

        @Override
        public boolean isAutoconfigure() {
            return true;
        }

        @Override
        public String[] getClasspath() {
            return new String[0];
        }

        @Override
        public List<RepositoryConfiguration> getRepositoryConfiguration() {
            return this.repositoryConfiguration;
        }

        @Override
        public boolean isQuiet() {
            return false;
        }

    }
}
