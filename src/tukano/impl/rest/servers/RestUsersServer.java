package tukano.impl.rest.servers;

import org.glassfish.jersey.server.ResourceConfig;
import tukano.api.java.Users;
import utils.Args;


public class RestUsersServer extends AbstractRestServer {
    public static final int PORT = 3456;


    RestUsersServer() {
        super(RestUsersServer.class.getName(), Users.NAME, PORT);
    }


    public static void main(String[] args) {
        Args.use(args);
        new RestUsersServer().start();
    }

    @Override
    void registerRestResources(ResourceConfig config) {
        config.register(RestUsersResource.class);
        //gajo no discord:
//		De que forma se consegue passar o booleano correspondente ao estado do serviço externo,  como um parâmetro para o construtor dos Resources? Encontrámos um método ResourceConfig.register para um objeto de um componente JAX-RS invés do register anterior que recebia uma Resource.class, mas não sabemos se é o método correto. Segue-se um exemplo:

//		ResourceConfig config = new ResourceConfig();
//		config.register(new RestUsersResource(stateBoolean));
    }



}