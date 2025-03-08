import org.grails.plugins.databasemigration.DatabaseMigrationException
import org.grails.plugins.databasemigration.command.DbmChangelogToGroovy

description('Converts a changelog file to a Groovy DSL file') {
    usage 'grails [environment] dbm-changelog-to-groovy [src_file_name] [dest_file_name]'
    flag name: 'src_file_name', description: 'The name and path of the changelog file to convert'
    flag name: 'dest_file_name', description: 'The name and path of the Groovy file'
    flag name: 'dataSource', description: 'if provided will run the script for the specified dataSource creating a file named changelog-dataSource.groovy if a filename is not given. Not needed for the default dataSource'
    flag name: 'force', description: 'Whether to overwrite existing files'
    flag name: 'add', description: 'if provided will run the script for the specified dataSource. Not needed for the default dataSource.'
}

try {
    new DbmChangelogToGroovy().handle(executionContext)
} catch (DatabaseMigrationException e) {
    error e.message, e
}
