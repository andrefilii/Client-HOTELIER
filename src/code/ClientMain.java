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
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ClientMain {
    private String hostName;
    private int port;
    private Scanner in;
    private PrintWriter out;
    private Scanner terminal;
    private Gson gson;
    private Thread listener;
    private AtomicBoolean stopListener;
    private ReentrantLock consoleLock;

    public ClientMain() {
        hostName = AppConfig.getServerAddress();
        port = AppConfig.getServerPort();
        gson = new GsonBuilder().setPrettyPrinting().create();
        stopListener = new AtomicBoolean(false);
        consoleLock = new ReentrantLock();
    }

    private void startBackgroundListener(String group, int port) {
        this.listener = new Thread(() -> {
            try {
                MulticastSocket ms = new MulticastSocket(port);
                InetAddress ia = InetAddress.getByName(group);
                ms.joinGroup(ia);
                ms.setSoTimeout(2000); // ogni due secondi, se non riceve niente, controlla la guardia del while (permette la corretta terminazione del programma)

                byte[] buffer = new byte[2048];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                while (!stopListener.get()) {
                    try {
                        ms.receive(dp);
                        int a = 2;
                        consoleLock.lock();
                        printLn("Nuovi primi posti:\n" + new String(dp.getData(), 0, dp.getLength()));
                        consoleLock.unlock();

                    } catch (SocketTimeoutException ignored) {}
                }

                ms.leaveGroup(ia);
                ms.close();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // provo a rilasciare la lock nel caso il thread sia stato interrotto mentre stava stampando
                try {
                    consoleLock.unlock();
                } catch (IllegalMonitorStateException ignored) {}
            }
        });

        this.listener.start();
    }

    private void register() {
        consoleLock.lock();

        terminal.nextLine();
        print("username: "); String username = terminal.nextLine();
        print("password: "); String password = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);

        String request = "register\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        printLn(response.printResponseFormat());

        consoleLock.unlock();
    }

    private void login() {
        consoleLock.lock();

        terminal.nextLine();
        print("username: "); String username = terminal.nextLine();
        print("password: "); String password = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);

        String request = "login\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        printLn(response.printResponseFormat());

        consoleLock.unlock();

        if (response.getStatus() == 200) {
            // se il login è andato a buon fine, il client si mette in ascolto di notifiche sui ranking locali
            // estrapolo gruppo e porta dal corpo della risposta
            try {
                JsonObject jsonResponse = gson.fromJson(response.getBody(), JsonObject.class);
                String group = jsonResponse.get("group").getAsString();
                int port = jsonResponse.get("port").getAsInt();
                startBackgroundListener(group, port);
            } catch (JsonSyntaxException | NullPointerException e) {
                printLn("Errore nell'elaborazione del corpo della risposta");
            }
        }
    }

    private void logout() {
        consoleLock.lock();

        terminal.nextLine();
        print("username: "); String username = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);

        String request = "logout\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        printLn(response.printResponseFormat());

        consoleLock.unlock();
    }

    private void searchHotel() {
        consoleLock.lock();

        terminal.nextLine();
        print("Nome Hotel: "); String hotel = terminal.nextLine();
        print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("nomeHotel", hotel);
        json.addProperty("citta", citta);

        String request = "searchHotel\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        printLn(response.printResponseFormat());

        consoleLock.unlock();
    }

    private void searchAllHotels() {
        consoleLock.lock();

        terminal.nextLine();
        print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("citta", citta);

        String request = "searchAllHotels\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        printLn(response.printResponseFormat());

        consoleLock.unlock();
    }

    private void insertReview() {
        consoleLock.lock();
        // TODO GESTIRE CONVERSIONE NUMERI
        terminal.nextLine();
        print("Nome Hotel: "); String hotel = terminal.nextLine();
        print("Città: "); String citta = terminal.nextLine();

        double globalScore, pulizia, posizione, servizi, qualita;
        try {
            print("Global Score: ");globalScore = Double.parseDouble(terminal.nextLine());
            print("Pulizia: ");pulizia = Double.parseDouble(terminal.nextLine());
            print("Posizione: ");posizione = Double.parseDouble(terminal.nextLine());
            print("Servizi: ");servizi = Double.parseDouble(terminal.nextLine());
            print("Qualità/prezzo: ");qualita = Double.parseDouble(terminal.nextLine());
        } catch (NullPointerException | NumberFormatException e) {
            printLn("Errore nel valore inserito!");
            consoleLock.unlock();
            return;
        }

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

        printLn(response.printResponseFormat());

        consoleLock.unlock();
    }

    private void showMyBadges() {
        consoleLock.lock();

        terminal.nextLine();
        String request = "showMyBadges\n";

        Response response = toResponseObject(performRequest(request));

        printLn(response.printResponseFormat());

        consoleLock.unlock();
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
        consoleLock.lock();
        printLn("========================= HOTELIER: an HOTEL advIsor sERvice =========================");

        String legenda = "Legenda comandi:\n" +
                "\t1 -> registrati al servizio\n" +
                "\t2 -> effettua il login\n" +
                "\t3 -> effettua il logout\n" +
                "\t4 -> cerca un hotel per nome e città\n" +
                "\t5 -> cerca hotel per città\n" +
                "\t6 -> inserisci recensione di un hotel\n" +
                "\t7 -> mostra i miei badges\n" +
                "\t0 -> chiudi il programma";

        printLn(legenda);

        consoleLock.unlock();

        boolean end = false;
        while (!end) {
            consoleLock.lock();
            print("=>");
            int command = -1;
            try {
                command = terminal.nextInt();
            } catch (InputMismatchException ignored) {
                // consumo il valore errato
                terminal.next();
            }
            consoleLock.unlock();
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
                    consoleLock.lock();
                    printLn("COMANDO ERRATO!\n" + legenda);
                    consoleLock.unlock();
                    break;
            }
        }
    }

    private void shutdown() {
        this.stopListener.set(true);
    }

    public void start() {
        printLn("Trying " + hostName + ":" + port + " ...");
        try (Socket socket = new Socket(hostName, port);
             Scanner in = new Scanner(socket.getInputStream());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner terminal = new Scanner(System.in)) {

            this.in = in;
            this.out = out;
            this.terminal = terminal;

            printLn("Connected to " + hostName);

            waitForCommands();

            printLn("Shutting down client...");

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            printLn("UNEXPETDED ERROR. SHUTTING DOWN");
            e.printStackTrace();
        } finally {
            // provo a rilasciare la lock, nel caso non sia stat rilasciata correttamente dal programma
            try {
                consoleLock.unlock();
            } catch (IllegalMonitorStateException ignored) {}
            shutdown();
        }
    }

    private void print(String msg) {
        System.out.print(msg);
        System.out.flush();
    }
    
    private void printLn(String msg) {
        System.out.println(msg);
        System.out.flush();
    }

    public static void main(String[] args) {
        new ClientMain().start();
    }
}
