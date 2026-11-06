package io.github.akarp0508.koifolio.audit;

/** Who made a change. @param name the agent's name, empty for the user */
public record Actor(Type type, String name) {

    public enum Type {
        USER,
        AGENT
    }

    public static final Actor USER = new Actor(Type.USER, null);

    public static Actor agent(String name) {
        return new Actor(Type.AGENT, name);
    }

    public boolean isAgent() {
        return type == Type.AGENT;
    }
}
