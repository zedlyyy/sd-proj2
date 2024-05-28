package tukano.impl.grpc.servers;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import tukano.api.java.Blobs;
import utils.Args;

public class GrpcBlobsServer extends AbstractGrpcServer {
    public static final int PORT = 15678;

    public GrpcBlobsServer(int port) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, FileNotFoundException, CertificateException, IOException {
        super(GrpcBlobsServer.class.getName(), Blobs.NAME, port, new GrpcBlobsServerStub());
    }

    public static void main(String[] args) throws Exception {
        try {
            Args.use(args);
            new GrpcBlobsServer(Args.valueOf("-port", PORT)).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
