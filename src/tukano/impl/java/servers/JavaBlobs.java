package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.java.Result.ErrorCode.CONFLICT;
import static tukano.api.java.Result.ErrorCode.FORBIDDEN;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.NOT_FOUND;
import static tukano.impl.java.clients.Clients.BlobsClients;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import tukano.api.java.Blobs;
import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.java.clients.Clients;
import utils.Hash;
import utils.Hex;
import utils.IO;
import utils.Token;

public class JavaBlobs implements ExtendedBlobs {

    protected static final int CHUNK_SIZE = 4096;
    protected static final String ALL_BLOBS_ROOT_DIR = "/tmp/blobs/";
    protected static final String DEFAULT_LOCAL_BLOB_ID = "local";

    protected Logger Log;
    protected String blobRootDir;

    /**
     * Constructor
     *
     * @param blobID    - blobID
     * @param className - class name
     */
    protected JavaBlobs(String className, String blobID) {
        this.Log = Logger.getLogger(className);
        this.blobRootDir = ALL_BLOBS_ROOT_DIR + blobID + "/";
    }

    /**
     * Constructor Overload
     */
    public JavaBlobs() {
        this(JavaBlobs.class.getName(), DEFAULT_LOCAL_BLOB_ID);
    }

    @Override
    public Result<Void> upload(String blobId, byte[] bytes) {
        Log.info(() -> format("\n\nupload : blobId = %s, sha256 = %s\n", blobId, Hex.of(Hash.sha256(bytes))));
        if (!validBlobId(blobId)) {
            Log.info("Invalid blobId\n");
            return error(FORBIDDEN);
        }
        File file = toFilePath(blobId);
        if (file == null) {
            Log.info("Invalid file\n");
            return error(BAD_REQUEST);
        }
        Result<Void> res = checkExistsAndEquals(bytes, file);
        if (res != null) {
            Log.info("File already exists." + res.isOK() + "\n");
            return res;
        }
        saveFile(file, bytes);
        Log.info("File saved.\n");
        return ok();
    }

    @Override
    public Result<byte[]> download(String blobId) {
        Log.info(() -> format("\n\ndownload : blobId = %s\n", blobId));
        File file = toFilePath(blobId);
        if (file == null)
            return error(BAD_REQUEST);
        return readOrError(file);
    }

    @Override
    public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink) {
        Log.info(() -> format("\n\ndownloadToSink : blobId = %s\n", blobId));
        File file = toFilePath(blobId);
        if (file == null)
            return error(BAD_REQUEST);
        if (!file.exists())
            return error(NOT_FOUND);
        try (FileInputStream fis = new FileInputStream(file)) {
            int n;
            var chunk = new byte[CHUNK_SIZE];
            while ((n = fis.read(chunk)) > 0)
                sink.accept(Arrays.copyOf(chunk, n));
            return ok();
        } catch (IOException x) {
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(() -> format("\n\ndelete : blobId = %s, token=%s\n", blobId, token));
        if (!Token.matches(token)) {
            Log.info("Token does not match: \n\tthis Token == " + Token.get() + "\n\tthat token == " + token + "\n");
            return error(FORBIDDEN);
        }
        File file = toFilePath(blobId);
        if (file == null) {
            Log.info(blobId + "  Invalid file\n");
            return error(BAD_REQUEST);
        }
        return deleteOrError(file);
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String token) {
        Log.info(format("\n\ndeleteAllBlobs : userId = %s, token=%s\n", userId, token));
        if (!Token.matches(token))
            return error(FORBIDDEN);
        File path = new File(blobRootDir + userId);
        return deleteAllFiles(path);
    }

    @Override
    public Result<List<String>> copyAllBlobs(String token, List<String> blobUris) {
        Log.info("\n\n\n\ncopyAllBlobs: " + blobUris + "\n");
        if (!Token.matches(token))
            return error(FORBIDDEN);

        List<String> failedBlobsUris = new ArrayList<>();
        for (String blobUri : blobUris) {
            String[] parts = blobUri.split(format("/%s/", Blobs.NAME));
            var res = BlobsClients.get(URI.create(parts[0])).download(parts[1]);
            if (!res.isOK()) {
                Log.info("Error downloading blobId: " + blobUri + " : " + res.error() + "\n");
                failedBlobsUris.add(blobUri);
            } else {
                Log.info("Downloaded blobUri: " + blobUri + "\n");
                this.upload(parts[1], res.value());
            }
        }
        return ok(failedBlobsUris);
    }

    protected Result<Void> checkExistsAndEquals(byte[] bytes, File file) {
        Log.info("\nChecking if file exists in Local Server and is equal: " + file.getPath());
        if (file.exists())
            return checkEqualBytes(bytes, IO.read(file));
        return null;
    }

    protected static Result<Void> checkEqualBytes(byte[] bytes1, byte[] bytes2) {
        if (Arrays.equals(Hash.sha256(bytes1), Hash.sha256(bytes2)))
            return ok();
        else
            return error(CONFLICT);
    }

    protected void saveFile(File file, byte[] bytes) {
        IO.write(file, bytes);
    }

    protected Result<byte[]> readOrError(File file) {
        if (file.exists())
            return ok(IO.read(file));
        else
            return error(NOT_FOUND);
    }

    protected Result<Void> deleteOrError(File file) {
        if (!file.exists()) {
            System.out.println("\n\nFile does not exist: " + file.getPath());
            Log.info("\n\nFile does not exist: " + file.getPath());
            return error(NOT_FOUND);
        }
        IO.delete(file);
        return ok();
    }

    protected Result<Void> deleteAllFiles(File path) {
        try {
            Files.walk(path.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            return ok();
        } catch (IOException e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    private boolean validBlobId(String blobId) {
        Log.info("validBlobId - blobId = " + blobId + "\n");
        // how to create the full blobId before sending it to the server?
        // blobs1-ourorg_5677/rest
        return Clients.ShortsClients.get().getShort(blobId).isOK();
    }

    private File toFilePath(String blobId) {
        String[] parts = blobId.split("-");
        if (parts.length != 2)
            return null;
        File res = new File(blobRootDir + parts[0] + "/" + parts[1]);
        res.getParentFile().mkdirs();
        return res;
    }

}
