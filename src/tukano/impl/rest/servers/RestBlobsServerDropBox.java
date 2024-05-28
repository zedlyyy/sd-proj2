package tukano.impl.rest.servers;

import org.glassfish.jersey.server.ResourceConfig;
import utils.Args;

public class RestBlobsServerDropBox extends RestBlobsServer {

    public static final int PORT = 5677;
    private static boolean cleanServer;
    private static String blobID;

    /**
     * Constructor
     *
     * @param port - port
     */
    public RestBlobsServerDropBox(int port) {
        super(RestBlobsServerDropBox.class.getName(), port);
    }


    @Override
    void registerRestResources(ResourceConfig config) {
        config.register(new RestBlobsResourceDropBox(cleanServer, blobID));
    }


    /**
     * Main method
     *
     * @param args - arguments
     */
    public static void main(String[] args) {
        if (args.length < 8) { // at least 8 arguments
            System.out.println("Usage: RestBlobsServerDropBox [-cleanServer <true|false>] [-blobID <blobID>] [-apiKey <apiKey>] [-apiSecret <apiSecret>] [-accessTokenStr <accessTokenStr>] OPTIONAL: [-port <port>] ");
            System.exit(1);
        }

        Args.use(args);

        cleanServer = Args.valueOf("-cleanServer", args[0].equals("true"));
        blobID = Args.valueOf("-blobID", args[1]);

        new RestBlobsServerDropBox(Args.valueOf("-port", PORT)).start();
    }


}
