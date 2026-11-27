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
package org.apache.grails.data.hibernate7.dbmigration.cli

import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.RuntimeEnvironment
import liquibase.Scope
import liquibase.changelog.ChangeLogIterator
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.filter.ContextChangeSetFilter
import liquibase.changelog.filter.CountChangeSetFilter
import liquibase.changelog.filter.DbmsChangeSetFilter
import liquibase.command.CommandScope
import liquibase.command.core.DiffChangelogCommandStep
import liquibase.command.core.DiffCommandStep
import liquibase.command.core.GenerateChangelogCommandStep
import liquibase.command.core.helpers.AbstractChangelogCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.command.core.helpers.DiffOutputControlCommandStep
import liquibase.command.core.helpers.PreCompareCommandStep
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.database.DatabaseConnection
import liquibase.database.DatabaseFactory
import liquibase.database.core.MSSQLDatabase
import liquibase.database.core.OracleDatabase
import liquibase.diff.compare.CompareControl
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.StandardObjectChangeFilter
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.exception.LockException
import liquibase.executor.Executor
import liquibase.executor.ExecutorService
import liquibase.executor.LoggingExecutor
import liquibase.lockservice.LockService
import liquibase.lockservice.LockServiceFactory
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.CompositeResourceAccessor
import liquibase.resource.FileSystemResourceAccessor
import liquibase.resource.ResourceAccessor
import liquibase.statement.core.RawSqlStatement
import liquibase.structure.core.Catalog
import liquibase.util.LiquibaseUtil
import liquibase.util.StreamUtil

import grails.config.ConfigMap
import grails.util.BuildSettings
import org.grails.build.parsing.CommandLine
import org.grails.plugins.databasemigration.DatabaseMigrationException
import org.grails.plugins.databasemigration.NoopVisitor

import static org.grails.plugins.databasemigration.DatabaseMigrationGrailsPlugin.getDataSourceName
import static org.grails.plugins.databasemigration.DatabaseMigrationGrailsPlugin.isDefaultDataSource
import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY
import static org.grails.plugins.databasemigration.PluginConstants.DEFAULT_CHANGE_LOG_LOCATION

@CompileStatic
trait DatabaseMigrationCommand {

    CommandLine commandLine

    String defaultSchema
    String dataSource
    String contexts

    abstract ConfigMap getConfig()

    String optionValue(String name) {
        commandLine.optionValue(name)?.toString()
    }

    boolean hasOption(String name) {
        commandLine.hasOption(name)
    }

    String getContexts() {
        if (contexts) {
            return contexts
        }
        return config.getProperty("${configPrefix}.contexts".toString(), List)?.join(',')
    }

    List<String> getArgs() {
        commandLine.remainingArgs
    }

    File getChangeLogLocation() {
        new File(config.getProperty("${configPrefix}.changelogLocation".toString(), String) ?: DEFAULT_CHANGE_LOG_LOCATION)
    }

    File getChangeLogFile() {
        new File(changeLogLocation, changeLogFileName)
    }

    String getChangeLogFileName() {
        def changelogFileName = config.getProperty("${configPrefix}.changelogFileName".toString(), String)
        if (changelogFileName) {
            return changelogFileName
        }
        return isDefaultDataSource(dataSource) ? 'changelog.groovy' : "changelog-${dataSource}.groovy"
    }

    File resolveChangeLogFile(String filename) {
        if (!filename) {
            return null
        }
        if (getExtension(filename)) {
            return new File(changeLogLocation, filename)
        }
        // a change log of a data source other than the default is named for it, as getChangeLogFileName names the root one
        if (!isDefaultDataSource(dataSource)) {
            return new File(changeLogLocation, "${filename}-${dataSource}.groovy")
        }
        return new File(changeLogLocation, "${filename}.groovy")
    }

    Map<String, String> getDataSourceConfig(ConfigMap config = this.config) {
        def dataSourceName = dataSource ?: 'dataSource'

        if (dataSourceName == 'dataSource' && config.containsKey(dataSourceName)) {
            return (Map<String, String>) (config.getProperty(dataSourceName, Map) ?: [:])
        }

        Map dataSourcesMap = (Map) config.getProperty('dataSources', Map)
        if (dataSourcesMap == null) {
            dataSourcesMap = [:]
        }
        if (dataSourcesMap.isEmpty()) {
            def defaultDataSource = config.getProperty('dataSource', Map)
            if (defaultDataSource) {
                dataSourcesMap['dataSource'] = defaultDataSource
            }
        }
        return (Map<String, String>) dataSourcesMap.get(dataSourceName)
    }

    /**
     * Writes the output of a tool to the standard output, or to the file it was given under the rule of
     * {@link #outputFile}.
     *
     * @param filename the file the tool was given; {@code null} or empty for the standard output
     * @param closure what writes the output
     */
    void withFileOrSystemOutWriter(String filename, @ClosureParams(value = SimpleType, options = 'java.io.Writer') Closure closure) {
        withOutputWriter(outputFile(filename), closure)
    }

    /**
     * The file a tool writes its output to, for the name it was given: resolved against the directory of the
     * project, and only where that is inside the project, all links followed, and where no file of the name is
     * there yet. A tool asks for it before it does anything else, so that a name it would not write to stops it
     * before it opens the database. Directories of the path that are not there yet are made when the file is
     * written, inside the project.
     *
     * @param filename the name the tool was given; {@code null} or empty for the standard output
     * @return the file, which {@link #withOutputWriter} and {@link #withOutputStream} create; {@code null} for the
     * standard output
     * @throws DatabaseMigrationException when the file would be outside the project, or is there already
     */
    File outputFile(String filename) {
        if (!filename) {
            return null
        }
        Path project = BuildSettings.BASE_DIR.toPath().toAbsolutePath()
        Path projectReal = project.toRealPath()
        Path given = Paths.get(filename)
        Path target = realTarget(given.isAbsolute() ? given : project.resolve(given))
        if (target == null || !target.startsWith(projectReal)) {
            // a path is false to Groovy when it is not there, so the one that was found is asked for by null
            Path shown = target != null ? target : given
            throw new DatabaseMigrationException("The output file ${shown} is outside the project ${projectReal}: name a file inside the project")
        }
        if (target == projectReal || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new DatabaseMigrationException(thereAlready(target))
        }
        target.toFile()
    }

    /**
     * Writes the output of a tool to the standard output, or to a new file.
     *
     * @param outputFile the file {@link #outputFile} gave; {@code null} for the standard output
     * @param closure what writes the output
     */
    void withOutputWriter(File outputFile, @ClosureParams(value = SimpleType, options = 'java.io.Writer') Closure closure) {
        if (outputFile == null) {
            closure.call(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)))
            return
        }
        withOutputStream(outputFile) { OutputStream out ->
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))
            closure.call(writer)
            writer.flush()
        }
    }

    /**
     * Writes the output of a tool to the standard output, or to a new file: the file is created, and is not
     * written when something of the name is there by then. When writing the output fails, the file is removed
     * again.
     *
     * @param outputFile the file {@link #outputFile} gave; {@code null} for the standard output
     * @param closure what writes the output
     */
    void withOutputStream(File outputFile, @ClosureParams(value = SimpleType, options = 'java.io.OutputStream') Closure closure) {
        if (outputFile == null) {
            closure.call(System.out)
            return
        }
        Path path = outputFile.toPath()
        Files.createDirectories(path.parent)
        OutputStream out
        try {
            out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (FileAlreadyExistsException ignored) {
            throw new DatabaseMigrationException(thereAlready(path))
        }
        boolean written = false
        try {
            closure.call(out)
            out.flush()
            written = true
        } finally {
            out.close()
            if (!written) {
                Files.deleteIfExists(path)
            }
        }
    }

    private String thereAlready(Path file) {
        "The output file ${file} is there already: remove it or name another file".toString()
    }

    // where the file of a path is: the nearest directory of the path that is there, with its links followed, and
    // the names after it, which are not there yet and so are no links; null when the path leads nowhere
    private Path realTarget(Path absolute) {
        Path name = absolute.fileName
        if (name == null) {
            return null
        }
        List<String> missing = []
        Path existing = absolute.parent
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(0, existing.fileName.toString())
            existing = existing.parent
        }
        if (existing == null) {
            return null
        }
        Path target
        try {
            target = existing.toRealPath()
        } catch (IOException ignored) {
            return null
        }
        for (String each : missing) {
            target = target.resolve(each)
        }
        target.resolve(name.toString()).normalize()
    }

    boolean isTimeFormat(String time) {
        time ==~ /\d{2}:\d{2}:\d{2}/
    }

    Date parseDateTime(String date, String time) throws ParseException {
        time = time ?: '00:00:00'
        DateFormat formatter = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss')
        formatter.parse("$date $time")
    }

    void withLiquibase(@ClosureParams(value = SimpleType, options = 'liquibase.Liquibase') Closure closure) {
        def resourceAccessor = createResourceAccessor()

        Path changeLogLocationPath = changeLogLocation.toPath()
        Path changeLogFilePath = changeLogFile.toPath()
        String relativePath = changeLogLocationPath.relativize(changeLogFilePath).toString()

        withDatabase { Database database ->
            Liquibase liquibase = new Liquibase(relativePath, resourceAccessor, database)
            liquibase.changeLogParameters.set(DATA_SOURCE_NAME_KEY, getDataSourceName(dataSource))
            closure.call(liquibase)
        }
    }

    ResourceAccessor createResourceAccessor() {
        // Avoid duplicates when migrations have been copied to the classpath
        if (Thread.currentThread().contextClassLoader?.getResource(changeLogFile.name)) {
            return new CompositeResourceAccessor(new ClassLoaderResourceAccessor())
        } else {
            return new CompositeResourceAccessor(new FileSystemResourceAccessor(changeLogLocation))
        }
    }

    void withDatabase(Map<String, String> dataSourceConfig = null, @ClosureParams(value = SimpleType, options = 'liquibase.database.Database') Closure closure) {
        def database = null
        try {
            database = createDatabase(defaultSchema, dataSource, dataSourceConfig ?: getDataSourceConfig())
            closure.call(database)
        } finally {
            database?.close()
        }
    }

    @CompileDynamic
    Database createDatabase(String defaultSchema, String dataSource, Map<String, String> dataSourceConfig) {
        String password = dataSourceConfig.password ?: null

        if (password && dataSourceConfig.passwordEncryptionCodec) {
            def clazz = Class.forName(dataSourceConfig.passwordEncryptionCodec)
            password = clazz.decode(password)
        }

        Database database = DatabaseFactory.getInstance().openDatabase(
                dataSourceConfig.url,
                dataSourceConfig.username ?: null,
                password,
                dataSourceConfig.driverClassName,
                null,
                null,
                null,
                new ClassLoaderResourceAccessor(Thread.currentThread().contextClassLoader)
        )
        configureDatabase(database)
        return database
    }

    void configureDatabase(Database database) {
        database.defaultSchemaName = defaultSchema
        if (!database.supportsSchemas() && defaultSchema) {
            database.defaultCatalogName = defaultSchema
        }
        database.databaseChangeLogTableName = config.getProperty("${configPrefix}.databaseChangeLogTableName".toString(), String)
        database.databaseChangeLogLockTableName = config.getProperty("${configPrefix}.databaseChangeLogLockTableName".toString(), String)
    }

    void doGenerateChangeLog(File changeLogFile, Database originalDatabase) {
        def changeLogFilePath = changeLogFile?.path
        def compareControl = new CompareControl([] as CompareControl.SchemaComparison[], null as String)
        DiffOutputControl diffOutputControl = createDiffOutputControl()

        final CommandScope command = new CommandScope('groovyGenerateChangeLog')
        command
                .addArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG, originalDatabase)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, originalDatabase)
                .addArgumentValue(PreCompareCommandStep.SNAPSHOT_TYPES_ARG, DiffCommandStep.parseSnapshotTypes(null as String))
                .addArgumentValue(PreCompareCommandStep.COMPARE_CONTROL_ARG, compareControl)
                .addArgumentValue(PreCompareCommandStep.OBJECT_CHANGE_FILTER_ARG, diffOutputControl.objectChangeFilter)
                .addArgumentValue(DiffChangelogCommandStep.CHANGELOG_FILE_ARG, changeLogFilePath)
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_CATALOG_ARG, diffOutputControl.getIncludeCatalog())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_SCHEMA_ARG, diffOutputControl.getIncludeSchema())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_TABLESPACE_ARG, diffOutputControl.getIncludeTablespace())
                .addArgumentValue(GenerateChangelogCommandStep.OVERWRITE_OUTPUT_FILE_ARG, GenerateChangelogCommandStep.OVERWRITE_OUTPUT_FILE_ARG.getDefaultValue())
                .addArgumentValue(GenerateChangelogCommandStep.RUN_ON_CHANGE_TYPES_ARG, AbstractChangelogCommandStep.RUN_ON_CHANGE_TYPES_ARG.getDefaultValue())
                .addArgumentValue(GenerateChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG, AbstractChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG.getDefaultValue())

        if (diffOutputControl.isReplaceIfExistsSet()) {
            command.addArgumentValue(GenerateChangelogCommandStep.USE_OR_REPLACE_OPTION, true)
        }
        command.setOutput(System.out)
        command.execute()
    }

    void doDiffToChangeLog(File changeLogFile, Database referenceDatabase, Database targetDatabase) {
        def changeLogFilePath = changeLogFile?.path
        def compareControl = new CompareControl([] as CompareControl.SchemaComparison[], null as String)
        DiffOutputControl diffOutputControl = createDiffOutputControl()

        final CommandScope command = new CommandScope('groovyDiffChangelog')
        command
                .addArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG, referenceDatabase)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, targetDatabase)
                .addArgumentValue(PreCompareCommandStep.SNAPSHOT_TYPES_ARG, DiffCommandStep.parseSnapshotTypes(null as String))
                .addArgumentValue(PreCompareCommandStep.COMPARE_CONTROL_ARG, compareControl)
                .addArgumentValue(PreCompareCommandStep.OBJECT_CHANGE_FILTER_ARG, diffOutputControl.objectChangeFilter)
                .addArgumentValue(DiffChangelogCommandStep.CHANGELOG_FILE_ARG, changeLogFilePath)
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_CATALOG_ARG, diffOutputControl.getIncludeCatalog())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_SCHEMA_ARG, diffOutputControl.getIncludeSchema())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_TABLESPACE_ARG, diffOutputControl.getIncludeTablespace())
                .addArgumentValue(GenerateChangelogCommandStep.RUN_ON_CHANGE_TYPES_ARG, AbstractChangelogCommandStep.RUN_ON_CHANGE_TYPES_ARG.getDefaultValue())
                .addArgumentValue(GenerateChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG, AbstractChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG.getDefaultValue())

        if (diffOutputControl.isReplaceIfExistsSet()) {
            command.addArgumentValue(GenerateChangelogCommandStep.USE_OR_REPLACE_OPTION, true)
        }
        command.setOutput(System.out)
        command.execute()

    }

    void doGeneratePreviousChangesetSql(Writer output, Database database, Liquibase liquibase, String count, String skip) {
        Contexts contexts = new Contexts(contexts)
        LabelExpression labelExpression = liquibase.changeLogParameters.labels
        liquibase.changeLogParameters.setContexts(contexts)

        final ExecutorService executorService = Scope.getCurrentScope().getSingleton(ExecutorService)
        final Executor oldTemplate = executorService.getExecutor('jdbc', database)
        final LoggingExecutor outputTemplate = new LoggingExecutor(oldTemplate, output, database)
        executorService.setExecutor('jdbc', database, outputTemplate)

        outputHeader(outputTemplate, (String) "Previous $count SQL Changeset(s) Skipping $skip Script", liquibase, database)

        LockService lockService = LockServiceFactory.getInstance().getLockService(database)
        lockService.waitForLock()

        try {
            def parser = ChangeLogParserFactory.instance.getParser(liquibase.changeLogFile, liquibase.resourceAccessor)
            DatabaseChangeLog changeLog = parser.parse(liquibase.changeLogFile, liquibase.changeLogParameters, liquibase.resourceAccessor)
            liquibase.checkLiquibaseTables(true, changeLog, contexts, labelExpression)
            changeLog.validate(database, contexts, labelExpression)
            changeLog.changeSets.reverse(true)
            skip.toInteger().times { changeLog.changeSets.remove(0) }

            ChangeLogIterator logIterator = new ChangeLogIterator(changeLog,
                    new ContextChangeSetFilter(contexts),
                    new DbmsChangeSetFilter(database),
                    new CountChangeSetFilter(count.toInteger()))

            logIterator.run(new NoopVisitor(database), new RuntimeEnvironment(database, contexts, labelExpression))

            output.flush()
        } finally {
            try {
                lockService.releaseLock()
                executorService.setExecutor('jdbc', database, oldTemplate)
            } catch (LockException e) {
                throw new LiquibaseException(e.message, e.cause)
            }
        }
    }

    void outputHeader(Executor executor, String message, Liquibase liquibase, Database database) throws DatabaseException {
        executor.comment('*********************************************************************')
        executor.comment(message)
        executor.comment('*********************************************************************')
        executor.comment('Change Log: ' + liquibase.changeLogFile)
        executor.comment('Ran at: ' + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()))
        DatabaseConnection connection = liquibase.getDatabase().getConnection()
        if (connection != null) {
            executor.comment('Against: ' + connection.getConnectionUserName() + '@' + connection.getURL())
        }
        executor.comment('Liquibase version: ' + LiquibaseUtil.getBuildVersion())
        executor.comment('*********************************************************************' + StreamUtil.getLineSeparator())

        if (database instanceof OracleDatabase) {
            executor.execute(new RawSqlStatement('SET DEFINE OFF;'))
        }
        if (database instanceof MSSQLDatabase && database.getDefaultCatalogName() != null) {
            executor.execute(new RawSqlStatement('USE ' + database.escapeObjectName(database.getDefaultCatalogName(), Catalog) + ';'))
        }
    }

    private DiffOutputControl createDiffOutputControl() {
        def diffOutputControl = new DiffOutputControl(false, false, false)

        String excludeObjects = config.getProperty("${configPrefix}.excludeObjects".toString(), String)
        String includeObjects = config.getProperty("${configPrefix}.includeObjects".toString(), String)
        if (excludeObjects && includeObjects) {
            throw new DatabaseMigrationException('Cannot specify both excludeObjects and includeObjects')
        }
        if (excludeObjects) {
            diffOutputControl.objectChangeFilter = new StandardObjectChangeFilter(StandardObjectChangeFilter.FilterType.EXCLUDE, excludeObjects)
        }
        if (includeObjects) {
            diffOutputControl.objectChangeFilter = new StandardObjectChangeFilter(StandardObjectChangeFilter.FilterType.INCLUDE, includeObjects)
        }

        diffOutputControl
    }

    void appendToChangeLog(File srcChangeLogFile, File destChangeLogFile) {
        if (!srcChangeLogFile.exists() || srcChangeLogFile == destChangeLogFile) {
            return
        }

        def relativePath = changeLogLocation.toPath().relativize(destChangeLogFile.toPath()).toString()
        def extension = getExtension(srcChangeLogFile.name)?.toLowerCase()

        switch (extension) {
            case ['yaml', 'yml']:
                srcChangeLogFile << """
                |- include:
                |    file: ${relativePath}
                """.stripMargin().trim()
                break
            case ['xml']:
                def text = srcChangeLogFile.text
                if (text =~ '<databaseChangeLog[^>]*/>') {
                    srcChangeLogFile.write(text.replaceFirst('(<databaseChangeLog[^>]*)/>', "\$1>\n    <include file=\"$relativePath\"/>\n</databaseChangeLog>"))
                } else {
                    srcChangeLogFile.write(text.replaceFirst('</databaseChangeLog>', "    <include file=\"$relativePath\"/>\n\$0"))
                }
                break
            case ['groovy']:
                def text = srcChangeLogFile.text
                srcChangeLogFile.write(text.replaceFirst('}.*$', "    include file: '$relativePath'\n\$0"))
                break
        }
    }

    String getConfigPrefix() {
        return isDefaultDataSource(dataSource) ?
                'grails.plugin.databasemigration' : "grails.plugin.databasemigration.${dataSource}"
    }

    private String getExtension(String fileName) {
        String extension = ''

        int i = fileName.lastIndexOf('.')
        if (i > 0) {
            extension = fileName.substring(i + 1)
        }
        extension
    }
}
