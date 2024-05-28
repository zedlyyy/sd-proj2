package tukano.impl.api.rest;

import java.util.List;


import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import tukano.api.rest.RestBlobs;

@Path(RestBlobs.PATH)
public interface RestExtendedBlobs extends RestBlobs {

	String TOKEN = "token";
	String BLOBS = "blobs";
	String USER_ID = "userId";

	@DELETE
	@Path("/{" + BLOB_ID + "}")
	void delete(@PathParam(BLOB_ID) String blobId, @QueryParam(TOKEN) String token);

	@DELETE
	@Path("/{" + USER_ID + "}/" + BLOBS)
	void deleteAllBlobs(@PathParam(USER_ID) String userId, @QueryParam(TOKEN) String token);

	@POST
	@Path("/copy")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	List<String> copyAllBlobs(@QueryParam(TOKEN) String token, List<String> blobIds);

}
