package br.com.tiagotds.transfereasy;

import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import java.util.logging.Logger;

public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        var app = Application.start(Settings.from(Config.fromEnvironment()));
        Runtime.getRuntime().addShutdownHook(new Thread(app::close, "shutdown"));
        LOG.info("transfereasy listening on port " + app.port());
        Thread.currentThread().join();
    }
}
