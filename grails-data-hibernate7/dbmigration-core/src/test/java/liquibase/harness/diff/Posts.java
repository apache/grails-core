package liquibase.harness.diff;

import java.sql.Date;

public class Posts {
    int id;
    int authorId; //TODO this might not be needed depending on mapping strategy
    String title;
    String description;
    String content;
    Date insertedDate;

    public Posts() {
    }

    public Posts(int id, int authorId, String title, String description, String content, Date insertedDate) {
        this.id = id;
        this.authorId = authorId;
        this.title = title;
        this.description = description;
        this.content = content;
        this.insertedDate = insertedDate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAuthorId() {
        return authorId;
    }

    public void setAuthorId(int authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getInsertedDate() {
        return insertedDate;
    }

    public void setInsertedDate(Date insertedDate) {
        this.insertedDate = insertedDate;
    }
}
