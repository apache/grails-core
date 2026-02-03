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



