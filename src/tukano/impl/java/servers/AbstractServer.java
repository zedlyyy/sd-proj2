package tukano.impl.java.servers;

import java.io.IOException;
import java.util.logging.Logger;

import utils.Args;
import utils.IP;
import utils.Token;


public abstract class AbstractServer {
    protected static final String INETADDR_ANY = "0.0.0.0";

    final protected Logger Log;
    final protected String serverURI;
    final protected String service;

    protected AbstractServer(String className, String service, String serverURI) {
        this.Log = Logger.getLogger(className);
        this.service = service;
        this.serverURI = serverURI;

        Token.set(Args.valueOf("-token", ""));
        Log.info("\n\nAbstractServer token: " + Args.valueOf("-token", ""));
    }

    abstract protected void start() throws IOException, Exception;

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }
}
