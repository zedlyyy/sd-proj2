package tukano.impl.grpc.servers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import tukano.api.java.Shorts;
import utils.Args;

public class GrpcShortsServer extends AbstractGrpcServer {
    public static final int PORT = 14567;


    public GrpcShortsServer() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, FileNotFoundException, CertificateException, IOException {
        super(GrpcShortsServer.class.getName(), Shorts.NAME, PORT, new GrpcShortsServerStub());
    }

    public static void main(String[] args) throws Exception {
        try {
            Args.use(args);
            new GrpcShortsServer().start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
