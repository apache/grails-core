package grails.gorm.tests

import grails.persistence.Entity

import spock.lang.PendingFeature
import spock.lang.PendingFeatureIf

/**
 * @author graemerocher
 */
class NamedQuerySpec extends GormDatastoreSpec {

    void "Test Named Query Passing Multiple Params To Nested Named Query"() {
        given:
        def now = new Date()

        new Publication(title: "Some Book",
                datePublished: now - 10, paperback: false).save()
        new Publication(title: "Some Book",
                datePublished: now - 1000, paperback: true).save()
        new Publication(title: "Some Book",
                datePublished: now - 2, paperback: true).save()

        new Publication(title: "Some Title",
                datePublished: now - 2, paperback: false).save()
        new Publication(title: "Some Title",
                datePublished: now - 1000, paperback: false).save()
        new Publication(title: "Some Title",
                datePublished: now - 2, paperback: true).save(flush:true)
        session.clear()

        expect:
        Publication.count() == 6

        when:
        def results = Publication.thisWeeksPaperbacks().list()

        then:
        results?.size() == 2

        when:
        results = Publication.queryThatNestsMultipleLevels().list()

        then:
        results?.size() == 2
    }

    void "Test findWhere method after chaining named queries"() {
        given:
        def now = new Date()

        new Publication(title: "Book 1",
                datePublished: now - 10, paperback: false).save()
        new Publication(title: "Book 2",
                datePublished: now - 1000, paperback: true).save()
        new Publication(title: "Book 3",
                datePublished: now - 10, paperback: true).save()

        new Publication(title: "Some Title",
                datePublished: now - 10, paperback: false).save()
        new Publication(title: "Some Title",
                datePublished: now - 1000, paperback: false).save()
        new Publication(title: "Some Title",
                datePublished: now - 10, paperback: true).save(flush:true)
        session.clear()

        when:
        def results = Publication.recentPublications().publicationsWithBookInTitle().findAllWhere(paperback: true)

        then:
        1 == results?.size()
    }

    void "Test chaining named queries"() {

        given:
        def now = new Date()
        [true, false].each { isPaperback ->
            4.times {
                Publication.newInstance(
                        title: "Book Some",
                        datePublished: now - 10, paperback: isPaperback).save()
                Publication.newInstance(
                        title: "Book Some Other",
                        datePublished: now - 10, paperback: isPaperback).save()
                Publication.newInstance(
                        title: "Some Other Title",
                        datePublished: now - 10, paperback: isPaperback).save()
                Publication.newInstance(
                        title: "Book Some",
                        datePublished: now - 1000, paperback: isPaperback).save()
                Publication.newInstance(
                        title: "Book Some Other",
                        datePublished: now - 1000, paperback: isPaperback).save()
                Publication.newInstance(
                        title: "Some Other Title",
                        datePublished: now - 1000, paperback: isPaperback).save()
            }
        }
        session.flush()
        session.clear()

        when:
        def results = Publication.recentPublications().publicationsWithBookInTitle().list()

        then: "The result size should be 16 when returned from chained queries"
        16 == results?.size()

        when:
        results = Publication.recentPublications().publicationsWithBookInTitle().count()
        then:
        16 == results

        when:
        results = Publication.recentPublications.publicationsWithBookInTitle.list()
        then:"The result size should be 16 when returned from chained queries"
        16 == results?.size()

        when:
        results = Publication.recentPublications.publicationsWithBookInTitle.count()
        then:
        16 == results

        when:
        results = Publication.paperbacks().recentPublications().publicationsWithBookInTitle().list()
        then: "The result size should be 8 when returned from chained queries"
        8 ==  results?.size()

        when:
        results = Publication.paperbacks().recentPublications().publicationsWithBookInTitle().count()
        then:
        8 == results

        when:
        results = Publication.recentPublications().publicationsWithBookInTitle().findAllByPaperback(true)
        then: "The result size should be 8"
        8 == results?.size()

        when:
        results = Publication.paperbacks.recentPublications.publicationsWithBookInTitle.list()
        then:"The result size should be 8 when returned from chained queries"
        8 == results?.size()

        when:
        results = Publication.paperbacks.recentPublications.publicationsWithBookInTitle.count()
        then:
        8 == results
    }

    void "Test named query with disjunction"() {
        given:
            def now = new Date()
            def oldDate = now - 2000

            Publication.newInstance(title: 'New Paperback', datePublished: now, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'Old Paperback', datePublished: oldDate, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'New Hardback', datePublished: now, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Old Hardback', datePublished: oldDate, paperback: false).save(failOnError: true)
            session.flush()
            session.clear()

        when:
            def publications = Publication.paperbackOrRecent.list()

        then:
            3 == publications?.size()
    }

    void "Test max and offset parameter"() {
        given:
            (1..25).each {num ->
                Publication.newInstance(title: "Book Number ${num}",
                                        datePublished: new Date()).save()
            }

        when:
            def pubs = Publication.recentPublications.list(max: 10, offset: 5)

        then:
            10 == pubs?.size()

        when:
            pubs = Publication.recentPublications.list(max: '10', offset: '5')

        then:
            10 == pubs?.size()
    }

    void "Test that parameter to get is converted"() {

        given:
            def now = new Date()
            def newPublication = Publication.newInstance(title: "Some New Book", datePublished: now - 10).save(failOnError: true)
            def oldPublication = Publication.newInstance(title: "Some Old Book",
            datePublished: now - 900).save(flush:true, failOnError: true)
            session.clear()

        when:
            def publication = Publication.recentPublications.get(newPublication.id.toString())

        then:
            publication != null
            'Some New Book'== publication.title
    }

    void "Test named query with additional criteria closure"() {

        given:
            def now = new Date()
            6.times {
                Publication.newInstance(title: "Some Book",
                                               datePublished: now - 10).save(failOnError: true)
                Publication.newInstance(title: "Some Other Book",
                                               datePublished: now - 10).save(failOnError: true)
                Publication.newInstance(title: "Some Book",
                                               datePublished: now - 900).save(failOnError: true)
            }
            session.flush()
            session.clear()

        when:
            def publications = Publication.recentPublications {
                eq 'title', 'Some Book'
            }

        then:
            6 == publications?.size()

        when:
            publications = Publication.recentPublications {
                like 'title', 'Some%'
            }

        then:
            12 == publications?.size()

        when:
            def cnt = Publication.recentPublications.count {
                eq 'title', 'Some Book'
            }

        then:
            6 == cnt

        when:
            publications = Publication.recentPublications(max: 3) {
                like 'title', 'Some%'
            }

        then:
            3 == publications?.size()
    }

    void "Test passing parameters to additional criteria"() {
        given:
            def now = new Date()

            6.times { cnt ->
                new Publication(title: "Some Old Book #${cnt}",
                                datePublished: now - 1000, paperback: true).save(failOnError: true).id
                new Publication(title: "Some New Book #${cnt}",
                                datePublished: now, paperback: true).save(failOnError: true).id
            }

            session?.flush()

        when:
            def results = Publication.publishedAfter(now - 5) {
                eq 'paperback', true
            }

        then:
            6 == results?.size()

        when:
            results = Publication.publishedAfter(now - 5, [max: 2, offset: 1]) {
                eq 'paperback', true
            }

        then:
            2 == results?.size()

        when:
            results = Publication.publishedBetween(now - 5, now + 1) {
                eq 'paperback', true
            }

        then:
            6 == results?.size()

        when:
            results = Publication.publishedBetween(now - 5, now + 1, [max: 2, offset: 1]) {
                eq 'paperback', true
            }

        then:
            2 == results?.size()

        when:
            results = Publication.publishedAfter(now - 1005) {
                eq 'paperback', true
            }

        then:
            12 == results?.size()

        when:
            results = Publication.publishedAfter(now - 5) {
                eq 'paperback', false
            }

        then:
            0 == results?.size()

        when:
            results = Publication.publishedAfter(now - 5, [max: 2, offset: 1]) {
                eq 'paperback', false
            }

        then:
            0 == results?.size()

        when:
            results = Publication.publishedBetween(now - 5, now + 1) {
                eq 'paperback', false
            }

        then:
            0 == results?.size()

        when:
            results = Publication.publishedBetween(now - 5, now + 1, [max: 2, offset: 1]) {
                eq 'paperback', false
            }

        then:
            0 == results?.size()

        when:
            results = Publication.publishedAfter(now - 1005) {
                eq 'paperback', false
            }

        then:
            0 == results?.size()
    }

    void "Test get method followed named query chaining"() {
        given:
            def now = new Date()

            def oldPaperBackWithBookInTitleId =  new Publication(
                title: "Book 1",
                datePublished: now - 1000, paperback: true).save().id
            def newPaperBackWithBookInTitleId =  new Publication(
                title: "Book 2",
                datePublished: now, paperback: true).save().id

            session.flush()
            session.clear()

        when:
            def publication = Publication.publicationsWithBookInTitle().publishedAfter(now - 5).get(oldPaperBackWithBookInTitleId)

        then:
            publication == null

        when:
            publication = Publication.publishedAfter(now - 5).publicationsWithBookInTitle().get(oldPaperBackWithBookInTitleId)

        then:
            publication == null

        when:
            publication = Publication.publishedAfter(now - 5).publicationsWithBookInTitle().get(newPaperBackWithBookInTitleId)

        then:
            publication != null

        when:
            publication = Publication.publishedAfter(now - 5).publicationsWithBookInTitle().get(newPaperBackWithBookInTitleId)

        then:
            publication != null
    }

    void "Test named query with findBy*() dynamic finder"() {

        given:
            def now = new Date()
            Publication.newInstance(title: "Book 1", datePublished: now - 900).save(failOnError: true)
            def recentBookId = Publication.newInstance(
                title: "Book 1",
                datePublished: now - 10).save(flush:true).id
            session.clear()

        when:
            def publication = Publication.recentPublications.findByTitle('Book 1')

        then:
            publication != null
            recentBookId == publication.id
    }

    void "Test named query with findAllBy*() dyamic finder"() {
        given:
            def now = new Date()
            3.times {
                new Publication(title: "Some Recent Book",
                                       datePublished: now - 10).save(failOnError: true)
                new Publication(title: "Some Other Book",
                                       datePublished: now - 10).save(failOnError: true)
                new Publication(title: "Some Book",
                                       datePublished: now - 900).save(flush:true, failOnError: true)
            }
            session.clear()

        when:
            def publications = Publication.recentPublications.findAllByTitle('Some Recent Book')

        then:
            3 == publications?.size()
            'Some Recent Book' == publications[0].title
            'Some Recent Book' == publications[1].title
            'Some Recent Book' == publications[2].title
    }

    @PendingFeature(reason = 'queries on associations not yet supported')
    void "Test named query with relationships in criteria"() {

        given:

            new PlantCategory(name:"leafy")
                .addToPlants(goesInPatch:true, name:"Lettuce")
                .save(flush:true)

            new PlantCategory(name:"groovy")
                .addToPlants(goesInPatch: true, name: 'Gplant')
                .save(flush:true)

            new PlantCategory(name:"grapes")
                .addToPlants(goesInPatch:false, name:"Gray")
                .save(flush:true)

            session.clear()

        when:
            def results = PlantCategory.withPlantsInPatch.list()

        then:
            2 == results.size()
            true == 'leafy' in results*.name
            true == 'groovy' in results*.name

        when:
            results = PlantCategory.withPlantsThatStartWithG.list()

        then:
            2 == results.size()
            true == 'groovy' in results*.name
            true == 'grapes' in results*.name

        when:
            results = PlantCategory.withPlantsInPatchThatStartWithG.list()

        then:
            1 == results.size()
            'groovy' == results[0].name
    }

    @PendingFeature(reason = 'queries on associations not yet supported')
    void "Test list distinct entities"() {

        given:
            new PlantCategory(name:"leafy")
                .addToPlants(goesInPatch:true, name:"lettuce")
                .addToPlants(goesInPatch:true, name:"cabbage")
                .save(flush:true)

            new PlantCategory(name:"orange")
                .addToPlants(goesInPatch:true, name:"carrots")
                .addToPlants(goesInPatch:true, name:"pumpkin")
                .save(flush:true)

            new PlantCategory(name:"grapes")
                .addToPlants(goesInPatch:false, name:"red")
                .addToPlants(goesInPatch:false, name:"white")
                .save(flush:true)

            session.clear()

        when:
            def categories = plantCategoryClass.withPlantsInPatch().listDistinct()
            def names = categories*.name

        then:
            2 == categories.size()
            2 == names.size()
            true == 'leafy' in names
            true == 'orange' in names
    }

    @PendingFeature(reason = 'queries on associations not yet supported')
    void "Another test on listing distinct entities"() {
        given:
            new PlantCategory(name:"leafy")
                .addToPlants(goesInPatch:true, name:"lettuce")
                .addToPlants(goesInPatch:true, name:"cabbage")
                .save(flush:true)

            new PlantCategory(name:"orange")
                .addToPlants(goesInPatch:true, name:"carrots")
                .addToPlants(goesInPatch:true, name:"pumpkin")
                .save(flush:true)

            new PlantCategory(name:"grapes")
                .addToPlants(goesInPatch:false, name:"red")
                .addToPlants(goesInPatch:false, name:"white")
                .save(flush:true)

            session.clear()

        when:
            def categories = plantCategoryClass.withPlantsInPatch.listDistinct()
            def names = categories*.name

      then:
            3 == categories.size()
            3 == names.size()
            true == 'leafy' in names
            true == 'orange' in names
            true == 'grapes' in names
    }

    void 'Test uniqueResult'() {
        given:
            def now = new Date()

            new Publication(title: "Ten Day Old Paperback",
                            datePublished: now - 10,
                            paperback: true).save(flush: true)
            new Publication(title: "One Hundred Day Old Paperback",
                            datePublished: now - 100,
                            paperback: true).save(flush: true)
            session.clear()

        when:
            def result = Publication.lastPublishedBefore(now - 200).list()

        then:
            !result

        when:
            result = Publication.lastPublishedBefore(now - 50).get()

        then:
            'One Hundred Day Old Paperback' == result?.title
    }


    void "Test named query passing multiple parameters to a nested query"() {
        given:
            def now = new Date()

            new Publication(title: "Some Book",
                            datePublished: now - 10, paperback: false).save()
            new Publication(title: "Some Book",
                            datePublished: now - 1000, paperback: true).save()
            new Publication(title: "Some Book",
                            datePublished: now - 2, paperback: true).save()

            new Publication(title: "Some Title",
                            datePublished: now - 2, paperback: false).save()
            new Publication(title: "Some Title",
                            datePublished: now - 1000, paperback: false).save()
            new Publication(title: "Some Title",
                            datePublished: now - 2, paperback: true).save(flush:true)
            session.clear()

        when:
            def results = Publication.thisWeeksPaperbacks().list()

        then:
            2 == results?.size()
    }



    void testChainingQueriesWithParams() {
        def Publication = ga.getDomainClass("Publication").clazz

        def now = new Date()
        def lastWeek = now - 7
        def longAgo = now - 1000
        2.times {
            Publication.newInstance(title: 'Some Book',
                                           datePublished: now).save(failOnError: true)
            Publication.newInstance(title: 'Some Title',
                                           datePublished: now).save(failOnError: true)
        }
        3.times {
            Publication.newInstance(title: 'Some Book',
                                           datePublished: lastWeek).save(failOnError: true)
            Publication.newInstance(title: 'Some Title',
                                           datePublished: lastWeek).save(failOnError: true)
        }
        4.times {
            Publication.newInstance(title: 'Some Book',
                                           datePublished: longAgo).save(failOnError: true)
            Publication.newInstance(title: 'Some Title',
                                           datePublished: longAgo).save(failOnError: true)
        }
        session.clear()

        def results = Publication.recentPublicationsByTitle('Some Book').publishedAfter(now - 2).list()
        assertEquals 'wrong number of books were returned from chained queries', 2, results?.size()

        results = Publication.recentPublicationsByTitle('Some Book').publishedAfter(now - 2).count()
        assertEquals 2, results

        results = Publication.recentPublicationsByTitle('Some Book').publishedAfter(lastWeek - 2).list()
        assertEquals 'wrong number of books were returned from chained queries', 5, results?.size()

        results = Publication.recentPublicationsByTitle('Some Book').publishedAfter(lastWeek - 2).count()
        assertEquals 5, results
    }

    void "Test referencing named query before any dynamic methods"() {

        /*
         * currently this will work:
         *   Publication.recentPublications().list()
         * but this will not:
         *   Publication.recentPublications.list()
         *
         * the static property isn't being added to the class until
         * the first dynamic method (recentPublications(), save(), list() etc...) is
         * invoked
         */
        given:
        when:
            def publications = Publication.recentPublications.list()
        then:
            0 == publications.size()
    }

    void "Test named query with conjunction"() {
        given:
            def now = new Date()
            def oldDate = now - 2000

            Publication.newInstance(title: 'New Paperback', datePublished: now, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'Old Paperback', datePublished: oldDate, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'New Hardback', datePublished: now, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Old Hardback', datePublished: oldDate, paperback: false).save(failOnError: true)
            session.flush()
            session.clear()

        when:
            def publications = Publication.paperbackAndRecent.list()

        then:
            1 == publications?.size()
    }

    void "Test named query with list() method"() {

        given:
            def now = new Date()
            Publication.newInstance(title: "Some New Book",
                                           datePublished: now - 10).save(failOnError: true)
            Publication.newInstance(title: "Some Old Book",
                                           datePublished: now - 900).save(flush:true, failOnError: true)

            session.clear()

        when:
            def publications = Publication.recentPublications.list()

        then:
            1 == publications?.size()
            'Some New Book' == publications[0].title
    }

    @PendingFeature(reason = 'findby boolean queries not yet supported')
    void "Test named query with findAll by boolean property"() {
        given:
            def Publication = ga.getDomainClass("Publication").clazz
            def now = new Date()

            Publication.newInstance(title: 'Some Book', datePublished: now - 900, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Some Book', datePublished: now - 900, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Some Book', datePublished: now - 10, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'Some Book', datePublished: now - 10, paperback: true).save(failOnError: true)

        when:
            def publications = Publication.recentPublications.findAllPaperbackByTitle('Some Book')

        then:
            2 == publications?.size()
            publications[0].title == 'Some Book'
            publications[1].title == 'Some Book'
    }

    @PendingFeatureIf(
            value = { !(
                System.getProperty('hibernate5.gorm.suite') ||
                System.getProperty('mongodb.gorm.suite')
            )},
            reason = 'findby boolean queries not yet supported'
    )
    void "Test named query with find by boolean property"() {

        given:
            def now = new Date()

            Publication.newInstance(title: 'Some Book', datePublished: now - 900, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Some Book', datePublished: now - 900, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Some Book', datePublished: now - 10, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'Some Book', datePublished: now - 10, paperback: true).save(failOnError: true)

        when:
            def publication = Publication.recentPublications.findPaperbackByTitle('Some Book')

        then:
            publication.title == 'Some Book'
    }

    void "Test named query with countBy*() dynamic finder"() {
        given:
            def now = new Date()
            3.times {
                Publication.newInstance(title: "Some Book",
                                               datePublished: now - 10).save(failOnError: true)
                Publication.newInstance(title: "Some Other Book",
                                               datePublished: now - 10).save(failOnError: true)
                Publication.newInstance(title: "Some Book",
                                               datePublished: now - 900).save(flush:true, failOnError: true)
            }
            session.clear()

        when:
            def numberOfNewBooksNamedSomeBook = Publication.recentPublications.countByTitle('Some Book')

        then:
            3 == numberOfNewBooksNamedSomeBook
    }

    @PendingFeatureIf(
            value = { !(
                System.getProperty('hibernate5.gorm.suite') ||
                System.getProperty('mongodb.gorm.suite')
            )},
            reason = 'list order by not yet supported'
    )
    void "Test named query with listOrderBy*() dynamic finder"() {

        given:
            def now = new Date()

            Publication.newInstance(title: "Book 1", datePublished: now).save(failOnError: true)
            Publication.newInstance(title: "Book 5", datePublished: now).save(failOnError: true)
            Publication.newInstance(title: "Book 3", datePublished: now - 900).save(failOnError: true)
            Publication.newInstance(title: "Book 2", datePublished: now - 900).save(failOnError: true)
            Publication.newInstance(title: "Book 4", datePublished: now).save(flush:true, failOnError: true)
            session.clear()

        when:
            def publications = Publication.recentPublications.listOrderByTitle()

        then:
            3 == publications?.size()
            'Book 1' == publications[0].title
            'Book 4' == publications[1].title
            'Book 5'== publications[2].title
    }

    void "Test get with id of object which does not match criteria"() {

        given:
            def now = new Date()
            def hasBookInTitle = Publication.newInstance(
                title: "Book 1",
                datePublished: now - 10).save(failOnError: true)
            def doesNotHaveBookInTitle = Publication.newInstance(
                title: "Some Publication",
                datePublished: now - 900).save(flush:true, failOnError: true)

            session.clear()

        when:
            def result = Publication.publicationsWithBookInTitle.get(doesNotHaveBookInTitle.id)

        then:
            result == null
    }

    void "Test get method returns correct object"() {

        given:
            def now = new Date()
            def newPublication = Publication.newInstance(
                title: "Some New Book",
                datePublished: now - 10).save(failOnError: true)
            def oldPublication = Publication.newInstance(
                title: "Some Old Book",
                datePublished: now - 900).save(flush:true, failOnError: true)

            session.clear()

        when:
            def publication = Publication.recentPublications.get(newPublication.id)

        then:
            publication != null
            'Some New Book' == publication.title
    }

    void "Test get method returns null"() {

        given:
            def now = new Date()
            def newPublication = Publication.newInstance(
                title: "Some New Book",
                datePublished: now - 10).save(failOnError: true)
            def oldPublication = Publication.newInstance(
                title: "Some Old Book",
                datePublished: now - 900).save(flush:true, failOnError: true)

            session.clear()

        when:
            def publication = Publication.recentPublications.get(42 + oldPublication.id)

        then:
            publication == null
    }

    @PendingFeatureIf(
            value = { !(
                System.getProperty('hibernate5.gorm.suite') ||
                System.getProperty('mongodb.gorm.suite')
            )},
            reason = 'Was previously @Ignore'
    )
    void "Test count method following named criteria"() {

        given:
            def now = new Date()
            def newPublication = Publication.newInstance(
                title: "Book Some New ",
                datePublished: now - 10).save(failOnError: true)
            def oldPublication = Publication.newInstance(
                title: "Book Some Old ",
                datePublished: now - 900).save(flush:true, failOnError: true)

            session.clear()

        when:
            def publicationsWithBookInTitleCount = Publication.publicationsWithBookInTitle.count()
            def recentPublicationsCount = Publication.recentPublications.count()

        then:
            2 == publicationsWithBookInTitleCount
            1 == recentPublicationsCount
    }

    void "Test count with parameterized named query"() {

        given:
            def now = new Date()
            Publication.newInstance(title: "Book",
                                           datePublished: now - 10).save(failOnError: true)
            Publication.newInstance(title: "Book",
                                           datePublished: now - 10).save(failOnError: true)
            Publication.newInstance(title: "Book",
                                           datePublished: now - 900).save(flush:true, failOnError: true)

            session.clear()

        when:
            def recentPublicationsCount = Publication.recentPublicationsByTitle('Book').count()

        then:
            2 == recentPublicationsCount
    }

    void "Test max parameter"() {
        given:
            (1..25).each {num ->
                Publication.newInstance(title: "Book Number ${num}",
                                        datePublished: new Date()).save()
            }

        when:
            def pubs = Publication.recentPublications.list(max: 10)
        then:
            10 == pubs?.size()
    }

    void "Test max results"() {
        given:
            (1..25).each {num ->
                Publication.newInstance(title: 'Book Title',
                                        datePublished: new Date() + num).save()
            }

        when:
            def pubs = Publication.latestBooks.list()

        then:
            10 == pubs?.size()
    }

    void "Test findAllWhere method combined with named query"() {
        given:
            def now = new Date()
            (1..5).each {num ->
                3.times {
                    Publication.newInstance(title: "Book Number ${num}",
                                            datePublished: now).save(failOnError: true)
                }
            }

        when:
            def pubs = Publication.recentPublications.findAllWhere(title: 'Book Number 2')

        then:
            3 == pubs?.size()
    }

    void "Test findAllWhere method with named query and disjunction"() {

        given:
            def now = new Date()
            def oldDate = now - 2000

            Publication.newInstance(title: 'New Paperback', datePublished: now, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'New Paperback', datePublished: now, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'Old Paperback', datePublished: oldDate, paperback: true).save(failOnError: true)
            Publication.newInstance(title: 'New Hardback', datePublished: now, paperback: false).save(failOnError: true)
            Publication.newInstance(title: 'Old Hardback', datePublished: oldDate, paperback: false).save(flush:true, failOnError: true)
            session.clear()

        when:
            def publications = Publication.paperbackOrRecent.findAllWhere(title: 'Old Paperback')

        then:
            1 == publications?.size()

        when:
            publications = Publication.paperbackOrRecent.findAllWhere(title: 'Old Hardback')

        then:
            0 == publications?.size()

        when:
            publications = Publication.paperbackOrRecent.findAllWhere(title: 'New Paperback')

        then:
            2 == publications?.size()
    }

    void "Test get with parameterized named query"() {

        given:
        def now = new Date()
        def recentPub = Publication.newInstance(title: "Some Title",
                                                datePublished: now).save()
        def oldPub = Publication.newInstance(title: "Some Title",
                                             datePublished: now - 900).save()

        when:
            def pub = Publication.recentPublicationsByTitle('Some Title').get(oldPub.id)

        then:
            pub == null

        when:
            pub = Publication.recentPublicationsByTitle('Some Title').get(recentPub.id)

        then:
            recentPub.id == pub?.id
    }

    void "Test named query with one parameter"() {

        given:
            def now = new Date()
            (1..5).each {num ->
                3.times {
                    Publication.newInstance(
                        title: "Book Number ${num}",
                        datePublished: now).save(failOnError: true)
                }
            }

        when:
            def pubs = Publication.recentPublicationsByTitle('Book Number 2').list()

        then:
            3 == pubs?.size()
    }

    void "Test named query with multiple parameters"() {

        given:
            def now = new Date()
            (1..5).each {num ->
                Publication.newInstance(
                    title: "Book Number ${num}",
                    datePublished: ++now).save(failOnError: true)
            }

        when:
            def pubs = Publication.publishedBetween(now-2, now).list()

        then:
            3 == pubs?.size()
    }

    void "Test named query with multiple parameters and dynamic finder"() {
        given:
            def now = new Date()
            (1..5).each {num ->
                Publication.newInstance(
                    title: "Book Number ${num}",
                    datePublished: now + num).save(failOnError: true)
                Publication.newInstance(
                    title: "Another Book Number ${num}",
                    datePublished: now + num).save(failOnError: true)
            }

        when:
            def pubs = Publication.publishedBetween(now, now + 2).findAllByTitleLike('Book%')

        then:
            2 == pubs?.size()
    }

    void "Test named query with multiple parameters and map"() {

        given:
            def now = new Date()
            (1..10).each {num ->
                Publication.newInstance(
                    title: "Book Number ${num}",
                    datePublished: ++now).save(failOnError: true)
            }

        when:
            def pubs = Publication.publishedBetween(now-8, now-2).list(offset:2, max: 4)

        then:
            4 == pubs?.size()
    }

    void "Test findWhere with named query"() {

        given:
            def now = new Date()
            (1..5).each {num ->
                3.times {
                    Publication.newInstance(
                        title: "Book Number ${num}",
                        datePublished: now).save(failOnError: true)
                }
            }

        when:
            def pub = Publication.recentPublications.findWhere(title: 'Book Number 2')
        then:
            'Book Number 2' == pub.title
    }
}

@Entity
class PlantCategory implements Serializable {
    Long id
    Long version
    Set plants
    String name

    static hasMany = [plants:Plant]

    static namedQueries = {
//        withPlantsInPatch {
//            plants {
//                eq 'goesInPatch', true
//            }
//        }
//        withPlantsThatStartWithG {
//            plants {
//                like 'name', 'G%'
//            }
//        }
//        withPlantsInPatchThatStartWithG {
//            withPlantsInPatch()
//            withPlantsThatStartWithG()
//        }
    }
}

@Entity
class Plant implements Serializable {
    Long id
    Long version
    boolean goesInPatch
    String name

    static mapping = {
        name index:true
        goesInPatch index:true
    }
}

@Entity
class Publication implements Serializable {
    Long id
    Long version
    String title
    Date datePublished
    Boolean paperback = true

    static mapping = {
        title index:true
        paperback index:true
        datePublished index:true
    }

    static namedQueries = {

        lastPublishedBefore { date ->
            uniqueResult = true
            le 'datePublished', date
            order 'datePublished', 'desc'
        }

        recentPublications {
            def now = new Date()
            gt 'datePublished', now - 365
        }

        publicationsWithBookInTitle {
            like 'title', 'Book%'
        }

        recentPublicationsByTitle { title ->
            recentPublications()
            eq 'title', title
        }

        latestBooks {
            maxResults(10)
            order("datePublished", "desc")
        }

        publishedBetween { start, end ->
            between 'datePublished', start, end
        }

        publishedAfter { date ->
            gt 'datePublished', date
        }

        paperbackOrRecent {
            or {
                def now = new Date()
                gt 'datePublished', now - 365
                paperbacks()
            }
        }

        paperbacks {
            eq 'paperback', true
        }

        paperbackAndRecent {
            paperbacks()
            recentPublications()
        }

        thisWeeksPaperbacks() {
            paperbacks()
            def today = new Date()
            publishedBetween(today - 7, today)
        }

        queryThatNestsMultipleLevels {
            // this nested query will call other nested queries
            thisWeeksPaperbacks()
        }
    }
}
