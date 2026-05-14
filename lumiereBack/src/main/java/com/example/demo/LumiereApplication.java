package com.example.demo;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

import org.springframework.web.cors.CorsConfiguration;

import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.web.filter.CorsFilter;

@SpringBootApplication
@EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
public class LumiereApplication {

	public static void main(String[] args) {
		SpringApplication.run(LumiereApplication.class, args);

	}

	@Bean

	public CorsFilter corsFilter() {

		CorsConfiguration corsConfiguration = new CorsConfiguration();

		UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();

		corsConfiguration.setAllowCredentials(true);

		corsConfiguration.setAllowedOrigins(Arrays.asList("http://192.168.1.107:8100", "http://192.168.1.107:4200",
				"http://172.18.101.132:4200", "http://172.18.101.132:8100",
				"http://172.18.3.125:4200", "http://18.209.167.231",
				"http://172.16.3.76:4200", "http://172.16.3.76:8100",
				"http://172.20.10.6:4200", "http://172.20.10.6:8100",
				"http://172.18.101.192:4200", "http://172.18.101.192:8100",
				"http://localhost:8100", "http://localhost:4200"));

		corsConfiguration.setAllowedHeaders(Arrays.asList("Origin", "Access-Control-Allow-Origin", "Content-Type",

				"Accept", "Jwt-Token", "Authorization", "Origin, Accept", "X-Requested-With",

				"Access-Control-Request-Method", "Access-Control-Request-Headers"));

		corsConfiguration.setExposedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Jwt-Token",
				"Authorization",

				"Access-Control-Allow-Origin", "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));

		corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

		urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);

		return new CorsFilter(urlBasedCorsConfigurationSource);
	}

}
