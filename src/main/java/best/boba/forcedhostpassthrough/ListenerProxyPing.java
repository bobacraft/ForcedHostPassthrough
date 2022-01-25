package best.boba.forcedhostpassthrough;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.api.util.ModInfo;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class ListenerProxyPing {
    final Config config;
    public ListenerProxyPing(Config config) {
        this.config = config;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onProxyPing(ProxyPingEvent event) {
        InboundConnection connection = event.getConnection();
        Optional<InetSocketAddress> optionalVirtualHost = connection.getVirtualHost();
        if (optionalVirtualHost.isEmpty()) {
            return;
        }

        InetSocketAddress virtualHost = optionalVirtualHost.get();
        String hostString = virtualHost.getHostString();

        Map<String, List<String>> forcedHosts = this.config.server().getConfiguration().getForcedHosts();
        List<String> tryBackends = forcedHosts.get(hostString);
        if (tryBackends == null) {
            return;
        }


        ServerPing.Builder serverPing = event.getPing().asBuilder();

        for (String backendName : tryBackends) {
            Optional<RegisteredServer> optionalBackend = this.config.server().getServer(backendName);
            if (optionalBackend.isEmpty()) {
                continue;
            }
            RegisteredServer backend = optionalBackend.get();

            ServerPing backendPing;
            try {
                backendPing = backend.ping().get();
            }
            catch (InterruptedException | ExecutionException e) {
                continue;
            }

            serverPing.description(backendPing.getDescriptionComponent());
            serverPing.version(backendPing.getVersion());

            Optional<Favicon> optionalFavicon = backendPing.getFavicon();
            if (optionalFavicon.isPresent()) {
                serverPing.favicon(optionalFavicon.get());
            } else {
                serverPing.clearFavicon();
            }

            Optional<ModInfo> optionalModInfo = backendPing.getModinfo();
            if (optionalModInfo.isPresent()) {
                serverPing.mods(optionalModInfo.get());
            } else {
                serverPing.clearMods();
            }

            Optional<ServerPing.Players> optionalPlayers = backendPing.getPlayers();
            if (optionalPlayers.isPresent()) {
                ServerPing.Players players = optionalPlayers.get();
                serverPing.onlinePlayers(players.getOnline());
                serverPing.maximumPlayers(players.getMax());
                serverPing.samplePlayers(players.getSample().toArray(new ServerPing.SamplePlayer[0]));
                // ^^ this last line needs testing
            } else {
                serverPing.nullPlayers();
            }

            event.setPing(serverPing.build());
            break; // do not try and fall back to any further backends because we're done
        }

        // if none of the backends responded, then serverPing should have never been updated.
        // that means that it will fall back to the default in Velocity config
    }
}
