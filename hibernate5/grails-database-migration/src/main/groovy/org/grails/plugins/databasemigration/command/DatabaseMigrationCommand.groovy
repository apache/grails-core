/*
 * Copyright 2015 original authors
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
package org.grails.plugins.databasemigration.command

import grails.config.ConfigMap
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
import liquibase.command.core.helpers.DbUrlConnectionCommandStep
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
import org.grails.build.parsing.CommandLine
import org.grails.plugins.databasemigration.DatabaseMigrationException
import org.grails.plugins.databasemigration.NoopVisitor

import java.nio.file.Path
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

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
        if (dataSource) {
            return new File(changeLogLocation, "${filename}-${dataSource}.groovy")
        }
        return new File(changeLogLocation, "${filename}.groovy")
    }

    Map<String, String> getDataSourceConfig(ConfigMap config = this.config) {
        def dataSourceName = dataSource ?: 'dataSource'

        if (dataSourceName == 'dataSource' && config.containsKey(dataSourceName)) {
            return (Map<String, String>) (config.getProperty(dataSourceName, Map) ?: [:])
        }

        def dataSources = config.getProperty('dataSources', Map) ?: [:]
        if (!dataSources) {
            def defaultDataSource = config.getProperty('dataSource', Map)
            if (defaultDataSource) {
                dataSources['dataSource'] = defaultDataSource
            }
        }
        return (Map<String, String>) dataSources.get(dataSourceName)
    }

    void withFileOrSystemOutWriter(String filename, @ClosureParams(value = SimpleType, options = "java.io.Writer") Closure closure) {
        if (!filename) {
            closure.call(new PrintWriter(System.out))
            return
        }

        def outputFile = new File(filename)
        if (outputFile.parentFile && !outputFile.parentFile.exists()) {
            outputFile.parentFile.mkdirs()
        }
        outputFile.withWriter { BufferedWriter writer ->
            closure.call(writer)
        }
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
        new CompositeResourceAccessor(
            new FileSystemResourceAccessor(changeLogLocation),
            new ClassLoaderResourceAccessor())

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

        final CommandScope command = new CommandScope("groovyGenerateChangeLog");
        command
                .addArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG, originalDatabase)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, originalDatabase)
                .addArgumentValue(PreCompareCommandStep.SNAPSHOT_TYPES_ARG, DiffCommandStep.parseSnapshotTypes(null as String))
                .addArgumentValue(PreCompareCommandStep.COMPARE_CONTROL_ARG, compareControl)
                .addArgumentValue(DiffChangelogCommandStep.CHANGELOG_FILE_ARG, changeLogFilePath)
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_CATALOG_ARG, diffOutputControl.getIncludeCatalog())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_SCHEMA_ARG, diffOutputControl.getIncludeSchema())
                .addArgumentValue(DiffOutputControlCommandStep.INCLUDE_TABLESPACE_ARG, diffOutputControl.getIncludeTablespace())
                .addArgumentValue(GenerateChangelogCommandStep.OVERWRITE_OUTPUT_FILE_ARG, GenerateChangelogCommandStep.OVERWRITE_OUTPUT_FILE_ARG.getDefaultValue())
                .addArgumentValue(GenerateChangelogCommandStep.RUN_ON_CHANGE_TYPES_ARG, AbstractChangelogCommandStep.RUN_ON_CHANGE_TYPES_ARG.getDefaultValue())
                .addArgumentValue(GenerateChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG, AbstractChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG.getDefaultValue());


        if(diffOutputControl.isReplaceIfExistsSet()) {
            command.addArgumentValue(GenerateChangelogCommandStep.USE_OR_REPLACE_OPTION, true)
        }
        command.setOutput(System.out)
        command.execute()
    }

    void doDiffToChangeLog(File changeLogFile, Database referenceDatabase, Database targetDatabase) {
        def changeLogFilePath = changeLogFile?.path
        def compareControl = new CompareControl([] as CompareControl.SchemaComparison[], null as String)
        DiffOutputControl diffOutputControl = createDiffOutputControl()

        final CommandScope command = new CommandScope("groovyDiffChangelog");
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
                .addArgumentValue(GenerateChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG, AbstractChangelogCommandStep.REPLACE_IF_EXISTS_TYPES_ARG.getDefaultValue());

        if(diffOutputControl.isReplaceIfExistsSet()) {
            command.addArgumentValue(GenerateChangelogCommandStep.USE_OR_REPLACE_OPTION, true)
        }
        command.setOutput(System.out)
        command.execute()

    }

    void doGeneratePreviousChangesetSql(Writer output, Database database, Liquibase liquibase, String count, String skip) {
        Contexts contexts = new Contexts(contexts)
        LabelExpression labelExpression = liquibase.changeLogParameters.labels
        liquibase.changeLogParameters.setContexts(contexts)

        final ExecutorService executorService = Scope.getCurrentScope().getSingleton(ExecutorService.class)
        final Executor oldTemplate = executorService.getExecutor("jdbc", database)
        final LoggingExecutor outputTemplate = new LoggingExecutor(oldTemplate, output, database)
        executorService.setExecutor("jdbc", database, outputTemplate)

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
                executorService.setExecutor("jdbc", database, oldTemplate)
            } catch (LockException e) {
                throw new LiquibaseException(e.message, e.cause)
            }
        }
    }

    void outputHeader(Executor executor, String message, Liquibase liquibase, Database database) throws DatabaseException {
        executor.comment("*********************************************************************")
        executor.comment(message)
        executor.comment("*********************************************************************")
        executor.comment("Change Log: " + liquibase.changeLogFile)
        executor.comment("Ran at: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date()))
        DatabaseConnection connection = liquibase.getDatabase().getConnection()
        if (connection != null) {
            executor.comment("Against: " + connection.getConnectionUserName() + "@" + connection.getURL())
        }
        executor.comment("Liquibase version: " + LiquibaseUtil.getBuildVersion())
        executor.comment("*********************************************************************" + StreamUtil.getLineSeparator())

        if (database instanceof OracleDatabase) {
            executor.execute(new RawSqlStatement("SET DEFINE OFF;"))
        }
        if (database instanceof MSSQLDatabase && database.getDefaultCatalogName() != null) {
            executor.execute(new RawSqlStatement("USE " + database.escapeObjectName(database.getDefaultCatalogName(), Catalog.class) + ";"))
        }
    }

    private DiffOutputControl createDiffOutputControl() {
        def diffOutputControl = new DiffOutputControl(false, false, false)

        String excludeObjects = config.getProperty("${configPrefix}.excludeObjects".toString(), String)
        String includeObjects = config.getProperty("${configPrefix}.includeObjects".toString(), String)
        if (excludeObjects && includeObjects) {
            throw new DatabaseMigrationException("Cannot specify both excludeObjects and includeObjects")
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
        String extension = ""

        int i = fileName.lastIndexOf('.')
        if (i > 0) {
            extension = fileName.substring(i+1)
        }
        extension
    }
}
