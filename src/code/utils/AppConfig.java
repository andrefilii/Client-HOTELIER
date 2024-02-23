package code.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class AppConfig {
    private static final Properties properties = initialize();

    private AppConfig(){}

    private static Properties initialize() {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("application.properties")) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties;
    }

    /**
     * Permette di ottenere l'indirizzo del server, speficiato dalla proprietà server.address
     * @return l'indirizzo del server
     * @throws NullPointerException se la proprietà non esiste nel file
     */
    public static String getServerAddress() throws NullPointerException{
        String url = properties.getProperty("server.address");
        if (url == null) throw new NullPointerException("Property 'server.address' can't be null");
        return url;
    }

    /**
     * Permette di ottenere la porta del server, specificata dalla proprietà server.port
     * @return la porta specificata, o in assenza quella di default (800)
     * @throws NumberFormatException se il valore nella proprietà non è un intero
     */
    public static Integer getServerPort() throws NumberFormatException {
        return Integer.parseInt(properties.getProperty("server.port", "800"));
    }
}
