package example

import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class Application implements CommandLineRunner{

    @Autowired
    Neo4jDatastore datastore

    static void main(String[] args) {
        SpringApplication.run(Application.class, args)
    }

    @Bean
    BookService bookService() {
        datastore.getService(BookService)
    }

    @Override
    @Transactional
    void run(String... args) throws Exception {
        new Book(title: "The Stand").save()
        new Book(title: "The Shining").save()
        new Book(title: "It").save()
    }
}