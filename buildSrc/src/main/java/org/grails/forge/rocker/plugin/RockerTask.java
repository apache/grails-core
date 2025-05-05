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

package org.grails.forge.rocker.plugin;

import com.fizzed.rocker.compiler.RockerOptions;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@CacheableTask
public abstract class RockerTask extends DefaultTask {


    /**
     * @return the config
     */
    @Nested
    public abstract Property<RockerConfiguration> getRockerProjectConfig();

    /**
     * @return the templateDirs
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTemplateDirs();

    /**
     * @return the outputDir
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Classpath
    public abstract DirectoryProperty getClassDir();

    /**
     * Sets up the logger and runs the rocker compiler
     */
    @TaskAction
    public void compileRocker() {
        RockerConfiguration ext = (RockerConfiguration)
                getProject().getExtensions().findByName("rocker");
        Set<File> templateDirs = getTemplateDirs().getFiles();
        for (File templateDir : templateDirs) {
            doCompileRocker(ext, getLogger(), templateDir, getOutputDir().get().getAsFile(), getClassDir().get().getAsFile());
        }
    }

    /**
     * Uses the project to get GradleExtension and runs the generator
     */
    public static void doCompileRocker(RockerConfiguration ext,
                                       Logger logger,
                                       File templateDir, File outputDir, File classDir) {

        runJavaGeneratorMain(ext, logger, templateDir, outputDir, classDir);
    }

    /**
     * Run the Rocker compiler with the RockerGradleExtension as configuration
     * options.
     *
     * @param ext the extension from the project object
     */
    private static void runJavaGeneratorMain(RockerConfiguration ext,
                                             Logger logger,
                                             File templateDir, File outputDir, File classDir) {
        if (ext.getSkip().get()) {
            logger.info("Skip flag is on, will skip goal.");
            return;
        }

        logger.info("Targeting java version " + ext.getJavaVersion().get());

        try {
            JavaGeneratorRunnable jgm = new JavaGeneratorRunnable();

            jgm.getParser().getConfiguration().setTemplateDirectory(templateDir);
            jgm.getGenerator().getConfiguration().setOutputDirectory(outputDir);
            jgm.getGenerator().getConfiguration().setClassDirectory(classDir);
            jgm.setFailOnError(ext.getFailOnError().get());

            // passthru other config
            if (ext.getSuffixRegex().isPresent()) {
                jgm.setSuffixRegex(ext.getSuffixRegex().get());
            }
            RockerOptions rockerOptions = jgm.getParser().getConfiguration().getOptions();
            rockerOptions.setJavaVersion(ext.getJavaVersion().get());
            if (ext.getExtendsClass().isPresent()) {
                rockerOptions.setExtendsClass(ext.getExtendsClass().get());
            }
            if (ext.getExtendsModelClass().isPresent()) {
                rockerOptions.setExtendsModelClass(ext.getExtendsModelClass().get());
            }
            if (ext.getDiscardLogicWhitespace().isPresent()) {
                rockerOptions.setDiscardLogicWhitespace(ext.getDiscardLogicWhitespace().get());
            }
            if (ext.getTargetCharset().isPresent()) {
                rockerOptions.setTargetCharset(ext.getTargetCharset().get());
            }
            if (ext.getOptimize().isPresent()) {
                rockerOptions.setOptimize(ext.getOptimize().get());
            }
            if (ext.getPostProcessing().isPresent()) {
                List<String> postProcessing = ext.getPostProcessing().get();
                if (!postProcessing.isEmpty()) {
                    rockerOptions.setPostProcessing(postProcessing.toArray(new String[0]));
                }
            }
            if (ext.getMarkAsGenerated().isPresent()) {
                rockerOptions.setMarkAsGenerated(ext.getMarkAsGenerated().get());
            }

            jgm.run();

        } catch (Exception e) {
            throw new RockerGradleException(e.getMessage(), e);
        }

        if (!ext.getSkipTouch().get()) {
            String touchFile = ext.getTouchFile().orElse("").get();
            if (touchFile.length() == 0) {
                throw new RockerGradleException(
                        "If skipTouch is equal to false, then "
                                + "touchFile must not be empty");
            }
            if (ext.getTouchFile().isPresent()) {
                File f = new File(touchFile);
                logger.info("Touching file " + f);
                try {
                    if (!f.exists()) {
                        new FileOutputStream(f).close();
                    }
                    if (!f.setLastModified(System.currentTimeMillis())) {
                        throw new IOException("Could not set Last Modified");
                    }
                } catch (IOException e) {
                    logger.debug("Unable to touch file: " + f.getAbsolutePath(), e);
                }
            }
        }
    }

}
