package liquibase.harness.diff

import java.sql.Date

import groovy.transform.CompileStatic

@CompileStatic
class Posts {

    int id
    int authorId
    String title
    String description
    String content
    Date insertedDate

    Posts() {
    }

    Posts(int id, int authorId, String title, String description, String content, Date insertedDate) {
        this.id = id
        this.authorId = authorId
        this.title = title
        this.description = description
        this.content = content
        this.insertedDate = insertedDate
    }

    int getId() {
        return id
    }

    void setId(int id) {
        this.id = id
    }

    int getAuthorId() {
        return authorId
    }

    void setAuthorId(int authorId) {
        this.authorId = authorId
    }

    String getTitle() {
        return title
    }

    void setTitle(String title) {
        this.title = title
    }

    String getDescription() {
        return description
    }

    void setDescription(String description) {
        this.description = description
    }

    String getContent() {
        return content
    }

    void setContent(String content) {
        this.content = content
    }

    Date getInsertedDate() {
        return insertedDate
    }

    void setInsertedDate(Date insertedDate) {
        this.insertedDate = insertedDate
    }

}
