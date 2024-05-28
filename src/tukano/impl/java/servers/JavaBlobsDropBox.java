package tukano.impl.java.servers;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;
import org.pac4j.scribe.builder.api.DropboxApi20;
import tukano.api.java.Result;
import tukano.impl.java.dropBox.msgs.DeleteFileArgs;
import tukano.impl.java.dropBox.msgs.DownloadFileArgs;
import tukano.impl.java.dropBox.msgs.uploadFileArgs;
import utils.Args;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class JavaBlobsDropBox extends JavaBlobs {

    private static final String DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download";
    private static final String UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload";
    private static final String DELETE_V2_URL = "https://api.dropboxapi.com/2/files/delete_v2";

    private static final int HTTP_SUCCESS = 200;
    private static final String CONTENT_TYPE_HDR = "Content-Type";
    private static final String API_ARGS_HDR = "Dropbox-API-Arg";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String OCTET_CONTENT_TYPE = "application/octet-stream";

    private static JavaBlobsDropBox instance;

    private final Gson json;
    private final OAuth20Service service;
    private final OAuth2AccessToken accessToken;

    /**
     * Constructor for JavaBlobsDropBox
     *
     * @param cleanServer - if true, deletes all files in the BLOBS_ROOT_DIR
     * @param blobID      - blobID
     */
    public JavaBlobsDropBox(boolean cleanServer, String blobID) {
        super(JavaBlobsDropBox.class.getName(), blobID);
        json = new Gson();
        accessToken = new OAuth2AccessToken(Args.valueOf("-accessTokenStr", ""));
        service = new ServiceBuilder(Args.valueOf("-apiKey", ""))
                .apiSecret(Args.valueOf("-apiSecret", ""))
                .build(DropboxApi20.INSTANCE);
        if (cleanServer) {
            Log.info("\n\nCleaning server: " + blobRootDir + "\n");
            deleteAllFiles(new File(blobRootDir));
        }
    }


    @Override
    protected void saveFile(File file, byte[] bytes) {
        uploadToDropBox(file, bytes);
    }

    @Override
    protected Result<Void> checkExistsAndEquals(byte[] bytes, File file) {
        Log.info("Checking if file exists in DropBox and is equal: " + file.getPath());
        try (Response downloadResponse = downloadFromDropBox(file)) {
            if (downloadResponse.getCode() == HTTP_SUCCESS)
                return checkEqualBytes(bytes, downloadResponse.getStream().readAllBytes());
            else
                return null;
        } catch (IOException | NullPointerException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    protected Result<byte[]> readOrError(File file) {
        try (Response downloadResponse = downloadFromDropBox(file)) {
            if (downloadResponse.getCode() == HTTP_SUCCESS)
                return Result.ok(downloadResponse.getStream().readAllBytes());
            else
                return Result.error(Result.ErrorCode.NOT_FOUND);
        } catch (IOException | NullPointerException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    protected Result<Void> deleteOrError(File file) {
        try (Response deleteResponse = deleteFromDropBox(file)) {
            if (deleteResponse.getCode() == HTTP_SUCCESS)
                return Result.ok();
            else
                return Result.error(Result.ErrorCode.NOT_FOUND);
        } catch (IOException | ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    protected Result<Void> deleteAllFiles(File path) {
        return deleteOrError(path);
    }

    /**
     * Uploads a file to DropBox
     *
     * @param file  - file data to upload
     * @param bytes - file content
     */
    private void uploadToDropBox(File file, byte[] bytes) {
        try {
            Log.info("Uploading file to Dropbox: " + file.getPath());
            OAuthRequest uploadRequest = new OAuthRequest(Verb.POST, UPLOAD_URL);
            uploadRequest.addHeader(CONTENT_TYPE_HDR, OCTET_CONTENT_TYPE);
            uploadRequest.addHeader(API_ARGS_HDR, json.toJson(new uploadFileArgs(file.getPath())));
            uploadRequest.setPayload(bytes);
            service.signRequest(accessToken, uploadRequest);
            Response response = service.execute(uploadRequest);
            if (response.getCode() != HTTP_SUCCESS)
                Log.info(String.format("Failed upload to dropBox of file: %s, Status: %d, \nReason: %s\n",
                        file.getName(), response.getCode(), response.getBody()));
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Downloads a file from DropBox
     *
     * @param file - file to download
     * @return - Response from DropBox
     * @throws IOException          - if an I/O error occurs
     * @throws ExecutionException   - if the computation threw an exception
     * @throws InterruptedException - if the current thread was interrupted
     */
    private Response downloadFromDropBox(File file) throws IOException, ExecutionException, InterruptedException {
        Log.info("Downloading file from Dropbox: " + file.getPath());
        OAuthRequest downloadRequest = new OAuthRequest(Verb.POST, DOWNLOAD_URL);
        downloadRequest.addHeader(CONTENT_TYPE_HDR, OCTET_CONTENT_TYPE);
        downloadRequest.addHeader(API_ARGS_HDR, json.toJson(new DownloadFileArgs(file.getPath())));
        service.signRequest(accessToken, downloadRequest);
        Response response = service.execute(downloadRequest);
        if (response.getCode() != HTTP_SUCCESS) {
            Log.info(String.format("Failed download from dropBox of file: %s, Status: %d, \nReason: %s\n",
                    file.getName(), response.getCode(), response.getBody()));
        }
        return response;
    }

    /**
     * Deletes a file from DropBox
     *
     * @param file - file to delete
     * @return - Response from DropBox
     * @throws IOException          - if an I/O error occurs
     * @throws ExecutionException   - if the computation threw an exception
     * @throws InterruptedException - if the current thread was interrupted
     */
    private Response deleteFromDropBox(File file) throws IOException, ExecutionException, InterruptedException {
        Log.info("Deleting file from Dropbox: " + file.getPath());
        OAuthRequest deleteRequest = new OAuthRequest(Verb.POST, DELETE_V2_URL);
        deleteRequest.addHeader(CONTENT_TYPE_HDR, JSON_CONTENT_TYPE);
        deleteRequest.setPayload(json.toJson(new DeleteFileArgs(file.getPath())));
        service.signRequest(accessToken, deleteRequest);
        Response response = service.execute(deleteRequest);
        if (response.getCode() != HTTP_SUCCESS) {
            Log.info(String.format("Failed delete from dropBox of file: %s, Status: %d, \nReason: %s\n",
                    file.getName(), response.getCode(), response.getBody()));
        }
        return response;
    }

}
