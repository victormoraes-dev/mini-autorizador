package br.com.vr.miniautorizador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniAuthorizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniAuthorizerApplication.class, args);
    }
}
