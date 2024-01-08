package code.entities;

public class Response {
    private Integer status;
    private String description;
    private String body;

    public Response(Integer status, String description, String body) {
        this.status = status;
        this.description = description;
        this.body = body;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String printResponseFormat() {
        return status + " " + description + "\n" + body;
    }
}
