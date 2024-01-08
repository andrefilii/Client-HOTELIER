package code;

import code.entities.Response;
import code.utils.AppConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientMain {
    private String hostName;
    private int port;
    private Scanner in;
    private PrintWriter out;
    private Scanner terminal;
    private Gson gson;
    private Thread listener;
    private AtomicBoolean stopListener;

    public ClientMain() {
        hostName = AppConfig.getServerAddress();
        port = AppConfig.getServerPort();
        gson = new GsonBuilder().setPrettyPrinting().create();
        stopListener = new AtomicBoolean(false);
    }

    private void startBackgroundListener(String group, int port) {
        this.listener = new Thread(() -> {
            try {
                MulticastSocket ms = new MulticastSocket(port);
                InetAddress ia = InetAddress.getByName(group);
                ms.joinGroup(ia);
                ms.setSoTimeout(2000); // ogni due secondi, se non riceve niente, controlla la guardia del while

                byte[] buffer = new byte[1024];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                while (!stopListener.get()) {
                    System.out.println("Hi");
                    try {
                        ms.receive(dp);
                        System.out.println("Dati arrivati");
                        // TODO elaborare i dati e inserirli in una struttura dati
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout");
                    }
                }

                ms.leaveGroup(ia);
                ms.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        this.listener.start();
    }

    private void register() {
        terminal.nextLine();
        System.out.print("username: "); String username = terminal.nextLine();
        System.out.print("password: "); String password = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);

        String request = "register\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 201) {
            // TODO messaggio di successo
        }

    }

    private void login() {
        terminal.nextLine();
        System.out.print("username: "); String username = terminal.nextLine();
        System.out.print("password: "); String password = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);

        String request = "login\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            // se il login è andato a buon fine, il client si mette in ascolto di notifiche sui ranking locali
            // estrapolo gruppo e porta dal corpo della risposta
            try {
                JsonObject jsonResponse = gson.fromJson(response.getBody(), JsonObject.class);
                String group = jsonResponse.get("group").getAsString();
                int port = jsonResponse.get("port").getAsInt();
                startBackgroundListener(group, port);
            } catch (JsonSyntaxException | NullPointerException e) {
                System.out.println("Errore nell'elaborazione del corpo della risposta");
            }


        }
    }

    private void logout() {
        terminal.nextLine();
        System.out.print("username: "); String username = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);

        String request = "logout\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            // TODO messaggio di successo
        }
    }

    private void searchHotel() {
        terminal.nextLine();
        System.out.print("Nome Hotel: "); String hotel = terminal.nextLine();
        System.out.print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("nomeHotel", hotel);
        json.addProperty("citta", citta);

        String request = "searchHotel\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            // TODO messaggio di successo
        }
    }

    private void searchAllHotels() {
        terminal.nextLine();
        System.out.print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("citta", citta);

        String request = "searchAllHotels\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            // TODO messaggio di successo
        }
    }

    private void insertReview() {
        terminal.nextLine();
        System.out.print("Nome Hotel: "); String hotel = terminal.nextLine();
        System.out.print("Città: "); String citta = terminal.nextLine();
        System.out.print("Global Score: "); double globalScore = Double.parseDouble(terminal.nextLine());
        System.out.print("Pulizia: "); double pulizia = Double.parseDouble(terminal.nextLine());
        System.out.print("Posizione: "); double posizione = Double.parseDouble(terminal.nextLine());
        System.out.print("Servizi: "); double servizi = Double.parseDouble(terminal.nextLine());
        System.out.print("Qualità/prezzo: "); double qualita = Double.parseDouble(terminal.nextLine());

        JsonObject rates = new JsonObject();
        rates.addProperty("cleaning", pulizia);
        rates.addProperty("position", posizione);
        rates.addProperty("services", servizi);
        rates.addProperty("quality", qualita);

        JsonObject json = new JsonObject();
        json.addProperty("nomeHotel", hotel);
        json.addProperty("citta", citta);
        json.addProperty("globalScore", globalScore);
        json.add("singleScores", rates);

        String request = "insertReview\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            // TODO messaggio di successo
        }
    }

    private void showMyBadges() {
        terminal.nextLine();
        String request = "showMyBadges\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            // TODO messaggio di successo
        }
    }

    private String performRequest(String request) {
        out.println(request);

        StringBuilder bodyBuilder = new StringBuilder();
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.isEmpty()) break;
            bodyBuilder.append(line).append("\n");
        }

        return bodyBuilder.toString();
    }

    private Response toResponseObject(String response) {
        Scanner scanner = new Scanner(response);
        Integer status = scanner.nextInt();
        String descrizione = scanner.nextLine().trim();

        StringBuilder bodyBuilder = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.isEmpty()) break;
            bodyBuilder.append(line).append("\n");
        }

        return new Response(status, descrizione, bodyBuilder.toString());
    }

    public void waitForCommands() {
        System.out.println("========================= HOTELIER: an HOTEL advIsor sERvice =========================");

        String legenda = "Legenda comandi:\n" +
                "\t1 -> registrati al servizio\n" +
                "\t2 -> effettua il login\n" +
                "\t3 -> effettua il logout\n" +
                "\t4 -> cerca un hotel per nome e città\n" +
                "\t5 -> cerca hotel per città\n" +
                "\t6 -> inserisci recensione di un hotel\n" +
                "\t7 -> mostra i miei badges\n" +
                "\t0 -> chiudi il programma";

        System.out.println(legenda);

        boolean end = false;
        while (!end) {
            System.out.print("=>");
            int command = -1;
//            try {
                command = terminal.nextInt();
//            } catch (InputMismatchException ignored) {}
            switch (command) {
                case 0:
                    end = true;
                    break;
                case 1:
                    register();
                    break;
                case 2:
                    login();
                    break;
                case 3:
                    logout();
                    break;
                case 4:
                    searchHotel();
                    break;
                case 5:
                    searchAllHotels();
                    break;
                case 6:
                    insertReview();
                    break;
                case 7:
                    showMyBadges();
                    break;
                default:
                    System.out.println("COMANDO ERRATO!\n" + legenda);
                    break;
            }
        }
    }

    private void shutdown() {
        this.stopListener.set(true);
    }

    public void start() {
        System.out.println("Trying " + hostName + ":" + port + " ...");
        try (Socket socket = new Socket(hostName, port);
             Scanner in = new Scanner(socket.getInputStream());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner terminal = new Scanner(System.in)) {

            this.in = in;
            this.out = out;
            this.terminal = terminal;

            System.out.println("Connected to " + hostName);

            waitForCommands();

            shutdown();

            System.out.println("Shutting down client...");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        new ClientMain().start();
    }
}
