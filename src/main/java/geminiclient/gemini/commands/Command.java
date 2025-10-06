package geminiclient.gemini.commands;

public class Command {
    public String prefix;
    public int args;

    public Command(String prefix,int args) {
        this.prefix = prefix;
        this.args = args;
    }

    public void onCommand(String message) {}
}
