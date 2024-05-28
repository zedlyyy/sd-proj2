package tukano.impl.discovery;

import utils.Sleep;

import java.net.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * <p>
 * A class interface to perform service discovery based on periodic
 * announcements over multicast communication.
 * </p>
 */

public interface Discovery {

    /**
     * Used to announce the URI of the given service name.
     *
     * @param serviceName - the name of the service
     * @param serviceURI  - the uri of the service
     */
    void announce(String serviceName, String serviceURI);

    /**
     * Get discovered URIs for a given service name
     *
     * @param serviceName - name of the service
     * @param minReplies  - minimum number of requested URIs. Blocks until the
     *                    number is satisfied.
     * @return array with the discovered URIs for the given service name.
     */
    URI[] knownUrisOf(String serviceName, int minReplies);

    /**
     * Remove a blob server from the list of known servers
     *
     * @param uri - the URI of the blob server to remove
     */
    void removeServer(String serviceName, String uri);

    /**
     * Subscribe to a service
     *
     * @param serviceName - the name of the service
     * @param consumer    - the consumer to be called when a URI is no longer available
     */
    void subscribe(String serviceName, Consumer<String> consumer);

    /**
     * Get the instance of the Discovery service
     *
     * @return the singleton instance of the Discovery service
     */
    static Discovery getInstance() {
        return DiscoveryImpl.getInstance();
    }

}

/**
 * Implementation of the multicast discovery service
 */
class DiscoveryImpl implements Discovery {

    private static final Logger Log = Logger.getLogger(Discovery.class.getName());

    static final int DISCOVERY_ANNOUNCE_INTERVAL_IN_MILIS = 1000;
    private static final int CHECK_URI_ALIVE_INTERVAL_IN_MILIS = 2000;
    private static final long DEAD_URI_TIMEOUT_IN_MILIS = 15000;

    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

    // Used separate the two fields that make up a service announcement.
    private static final String DELIMITER = "\t";

    private static final int MAX_DATAGRAM_SIZE = 65536;

    private static Discovery singleton;

    /**
     * Map of service names to a Map of URIs (as strings) to timestamps as longs
     */
    private Map<String, Map<String, Long>> uris = new ConcurrentHashMap<>();

    /**
     * Map of service names to a set of subscribers
     */
    private Map<String, Set<Consumer<String>>> subscribers = new ConcurrentHashMap<>();

    /**
     * Get the singleton instance of the Discovery service
     *
     * @return - the singleton instance
     */
    synchronized static Discovery getInstance() {
        if (singleton == null) {
            singleton = new DiscoveryImpl();
        }
        return singleton;
    }

    /**
     * Constructor
     */
    private DiscoveryImpl() {
        this.startListener();
        new Thread(this::checkUriIsAlive).start();
    }

    @Override
    public void announce(String serviceName, String serviceURI) {
        Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s\n", DISCOVERY_ADDR, serviceName,
                serviceURI));

        var pktBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
        var pkt = new DatagramPacket(pktBytes, pktBytes.length, DISCOVERY_ADDR);

        // start thread to send periodic announcements
        new Thread(() -> {
            try (var ds = new DatagramSocket()) {
                while (true) {
                    try {
                        ds.send(pkt);
                        Sleep.ms(DISCOVERY_ANNOUNCE_INTERVAL_IN_MILIS);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public URI[] knownUrisOf(String serviceName, int minEntries) {
        while (true) {
            var res = uris.getOrDefault(serviceName, Collections.emptyMap());
            if (res.size() >= minEntries)
                return res.keySet().stream()
                        .map(URI::create)
                        .toArray(URI[]::new);
            else
                Sleep.ms(DISCOVERY_ANNOUNCE_INTERVAL_IN_MILIS);

        }
    }

    @Override
    public void removeServer(String serviceName, String uri) {
        Map<String, Long> serviceUris = uris.get(serviceName);
        if (serviceUris != null)
            serviceUris.remove(uri);
    }

    @Override
    public void subscribe(String serviceName, Consumer<String> consumer) {
        subscribers.computeIfAbsent(serviceName, k -> ConcurrentHashMap.newKeySet()).add(consumer);
    }

    /**
     * Start the listener for the multicast group
     */
    private void startListener() {
        Log.info(String.format("Starting discovery on multicast group: %s, port: %d\n", DISCOVERY_ADDR.getAddress(),
                DISCOVERY_ADDR.getPort()));

        new Thread(() -> {
            try (var ms = new MulticastSocket(DISCOVERY_ADDR.getPort())) {
                ms.joinGroup(DISCOVERY_ADDR, NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
                for (; ; ) {
                    try {
                        var pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
                        ms.receive(pkt);
                        var msg = new String(pkt.getData(), 0, pkt.getLength());
                        Log.finest(String.format("Received: %s", msg));

                        var parts = msg.split(DELIMITER);
                        if (parts.length == 2) {
                            String serviceName = parts[0];
                            String uri = parts[1];
                            uris.computeIfAbsent(serviceName, (k) -> new ConcurrentHashMap<>())
                                    .put(uri, System.currentTimeMillis());
                        }

                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
        }).start();
    }

    /**
     * Check if the URIs are still alive, and if not, notify the subscribers
     */
    private void checkUriIsAlive() {
        while (true) {
            try {
                for (var svc : uris.entrySet()) {
                    String serviceName = svc.getKey();
                    var uris = svc.getValue();
                    for (var uriEntry : uris.entrySet()) {
                        String uri = uriEntry.getKey();
                        Long timestamp = uriEntry.getValue();
                        if (System.currentTimeMillis() - timestamp > DEAD_URI_TIMEOUT_IN_MILIS) {
                            uris.remove(uri);
                            notifySubscribers(serviceName, uri);
                        }
                    }
                }
                Sleep.ms(CHECK_URI_ALIVE_INTERVAL_IN_MILIS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Notify the subscribers of a service that the URI is no longer available
     *
     * @param serviceName - the name of the service
     * @param uri         - the URI that is no longer available
     */
    private void notifySubscribers(String serviceName, String uri) {
        Set<Consumer<String>> serviceSubscribers = subscribers.get(serviceName);
        if (serviceSubscribers != null)
            serviceSubscribers.forEach(c -> c.accept(uri));
    }

}