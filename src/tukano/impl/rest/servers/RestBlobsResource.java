package tukano.impl.rest.servers;

import java.util.List;

import jakarta.inject.Singleton;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.rest.RestExtendedBlobs;
import tukano.impl.java.servers.JavaBlobs;
import tukano.impl.java.servers.JavaBlobsDropBox;

@Singleton
public class RestBlobsResource extends RestResource implements RestExtendedBlobs {

    /**
     * Implementation of Blobs API
     */
    ExtendedBlobs impl;

    /**
     * Constructor JavaBlobs
     */
    public RestBlobsResource() {
        impl = new JavaBlobs();
    }

    public RestBlobsResource(boolean cleanServer, String blobID) {
        impl = new JavaBlobsDropBox(cleanServer, blobID);
    }

    @Override
    public void upload(String blobId, byte[] bytes) {
        super.resultOrThrow(impl.upload(blobId, bytes));
    }

    @Override
    public byte[] download(String blobId) {
        return super.resultOrThrow(impl.download(blobId));
    }

    @Override
    public void delete(String blobId, String token) {
        super.resultOrThrow(impl.delete(blobId, token));
    }

    @Override
    public void deleteAllBlobs(String userId, String password) {
        super.resultOrThrow(impl.deleteAllBlobs(userId, password));
    }

    @Override
    public List<String> copyAllBlobs(String token, List<String> blobIds) {
        return super.resultOrThrow(impl.copyAllBlobs(token, blobIds));
    }
}
