package tukano.impl.api.java;

import java.util.List;

import tukano.api.java.Blobs;
import tukano.api.java.Result;

public interface ExtendedBlobs extends Blobs {

	Result<Void> delete(String blobId, String token);

	Result<Void> deleteAllBlobs(String userId, String token);

	Result<List<String>> copyAllBlobs(String token, List<String> blobIds);

}
