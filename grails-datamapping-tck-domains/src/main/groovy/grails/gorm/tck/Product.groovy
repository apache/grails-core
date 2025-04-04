package grails.gorm.tck

import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@CompileStatic
@Entity
class Product {

    String name
    String color

    static Number removeAllByColor(String givenColor) {
        whereLazy { color == givenColor }.deleteAll()
    }

    static Number updateAll(String givenColor) {
        whereLazy {color == givenColor}.updateAll([name: 't-shirt ' + givenColor])
    }
}