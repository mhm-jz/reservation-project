package com.azki.reservation;

import com.azki.reservation.config.JwtProperties;
import com.azki.reservation.config.SlotCacheProperties;
import com.azki.reservation.config.SlotSearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        SlotCacheProperties.class,
        SlotSearchProperties.class
})
public class ReservationServiceApplication {

    public static void main(String[] args) {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ReservationServiceApplication.class, args);
    }
}
