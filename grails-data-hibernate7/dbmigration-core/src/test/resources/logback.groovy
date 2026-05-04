// See http://logback.qos.ch/manual/groovy.html for details on configuration
def CONSOLE_LOG_PATTERN = '%d{HH:mm:ss.SSS} [%t] %highlight(%p) %cyan(\\(%logger{39}\\)) %m%n'

appender('STDOUT', ConsoleAppender) {
    withJansi = true
    encoder(PatternLayoutEncoder) {
        pattern = CONSOLE_LOG_PATTERN
    }
}
root(ERROR, ['STDOUT'])

//logger("org.grails", DEBUG, ['STDOUT'], false)
logger("liquibase", DEBUG, ['STDOUT'], false)
//logger("groovy.sql", DEBUG, ['STDOUT'], false)
//logger("org.hibernate.SQL", DEBUG, ["STDOUT"], false)
logger("org.grails.datastore.gorm.GormEnhancer", INFO, ['STDOUT'], false)
logger("org.grails.plugin.datasource.TomcatJDBCPoolMBeanExporter", WARN, ['STDOUT'], false)



