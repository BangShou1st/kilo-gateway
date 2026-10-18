package com.sharedlibs.kilogateway.autoconfigure;

import com.sharedlibs.kilogateway.KiloGatewayClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "kilo.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KiloGatewayProperties.class)
public class KiloGatewayAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(KiloGatewayClient.class)
    public KiloGatewayClient kiloGatewayClient(KiloGatewayProperties properties) {
        return new KiloGatewayClient(properties);
    }
}
