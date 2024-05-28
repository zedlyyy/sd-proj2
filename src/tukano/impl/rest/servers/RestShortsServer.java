package tukano.impl.rest.servers;

import org.glassfish.jersey.server.ResourceConfig;
import tukano.api.java.Shorts;
import utils.Args;


public class RestShortsServer extends AbstractRestServer {
    public static final int PORT = 4567;
    private static final int DEFAULT_BLOBS_REPLICATION = 2;

    private static int blobsReplication;

    /**
     * Constructor
     */
    RestShortsServer() {
        super(RestShortsServer.class.getName(), Shorts.NAME, PORT);
    }


    @Override
    void registerRestResources(ResourceConfig config) {
        config.register(new RestShortsResource(blobsReplication));
    }

    /**
     * Main method
     *
     * @param args - arguments
     */
    public static void main(String[] args) {
        Args.use(args);
        blobsReplication = Args.valueOf("blobsReplication", DEFAULT_BLOBS_REPLICATION);

        new RestShortsServer().start();
    }

}