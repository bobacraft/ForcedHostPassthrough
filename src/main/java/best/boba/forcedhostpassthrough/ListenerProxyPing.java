package best.boba.forcedhostpassthrough;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ListenerProxyPing {
    final Config config;
    public ListenerProxyPing(Config config) {
        this.config = config;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onProxyPing(ProxyPingEvent event) {
        Logger logger = this.config.logger();

        InboundConnection connection = event.getConnection();
        Optional<InetSocketAddress> optionalVirtualHost = connection.getVirtualHost();
        if (optionalVirtualHost.isEmpty()) {
            logger.info("Virtual host was empty");
            return;
        }

        InetSocketAddress virtualHost = optionalVirtualHost.get();
        String hostString = virtualHost.getHostString();

        Map<String, List<String>> forcedHosts = this.config.server().getConfiguration().getForcedHosts();
        List<String> tryBackends = forcedHosts.get(hostString);
        if (tryBackends == null) {
            logger.info("No forced hosts found for: " + hostString);
            return;
        }

        logger.info("Trying the following hosts for ping passthrough: " +
                String.join(", ", tryBackends));


        ServerPing.Builder serverPing = event.getPing().asBuilder();

        for (String backendName : tryBackends) {
            Optional<RegisteredServer> optionalBackend = this.config.server().getServer(backendName);
            if (optionalBackend.isEmpty()) {
                continue;
            }
            RegisteredServer backend = optionalBackend.get();

            ServerPing backendPing;
            try {
                backendPing = backend.ping().join();
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            if (backendPing == null) {
                continue;
            }
            serverPing.description(backendPing.getDescriptionComponent());

            event.setPing(serverPing.build());
            break;
        }

        // if none of the backends responded, then serverPing should have never been updated.
    }
}
