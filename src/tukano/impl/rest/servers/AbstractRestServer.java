package tukano.impl.rest.servers;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import tukano.impl.discovery.Discovery;
import tukano.impl.java.servers.AbstractServer;
import tukano.impl.rest.servers.utils.CustomLoggingFilter;
import tukano.impl.rest.servers.utils.GenericExceptionMapper;
import utils.IP;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.NoSuchAlgorithmException;


public abstract class AbstractRestServer extends AbstractServer {
    private static final String SERVER_BASE_URI = "https://%s:%s%s";
    private static final String REST_CTX = "/rest";

    protected AbstractRestServer(String log, String service, int port) {
        super(log, service, String.format(SERVER_BASE_URI, IP.hostName(), port, REST_CTX));
    }

    protected void start() {

        ResourceConfig config = new ResourceConfig();

        registerResources(config);

        try {
            JdkHttpServerFactory.createHttpServer(URI.create(serverURI.replace(IP.hostAddress(), INETADDR_ANY)), config, SSLContext.getDefault());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        Log.info("Server Announcing - service: " + service + "  uri: " + super.serverURI);
        Discovery.getInstance().announce(service, super.serverURI);

        Log.info(String.format("%s Server ready @ %s\n", service, serverURI));
    }

    protected void registerResources(ResourceConfig config) {
        registerRestResources(config);
        config.register(new GenericExceptionMapper());
        config.register(new CustomLoggingFilter());
    }

    abstract void registerRestResources(ResourceConfig config);

}
