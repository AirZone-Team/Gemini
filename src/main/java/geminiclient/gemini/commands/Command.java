package geminiclient.gemini.commands;

public class Command {
    public String getCommand() {
        return command;
    }

    private final String command;
    public Command(String command) {
        this.command = command;
    }
    public void onCommand(String message) {}
}
