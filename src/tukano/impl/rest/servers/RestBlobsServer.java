package tukano.impl.rest.servers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.text.Style;

import org.glassfish.jersey.server.ResourceConfig;
import tukano.api.java.Blobs;
import utils.Args;

public class RestBlobsServer extends AbstractRestServer {

    public static final int PORT = 5678;

    /**
     * Constructor
     *
     * @param className - class name
     * @param port      - port
     */
    RestBlobsServer(String className, int port) {
        super(className, Blobs.NAME, port);
    }

    /**
     * Constructor Overload
     *
     * @param port - port
     */
    RestBlobsServer(int port) {
        this(RestBlobsServer.class.getName(), port);
    }

    @Override
    void registerRestResources(ResourceConfig config) {
        config.register(RestBlobsResource.class);
    }

    /**
     * Main method
     *
     * @param args - arguments
     */
    public static void main(String[] args) {
        
        Args.use(args);
       
        new RestBlobsServer(Args.valueOf("-port", PORT)).start();

    }
}