package tukano.impl.java.servers;

import static java.lang.String.format;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.errorOrResult;
import static tukano.api.java.Result.errorOrValue;
import static tukano.api.java.Result.errorOrVoid;
import static tukano.api.java.Result.ok;
import static tukano.api.java.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.java.Result.ErrorCode.FORBIDDEN;
import static tukano.api.java.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.java.Result.ErrorCode.TIMEOUT;
import static tukano.impl.java.clients.Clients.BlobsClients;
import static tukano.impl.java.clients.Clients.UsersClients;
import static utils.DB.getOne;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import tukano.api.Short;
import tukano.api.User;
import tukano.api.java.Blobs;
import tukano.api.java.Result;
import tukano.impl.api.java.ExtendedShorts;
import tukano.impl.discovery.Discovery;
import tukano.impl.java.servers.data.Following;
import tukano.impl.java.servers.data.Likes;
import tukano.impl.java.servers.data.Short_Blob;
import utils.DB;
import utils.Token;

public class JavaShorts implements ExtendedShorts, Consumer<String> {
    private static final String BLOB_COUNT = "*";
    private static final String URI_DELIMITER = "|";

    private static final Logger Log = Logger.getLogger(JavaShorts.class.getName());

    AtomicLong counter = new AtomicLong(totalShortsInDatabase());

    private static final long USER_CACHE_EXPIRATION = 3000;
    private static final long SHORTS_CACHE_EXPIRATION = 3000;
    private static final long BLOBS_USAGE_CACHE_EXPIRATION = 10000;

    private static final int DEFAULT_BLOBS_REPLICATION = 2;
    private static final long RETRY_REPLICATION_INTERVAL = 5000;

    private final int blobsReplication;

    /**
     * Constructor
     *
     * @param blobsReplication - number of replicas of blobs
     */
    public JavaShorts(int blobsReplication) {
        this.blobsReplication = blobsReplication;
        Discovery.getInstance().subscribe(Blobs.NAME, this);
    }

    public JavaShorts() {
        this(DEFAULT_BLOBS_REPLICATION);
    }

    ///////////////////////////////////////////////////////
    ///////////////////// - RECORDS - /////////////////////

    /**
     * User Credentials class
     */
    record Credentials(String userId, String pwd) {
        static Credentials from(String userId, String pwd) {
            return new Credentials(userId, pwd);
        }
    }

    /**
     * BlobServerCount class
     *
     * @param baseURI - base URI
     * @param count   - count
     */
    record BlobServerCount(String baseURI, Long count) {
    }

    ///////////////////////////////////////////////////////
    ///////////////////// - CACHES - //////////////////////

    /**
     * Cache for users
     */
    //@formatter:off
    protected final LoadingCache<Credentials, Result<User>> usersCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(USER_CACHE_EXPIRATION))
            .removalListener((e) -> {})
            .build(new CacheLoader<>() {
                @Override
                public Result<User> load(@SuppressWarnings("null") Credentials u) {
                    var res = UsersClients.get().getUser(u.userId(), u.pwd());
                    if (res.error() == TIMEOUT)
                        return error(BAD_REQUEST);
                    return res;
                }
            });
    //@formatter:on

    /**
     * Cache for shorts
     */
    //@formatter:off
    protected final LoadingCache<String, Result<Short>> shortsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(SHORTS_CACHE_EXPIRATION))
            .removalListener((e) -> { })
            .build(new CacheLoader<>() {
                @Override
                public Result<Short> load(String shortId) {
                    var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
                    var likes = DB.sql(query, Long.class);
                    return errorOrValue(getOne(shortId, Short.class), shrt -> shrt.copyWith(likes.get(0)));
                }
            });
    //@formatter:on

    /**
     * Cache for blob count
     */
    //@formatter:off
    protected final LoadingCache<String, Map<String, Long>> blobCountCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(Duration.ofMillis(BLOBS_USAGE_CACHE_EXPIRATION))
            .removalListener((e) -> {})
            .build(new CacheLoader<>() {
                @Override
                public Map<String, Long> load(String __) {
                    final var QUERY = "SELECT REGEXP_SUBSTRING(s.blobUrl, '^(\\w+:\\/\\/)?([^\\/]+)\\/([^\\/]+)') AS baseURI, count('*') AS usage From Short s GROUP BY baseURI";
                    var hits = DB.sql(QUERY, BlobServerCount.class);
                    var candidates = hits.stream().collect(Collectors.toMap(BlobServerCount::baseURI, BlobServerCount::count));
                    for (var uri : BlobsClients.all())
                        candidates.putIfAbsent(uri.toString(), 0L);
                    return candidates;
                }
            });
    //@formatter:on

    //////////////////////////////////////////////////////////////
    ///////////////////// - PUBLIC METHODS - /////////////////////

    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("\n\ncreateShort : userId = %s, pwd = %s\n", userId, password));

        return errorOrResult(okUser(userId, password), user -> {
            var shortId = format("%s-%d", userId, counter.incrementAndGet());
            String blobURL = getBlobServersSortedByLeastLoad().stream()
                    .limit(blobsReplication)
                    .map(uri -> {
                        updateBlobShortsCount(uri, 1L);
                        DB.insertOne(new Short_Blob(shortId, uri));
                        return format("%s/%s/%s", uri, Blobs.NAME, shortId);
                    })
                    .collect(Collectors.joining(URI_DELIMITER));

            Log.info("createShort: \n\tBlob URL: " + blobURL + " \n\tshortID:" + shortId + "\n\n");
            return DB.insertOne(new Short(shortId, userId, blobURL));
        });
    }

    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("\n\ngetShort : shortId = %s\n", shortId));

        if (shortId == null)
            return error(BAD_REQUEST);

        Result<Short> sh = shortFromCache(shortId);
        Log.info("getShort: " + sh + "\n");

        return sh;
    }

    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(() -> format("\n\ndeleteShort : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {

            return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
                return DB.transaction(hibernate -> {

                    shortsCache.invalidate(shortId);
                    hibernate.remove(shrt);

                    var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
                    hibernate.createNativeQuery(query, Likes.class).list().forEach(hibernate::remove);

                    BlobsClients.get().delete(shrt.getBlobUrl(), Token.get());
                });
            });
        });
    }

    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("\n\ngetShorts : userId = %s\n", userId));

        var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
        return errorOrValue(okUser(userId), DB.sql(query, String.class));
    }

    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("\n\nfollow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
                isFollowing, password));

        return errorOrResult(okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
        });
    }

    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("\n\nfollowers : userId = %s, pwd = %s\n", userId, password));

        var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
        return errorOrValue(okUser(userId, password), DB.sql(query, String.class));
    }

    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("\n\nlike : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId,
                isLiked, password));

        return errorOrResult(getShort(shortId), shrt -> {
            shortsCache.invalidate(shortId);

            var l = new Likes(userId, shortId, shrt.getOwnerId());
            return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
        });
    }

    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("\n\nlikes : shortId = %s, pwd = %s\n", shortId, password));

        return errorOrResult(getShort(shortId), shrt -> {

            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

            return errorOrValue(okUser(shrt.getOwnerId(), password), DB.sql(query, String.class));
        });
    }

    @Override
    public Result<List<String>> getFeed(String userId, String password) {
        Log.info(() -> format("\n\ngetFeed : userId = %s, pwd = %s\n", userId, password));

        final var QUERY_FMT = """
                SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'
                UNION
                SELECT s.shortId, s.timestamp FROM Short s, Following f
                	WHERE
                		f.followee = s.ownerId AND f.follower = '%s'
                ORDER BY s.timestamp DESC""";

        return errorOrValue(okUser(userId, password), DB.sql(format(QUERY_FMT, userId, userId), String.class));
    }

    // Extended API

    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("\n\ndeleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password,
                token));

        if (!Token.matches(token))
            return error(FORBIDDEN);

        return DB.transaction((hibernate) -> {

            usersCache.invalidate(new Credentials(userId, password));

            // delete shorts
            var query1 = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
            hibernate.createNativeQuery(query1, Short.class).list().forEach(s -> {
                shortsCache.invalidate(s.getShortId());
                hibernate.remove(s);
            });

            // delete follows
            var query2 = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId,
                    userId);
            hibernate.createNativeQuery(query2, Following.class).list().forEach(hibernate::remove);

            // delete likes
            var query3 = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
            hibernate.createNativeQuery(query3, Likes.class).list().forEach(l -> {
                shortsCache.invalidate(l.getShortId());
                hibernate.remove(l);
            });
        });
    }

    // Replicate dead blob server
    @Override
    public void accept(String oldBlobUrl) {
        Log.info("\n\n\nReplicating dead blob server: " + oldBlobUrl + "\n");
        try {
            // remove blob from cache
            blobCountCache.get(BLOB_COUNT).remove(oldBlobUrl);

            // update DB data
            Map<String, List<String>> blobsCopyLists = new HashMap<>(); // blobURL_TO_copy -> List of blobsUris to copy
                                                                        // from
            List<String> shtsIDs_toCopy = DB.sql(
                    format("SELECT shortId FROM Short_Blob WHERE blobUrl = '%s'", oldBlobUrl),
                    String.class);
            shtsIDs_toCopy.forEach(shtId -> updateShortUrl(oldBlobUrl, blobsCopyLists, shtId));

            // update blob shorts count and cache
            blobsCopyLists.forEach((blobUrl, listToCopy) -> updateBlobShortsCount(blobUrl, listToCopy.size()));

            // order blob replication
            orderBlobReplication(blobsCopyLists);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    ///////////////////////////////////////////////////////////////
    ///////////////////// - PRIVATE METHODS - /////////////////////

    /**
     * Update DB data
     *
     * @param oldBlobUrl     - old blob URL
     * @param blobsCopyLists - blobs copy map < blobURL_TO_copy -> List of blobsUris
     *                       to copy from >
     * @param shtId          - short id
     */
    private void updateShortUrl(String oldBlobUrl, Map<String, List<String>> blobsCopyLists, String shtId) {
        // get short, its blobURLs and remove oldBlobUrl from it
        Short sht = DB.getOne(shtId, Short.class).value();
        var sht_uris = List.of(sht.getBlobUrl().split("\\" + URI_DELIMITER)).stream()
                .filter(u -> !u.contains(oldBlobUrl)).collect(Collectors.toList());
        // find new blobURL to copy to and add a source blobURL to its blobsCopyLists
        Optional<String> blob_copy_TO = getBlobServersSortedByLeastLoad().stream()
                .filter(u -> sht_uris.stream().noneMatch(s -> s.contains(u)))
                .findFirst();
        if (blob_copy_TO.isEmpty()) {
            Log.info("Replicate blob failure - no more free blob servers to replicate\n");
            return;
        }

        String blobURL_copy_TO = blob_copy_TO.get();
        blobsCopyLists.computeIfAbsent(blobURL_copy_TO, k -> new ArrayList<>()).add(sht_uris.get(0));

        // update short url
        sht_uris.add(format("%s/%s/%s", blobURL_copy_TO, Blobs.NAME, shtId));
        sht.setBlobUrl(sht_uris.stream().collect(Collectors.joining(URI_DELIMITER)));

        // update DB
        DB.deleteOne(new Short_Blob(shtId, oldBlobUrl));
        DB.insertOne(new Short_Blob(shtId, blobURL_copy_TO));
        DB.updateOne(sht);
    }

    /**
     * Order blob replication
     *
     * @param blobsCopyLists - blobs copy map < blobURL_TO_copy -> List of blobsUris
     *                       to copy from >
     */
    private void orderBlobReplication(Map<String, List<String>> blobsCopyLists) {
        // for each blob where to copy shorts, order replication until success
        blobsCopyLists.forEach((blobURL_TO_copy, copy_FROM_List) -> {
            Result<List<String>> res = BlobsClients.get(URI.create(blobURL_TO_copy)).copyAllBlobs(Token.get(),
                    copy_FROM_List);
            if (res.isOK()) {
                if (res.value().isEmpty())
                    Log.info("copyAllBlobs - success - all blobs replicated\n");
                else {
                    Log.info("copyAllBlobs - failure - some blobs not replicated: " + res.value() + "\nTrying again\n");
                    orderBlobReplication(Map.of(blobURL_TO_copy, res.value()));
                }
            } else {
                Log.info("copyAllBlobs - failure - error: " + res.error() + "Trying again\n");
                orderBlobReplication(Map.of(blobURL_TO_copy, copy_FROM_List));
            }
        });
    }

    /**
     * Update blob shorts count
     *
     * @param blobsURL - blob URL
     * @param inc      - increment
     */
    private void updateBlobShortsCount(String blobsURL, long inc) {
        try {
            blobCountCache.get(BLOB_COUNT).compute(blobsURL, (k, v) -> v + inc);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get user from cache
     *
     * @param userId - user id
     * @return - user
     */
    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return ok();
        else
            return error(res.error());
    }

    /**
     * Get user from cache
     *
     * @param userId - user id
     * @param pwd    - password
     * @return - user
     */
    protected Result<User> okUser(String userId, String pwd) {
        try {
            return usersCache.get(new Credentials(userId, pwd));
        } catch (Exception x) {
            x.printStackTrace();
            return Result.error(INTERNAL_ERROR);
        }
    }

    /**
     * Get short from cache
     *
     * @param shortId - short id
     * @return - short
     */
    protected Result<Short> shortFromCache(String shortId) {
        try {
            return shortsCache.get(shortId);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    /**
     * Get the least loaded blob servers URIs
     *
     * @return - a blobsReplication number of least loaded blob servers URIs
     */
    private List<String> getBlobServersSortedByLeastLoad() {
        try {
            var servers = blobCountCache.get(BLOB_COUNT);

            return servers.entrySet()
                    .stream()
                    .sorted((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } catch (Exception x) {
            x.printStackTrace();
        }
        return null;
    }

    /**
     * Get the total number of shorts in the database
     *
     * @return - total number of shorts in the database
     */
    private long totalShortsInDatabase() {
        var hits = DB.sql("SELECT count('*') FROM Short", Long.class);
        return 1L + (hits.isEmpty() ? 0L : hits.get(0));
    }

}
