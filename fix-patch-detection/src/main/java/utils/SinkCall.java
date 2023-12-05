package utils;

public class SinkCall {
    private Message message;
    private String location;
    public SinkCall(Message message,String location){
        this.message=message;
        this.location=location;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}

