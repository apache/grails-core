import org.grails.plugins.databasemigration.DatabaseMigrationException
import org.grails.plugins.databasemigration.command.DbmCreateChangelog

description('Creates an empty changelog file') {
    usage 'grails [environment] dbm-create-changelog [filename]'
    flag name: 'filename', description: 'The path to the output file to write to'
    flag name: 'dataSource', description: 'if provided will run the script for the specified dataSource creating a file named changelog-dataSource.groovy if a filename is not given. Not needed for the default dataSource'
    flag name: 'force', description: 'Whether to overwrite existing files'
    flag name: 'add', description: 'if provided will run the script for the specified dataSource. Not needed for the default dataSource.'
}

try {
    new DbmCreateChangelog().handle(executionContext)
} catch (DatabaseMigrationException e) {
    error e.message, e
}
