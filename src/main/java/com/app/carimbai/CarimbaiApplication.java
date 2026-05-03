package com.app.carimbai;

import com.app.carimbai.config.OAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(OAuthProperties.class)
public class CarimbaiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CarimbaiApplication.class, args);
	}

}
