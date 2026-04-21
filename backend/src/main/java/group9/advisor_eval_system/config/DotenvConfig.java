package group9.advisor_eval_system.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DotenvConfig {

    static {
        // Best-effort: load environment variables from .env during development.
        // Does not override real OS environment variables.
        tryLoadDotenv();
    }

    private static void tryLoadDotenv() {
        try {
            String directory = findDotenvDirectory();
            if (directory == null) {
                return;
            }

            Dotenv dotenv = Dotenv.configure()
                    .directory(directory)
                    .filename(".env")
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();

            dotenv.entries().forEach(e -> {
                String key = e.getKey();
                String value = e.getValue();
                if (key == null || key.isBlank() || value == null) {
                    return;
                }

                // Respect real environment variables and existing system properties.
                if (System.getenv(key) != null) {
                    return;
                }
                if (System.getProperty(key) != null) {
                    return;
                }

                System.setProperty(key, value);
            });
        } catch (Exception ignored) {
            // Intentionally ignore; .env is optional.
        }
    }

    private static String findDotenvDirectory() {
        // Common run modes:
        // - running from /backend: ./.env exists
        // - running from repo root: ./backend/.env exists
        if (Files.exists(Path.of(".env"))) {
            return ".";
        }
        if (Files.exists(Path.of("backend", ".env"))) {
            return "backend";
        }
        return null;
    }
}
