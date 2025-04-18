package grails.docs.app

import demo.Speaker
import demo.SpeakerService
import demo.Talk
import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {

    SpeakerService speakerService

    def init = { servletContext ->

        Speaker jeffScottBrown = speakerService.save('Jeff Scott',
                'Brown',
                'jeffScottBrown@email.com',
                'Jeff is a co-founder of the Grails framework, and a core member of the Grails development team.',
        [new Talk(title: 'Polyglot Web Development with Grails 3', duration: 50),
        new Talk(title: 'REST With Grails 3', duration: 50),
        new Talk(title: 'Testing in Grails 3', duration: 50)])

        Speaker graemeRocher = speakerService.save('Graeme',
                'Rocher',
                'graemeRocher@email.com',
                'Graeme is a co-founder of the Grails framework and co-authored “The Definitive Guide to Grails” - Apress.',
                [new Talk(title: 'What\'s New in Grails?', duration: 50),
        new Talk(title: 'The Latest and Greatest in GORM', duration: 50)])

        Speaker paulKing = speakerService.save('Paul',
                'King',
                'paulKing@email.com',
                'Paul King has been contributing to Open Source projects for nearly 30 years and is an active committer on numerous projects, including Groovy, GPars, and Gradle. Paul speaks at international conferences, publishes in software magazines and journals, and is a co-author of Manning’s best-seller, Groovy in Action, 2nd Edition.',
        [new Talk(title: 'Groovy: The Awesome Parts', duration: 50)])

        Speaker ivanLopez = speakerService.save('Iván',
                'López',
                'ivanLopez@email.com',
                "Iván discovered Grails 7 years ago and since almost exclusively develops using Groovy. He is the creator of some Grails plugins like Postgresql-Extensions and Slug-Generator. He's also the coordinator of the Madrid Groovy User Group and the organizer of the Greach Conference.",
                [
                        new Talk(title: 'From Java to Groovy: Adventure time!', duration: 50),
                        new Talk(title: 'Dockerize your Grails!', duration: 50)
                ])

        Speaker ryanVanderwerf = speakerService.save('Ryan',
                'Vanderwerf',
                'ryanVanderwerf@email.com',
                'Ryan is a developer on the core Grails team. Formerly, he served as the Chief Systems and Software Architect and Director of Products at ReachForce and Lead Architect at www.developerprogram.com. He has helped maintain various Grails plugins and serves as co-chair of the Groovy and Grails User Group in Austin, TX.',
                [
                        new Talk(title: 'Alexa, Tell Me I\'m Groovy!', duration: 50),
                        new Talk(title: 'Getting Groovy with Google Home', duration: 50),
                        new Talk(title: 'Amazon Alexa Workshop', duration: 180)])

        Speaker colinHarrington = speakerService.save('Colin',
                'Harrington',
                'colinHarrington@email.com',
                '''Colin boasts over 7 years of Grails experience and 10+ years of experience developing web-based applications. He's an agile practitioner with a proven track record having been a key component of multiple powerful fast-paced teams
''',
                [new Talk(title: 'Performance tuning your Grails apps', duration: 50),
        new Talk(title: 'Grails and Docker', duration: 50)])

        Speaker zacharyKlein = speakerService.save('Zachary',
                'Klein',
                'colinHarrington@email.com',
                '''Zacahry has been doing Groovy & Grails development for over 6 years. Outside of the JVM and web dev space, Zachary is the author of the webpack and React profiles for Grails 3.''',
                [
                        new Talk(title: 'Grails and the Wonderful World of Javascript Frameworks', duration: 50),
                        new Talk(title: 'Using React with Grails 3', duration: 50)
                ])
    }
    def destroy = {
    }
}
