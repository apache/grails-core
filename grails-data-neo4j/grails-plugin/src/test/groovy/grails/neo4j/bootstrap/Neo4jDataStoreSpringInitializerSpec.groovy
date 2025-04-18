package grails.neo4j.bootstrap

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.spring.DefaultRuntimeSpringConfiguration
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification
/*
 * Copyright 2014 original authors
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

/**
 * @author graemerocher
 */
class Neo4jDataStoreSpringInitializerSpec extends Specification {

    void "Test Neo4jDataStoreSpringInitializer loads neo4j correctly"() {
        setup:"neo4j is initialised"
        def config = new StandardEnvironment()
        config.propertySources.addFirst(new MapPropertySource("test",
                [(Settings.SETTING_NEO4J_TYPE): Settings.DATABASE_TYPE_EMBEDDED]
        ))
        def init = new Neo4jDataStoreSpringInitializer(config, Author, Book)
        def ctx = init.configure()

        when:"A GORm method is executed"
        int count = Book.count()
        then:"GORM for Neo4j is correctly configured"
        count == 0

        when:
        Author author
        Author.withTransaction {
            author = new Author(name: "Stephen King")
                    .addToBooks(title: "")
            author.validate()
        }

        then:"GORM for Neo4j is correctly configured"
        author.errors.hasErrors()

        cleanup:
        ctx.close()
    }

    void "Test configuration from map Neo4jDataStoreSpringInitializer loads neo4j correctly"() {
        when:"neo4j is initialised"

        def config = [ (Settings.SETTING_NEO4J_URL):"jdbc:foo:bar", (Settings.SETTING_NEO4J_DRIVER_PROPERTIES):[one:"two"]]
        def init = new Neo4jDataStoreSpringInitializer(config,Book)
//        init.configure()

        then:"GORM for Neo4j is correctly configured"
        init.configuration.getProperty("grails.neo4j.url") == "jdbc:foo:bar"
        init.configuration.getProperty("grails.neo4j.options", Map.class) == [one:"two"]

    }
}

@Entity
class Author {
    String name
    static hasMany = [books:Book]

    static constraints = {
        name blank:false
    }
}
@Entity
class Book {
    String title
    static belongsTo = [author:Author]
    static constraints = {
        title blank:false
    }
}
