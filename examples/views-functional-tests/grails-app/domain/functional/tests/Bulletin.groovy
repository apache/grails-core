package functional.tests

class Bulletin {

    String name
    String content

    static hasMany = [
        targetUsers : User,
        contactUsers: User
    ]

    static mapping = {
        targetUsers joinTable: [name: 'bulletin_target_users',
                                column: 'target_user_id',
                                key: 'bulletin_id']
        contactUsers joinTable: [name: 'bulletin_contact_users',
                                 column: 'contact_user_id',
                                 key: 'bulletin_id']
    }
}
