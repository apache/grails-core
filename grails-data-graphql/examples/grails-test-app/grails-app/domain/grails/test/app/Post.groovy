package grails.test.app

class Post {

    String title

    Date dateCreated
    Date lastUpdated

    static hasMany = [tags: Tag]

    static constraints = {
    }

    static graphql = true
}
