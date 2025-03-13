package example

import grails.gorm.transactions.Transactional
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class Application implements CommandLineRunner{

    static void main(String[] args) {
        SpringApplication.run(Application.class, args)
    }

    @Override
    @Transactional
    void run(String... args) throws Exception {
        new Book(title: "The Stand").save()
        new Book(title: "The Shining").save()
        new Book(title: "It").save()
    }
}