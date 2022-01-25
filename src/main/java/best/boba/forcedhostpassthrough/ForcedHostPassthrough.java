package best.boba.forcedhostpassthrough;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(id = "forcedhostpassthrough",
        name = "ForcedHostPassthrough",
        version = "1.0",
        url = "https://github.com/bobacraft/ForcedHostPassthrough",
        authors = {"bbaovanc"},
        description = "Enable ping passthrough on Velocity, but only for forced hosts (not the main address)"
)
public class ForcedHostPassthrough {
    private final Config config;

    @Inject
    public ForcedHostPassthrough(ProxyServer server, Logger logger) {
        this.config = new Config(server, logger);
    }

    public void initialize() {
        EventManager eventManager = this.config.server().getEventManager();
        eventManager.register(this, new ListenerProxyPing(config));
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        initialize();
    }
}
