package com.zerowipe;

import com.zerowipe.config.ZeroWipeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ZeroWipeProperties.class)
public class ZeroWipeApplication {

    public static void main(String[] args) {
        // The SQLite datasource lives under ${user.home}/.zerowipe; the driver
        // will not create that directory itself, so ensure it exists before
        // Spring wires up the datasource.
        Path dataDir = Path.of(System.getProperty("user.home"), ".zerowipe");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create ZeroWipe data directory: " + dataDir, e);
        }

        SpringApplication.run(ZeroWipeApplication.class, args);
    }
}
