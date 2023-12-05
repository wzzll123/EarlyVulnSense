package utils;

import com.google.gson.annotations.SerializedName;

public class SinkModel {
    private Message message;
    private String level;
    private Descriptor descriptor;
    private Properties properties;

    public Message getMessage() {
        return message;
    }

    public String getLevel() {
        return level;
    }

    public Descriptor getDescriptor() {
        return descriptor;
    }

    public Properties getProperties() {
        return properties;
    }

    public static class Message {
        private String text;

        public String getText() {
            return text;
        }
    }

    public static class Descriptor {
        private String id;
        private int index;

        public String getId() {
            return id;
        }

        public int getIndex() {
            return index;
        }
    }

    public static class Properties {
        @SerializedName("formattedMessage")
        private FormattedMessage formattedMessage;

        public FormattedMessage getFormattedMessage() {
            return formattedMessage;
        }
    }

    public static class FormattedMessage {
        private String text;

        public String getText() {
            return text;
        }
    }
}
