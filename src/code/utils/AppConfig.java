package code.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {
    private static final Properties properties = initialize();

    private AppConfig(){}

    private static Properties initialize() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("src/resources/application.properties")) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties;
    }

    public static String getServerAddress() throws NullPointerException{
        String url = properties.getProperty("server.address");
        if (url == null) throw new NullPointerException("Property 'server.address' can't be null");
        return url;
    }

    public static Integer getServerPort() throws NumberFormatException {
        return Integer.parseInt(properties.getProperty("server.port", "800"));
    }
}
