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
    }

	public static void main(String[] args) {
		SpringApplication.run(CarimbaiApplication.class, args);
	}

}
