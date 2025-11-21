package com.app.carimbai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Slf4j
@SpringBootApplication
public class CarimbaiApplication {

    public CarimbaiApplication(Environment env) {
        log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
        log.info("DB_URL_DEV: {}", env.getProperty("DB_URL_DEV"));
        log.info("DB_URL: {}", env.getProperty("DB_URL"));
        log.info("DB_USERNAME: {}", env.getProperty("DB_USERNAME"));
        log.info("DB_PASSWORD: {}", env.getProperty("DB_PASSWORD"));
    }

	public static void main(String[] args) {
		SpringApplication.run(CarimbaiApplication.class, args);
	}

}
