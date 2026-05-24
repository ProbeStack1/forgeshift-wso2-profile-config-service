package com.forgeshift.profile.config.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;

/**
 * One WebClient bean per upstream we verify against:
 *   wso2VerifyWebClient   - for WSO2 /oauth2/token + /api/am/admin/v4/* probes
 *   kongKonnectVerifyWebClient - for Konnect /v2/control-planes/* probes
 *
 * Both use a fresh insecure-TLS HttpClient so per-call trustSelfSigned can be
 * honoured. Real production deployments should override these beans with
 * properly-configured trust stores.
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Bean(name = "wso2VerifyWebClient")
    public WebClient wso2VerifyWebClient(ProfileConfigProperties props) throws SSLException {
        return insecureWebClient(props.getVerify().getTimeoutSeconds());
    }

    @Bean(name = "kongKonnectVerifyWebClient")
    public WebClient kongKonnectVerifyWebClient(ProfileConfigProperties props) throws SSLException {
        return insecureWebClient(props.getVerify().getTimeoutSeconds());
    }

    private WebClient insecureWebClient(int timeoutSeconds) throws SSLException {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .secure(spec -> {
                    try {
                        spec.sslContext(SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                .build());
                    } catch (SSLException e) {
                        throw new IllegalStateException("Failed to build insecure SSL context", e);
                    }
                });
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
