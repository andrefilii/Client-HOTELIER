package code;

import code.entities.FirstPositionHotel;
import code.entities.Response;
import code.utils.AppConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ClientMain {
    private String hostName;

    private int port;

    // per leggere i messaggi inviati dal server
    private Scanner in;

    // per scrivere messaggi al server
    private PrintWriter out;

    // per leggere input da terminale
    private Scanner terminal;

    private Gson gson;

    // attende i messaggi multicast
    private Thread listener;

    // permette allo shutdown di interrompere il thread che attende un messaggio
    private AtomicBoolean stopListener;

    // variabile utilizzata dal thread in background per salvare le nuove prime posizioni
    private final Map<String, String> newFirstPositions;

    public ClientMain() {
        hostName = AppConfig.getServerAddress();
        port = AppConfig.getServerPort();
        gson = new GsonBuilder().setPrettyPrinting().create();
        stopListener = new AtomicBoolean(false);
        newFirstPositions = new HashMap<>();
    }

    private void startBackgroundListener(String group, int port) {
        this.stopListener.set(false);
        this.listener = new Thread(() -> {
            MulticastSocket ms = null;
            InetAddress ia = null;
            try {
                // serve per l'elaborazione del'array di hotel
                Type listType = new TypeToken<ArrayList<FirstPositionHotel>>() {}.getType();

                ms = new MulticastSocket(port);
                ia = InetAddress.getByName(group);
                ms.joinGroup(ia);
                // ogni due secondi, se non riceve niente, controlla la guardia del while (permette la corretta terminazione del programma)
                ms.setSoTimeout(2000);

                byte[] buffer = new byte[2048];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                while (!stopListener.get()) {
                    try {
                        // attende di ricevere un messaggio
                        ms.receive(dp);

                        // messaggio ricevuto: elabora l'array e lo inserisce nella mappa, mantenendo la lock
                        synchronized (this.newFirstPositions) {
                            // prendo la risposta come stringa
                            String response = new String(dp.getData(), 0, dp.getLength());
                            // creo la lista di prime posizioni
                            ArrayList<FirstPositionHotel> newFirstPositions = null;
                            try {
                                newFirstPositions = gson.fromJson(response, listType);
                            } catch (JsonSyntaxException ignored) {
                            }
                            if (newFirstPositions != null && !newFirstPositions.isEmpty()) {
                                // se ci sono aggiornamenti, li metto nella mappa condivisa this.newPositions
                                for (FirstPositionHotel newFirstPosition : newFirstPositions) {
                                    this.newFirstPositions.put(newFirstPosition.getCitta(), newFirstPosition.getNomeHotel());
                                }
                            }
                        }

                    } catch (SocketTimeoutException ignored) {
                        // scaduto il timer sulla receive, controllo la guardia del while
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // esco dal gruppo multicast
                if (ms != null && ia != null) {
                    try {
                        ms.leaveGroup(ia);
                        ms.close();
                    } catch (IOException ignored) {}
                }
            }
        });

        this.listener.start();
    }

    /**
     * Permette di gestire la registrazione<br>
     * Richiede all'utente di inserire username e password
     */
    private void register() {

        System.out.print("username: "); String username = terminal.nextLine();
        System.out.print("password: "); String password = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("password", password);

        String request = "register\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

    }

    /**
     * Permette di effettuare il login su server<br>
     * Richiede username e password
     */
    private void login() {

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

    /**
     * Permette di effettuare il logout<br>
     */
    private void logout() {

        String request = "logout\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

        if (response.getStatus() == 200) {
            this.stopListener.set(true);

            synchronized (this.newFirstPositions) {
                newFirstPositions.clear();
            }
        }

    }

    /**
     * Permette di cercare un hotel<br>
     * Richiede nome e città dell'hotel
     */
    private void searchHotel() {

        System.out.print("Nome Hotel: "); String hotel = terminal.nextLine();
        System.out.print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("nomeHotel", hotel);
        json.addProperty("citta", citta);

        String request = "searchHotel\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

    }

    /**
     * Permette di cercare gli hotel in una determinata città<br>
     * Richiede di passare la città
     */
    private void searchAllHotels() {

        System.out.print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("citta", citta);

        String request = "searchAllHotels\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

    }

    /**
     * Permette di inserire una recensione per un hotel<br>
     * Richiede nome e città dell'hotel, e successivamente i diversi voti.
     * I voti devono essere valori numerici, anche con virgola, compresi tra 0 e 5
     */
    private void insertReview() {

        System.out.print("Nome Hotel: "); String hotel = terminal.nextLine();
        System.out.print("Città: "); String citta = terminal.nextLine();

        double globalScore, pulizia, posizione, servizi, qualita;
        try {
            System.out.print("Global Score: ");globalScore = Double.parseDouble(terminal.nextLine());
            if (globalScore < 0 || globalScore > 5) {
                System.out.println("Errore intervallo global score! Il valore deve essere compreso tra 0 e 5");
                return;
            }
            System.out.print("Pulizia: ");pulizia = Double.parseDouble(terminal.nextLine());
            if (pulizia < 0 || pulizia > 5) {
                System.out.println("Errore intervallo pulizia! Il valore deve essere compreso tra 0 e 5");
                return;
            }
            System.out.print("Posizione: ");posizione = Double.parseDouble(terminal.nextLine());
            if (posizione < 0 || posizione > 5) {
                System.out.println("Errore intervallo posizione! Il valore deve essere compreso tra 0 e 5");
                return;
            }
            System.out.print("Servizi: ");servizi = Double.parseDouble(terminal.nextLine());
            if (servizi < 0 || servizi > 5) {
                System.out.println("Errore intervallo servizi! Il valore deve essere compreso tra 0 e 5");
                return;
            }
            System.out.print("Qualità/prezzo: ");qualita = Double.parseDouble(terminal.nextLine());
            if (qualita < 0 || qualita > 5) {
                System.out.println("Errore intervallo qualità! Il valore deve essere compreso tra 0 e 5");
                return;
            }
        } catch (NullPointerException | NumberFormatException e) {
            System.out.println("Errore nel valore inserito!");
            return;
        }

        // i ratings delle categorie vanno in un sotto-oggetto
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

    }

    /**
     * Mostra i badge dell'utente (se connesso)
     */
    private void showMyBadges() {

        String request = "showMyBadges\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

    }

    /**
     * Permette di eseguire la richiesta passata come stringa al server.
     * Si aspetta di riceve un messaggio che termina appena viene trovata una riga vuota
     * @param request richiesta da mandare, in formato json
     * @return una stringa rappresentante la risposta del server
     */
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

    /**
     * Permette di prendere la risposta del server e trasformarla in un oggetto che distingue le diverse parti<br>
     * Contiene lo status code, la descrizione associata allo status code, e il body
     * @param response la risposta del server
     * @return risposta del server come entità Response
     * @see Response
     */
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

    /**
     * Stampa le nuove prime posizioni (se presenti), preservando concorrenza sulla mappa
     */
    private void stampaPrimePosizioni() {
        synchronized (this.newFirstPositions) {
            if (!this.newFirstPositions.isEmpty()) {
                StringBuilder builder = new StringBuilder("-------------------------\nNuovi primi posti:\n");

                // consuma tutte le città che hanno cambiato posizione e le elimina dalla mappa
                Iterator<Map.Entry<String, String>> entryIterator = this.newFirstPositions.entrySet().iterator();
                while (entryIterator.hasNext()) {
                    Map.Entry<String, String> entry = entryIterator.next();
                    builder.append("Città: ").append(entry.getKey())
                            .append(", Nome Hotel: ").append(entry.getValue()).append("\n");
                    entryIterator.remove();
                }

                builder.append("-------------------------\n");

                System.out.print(builder);
            }
        }
    }

    /**
     * attende i comandi da tastiera
     */
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
                "\t8 -> mostra legenda comandi\n" +
                "\t0 -> chiudi il programma";

        System.out.println(legenda);

        boolean end = false;
        while (!end) {
            int command = -1;
            boolean stop = false;
            do {
                // controllo se ci sono notifiche sulle prime posizioni, e nel caso stampo
                stampaPrimePosizioni();

                System.out.print("=>");
                try {
                    String line = terminal.nextLine();
                    if (!line.isEmpty()) {
                        command = Integer.parseInt(line);
                        stop = true;
                    }
                } catch (NumberFormatException e) {
                    // consumo la riga errata
                    System.out.println("COMANDO ERRATO!");
                    //terminal.nextLine();
                }
            } while(!stop);

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
                case 8:
                    System.out.println(legenda);
                    break;
                default:
                    System.out.println("Nessun comando associato al codice!\n" + legenda);
                    break;
            }
        }
    }

    /**
     * Esegue le operazioni necessarie per una corretta terminazione del server, ovvero avvisa il thread listener di uscire
     * dal while
     */
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

            System.out.println("Shutting down client...");

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            System.out.println("UNEXPETDED ERROR. SHUTTING DOWN");
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * Permette di controllare i prerequisiti richiesti affinchè l'applicazione possa funzionare correttamente
     * @return true se sono soddisfatti, false altrimenti
     */
    private static boolean controllaPrerequisiti() {
        // controllo che esista il file delle proprietà nella root del progetto
        File file = new File("application.properties");
        return file.exists() && file.canRead();
    }

    public static void main(String[] args) {
        if(!controllaPrerequisiti()) {
            throw new RuntimeException("ERRORE! Il programma per avviarsi correttamente deve avere il file application.properties presente nella stessa cartella del JAR/progetto");
        }
        new ClientMain().start();
    }
}
