package br.com.tiagotds.transfereasy.infrastructure.config;

/** @param port {@code 0} binds an ephemeral port */
public record HttpSettings(int port, int maxBodyBytes) {

    static HttpSettings from(Config config) {
        return new HttpSettings(config.port("http.port"), config.positiveInt("http.max-body-bytes"));
    }
}
