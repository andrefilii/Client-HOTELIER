package code;

import code.entities.Response;
import code.utils.AppConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.File;
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
    private String newFirstPosition;
    
    // permette di gestire la concorrenza sulla variabile condivisa newFirstPositions
    private ReentrantLock newFirstPositionLock;

    public ClientMain() {
        hostName = AppConfig.getServerAddress();
        port = AppConfig.getServerPort();
        gson = new GsonBuilder().setPrettyPrinting().create();
        stopListener = new AtomicBoolean(false);
        newFirstPositionLock = new ReentrantLock();
    }

    private void startBackgroundListener(String group, int port) {
        this.listener = new Thread(() -> {
            try {
                MulticastSocket ms = new MulticastSocket(port);
                InetAddress ia = InetAddress.getByName(group);
                ms.joinGroup(ia);
                // ogni due secondi, se non riceve niente, controlla la guardia del while (permette la corretta terminazione del programma)
                ms.setSoTimeout(2000);

                byte[] buffer = new byte[2048];
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                while (!stopListener.get()) {
                    try {
                        // attende di ricevere un messaggio
                        ms.receive(dp);

//                        consoleLock.lock();
                        newFirstPositionLock.lock();
                        
                        newFirstPosition = new String(dp.getData(), 0, dp.getLength());
                        
                        newFirstPositionLock.unlock();
//                        consoleLock.unlock();

                    } catch (SocketTimeoutException ignored) {
                        // scaduto il timer sulla receive, controllo la guardia del while
                    }
                }

                ms.leaveGroup(ia);
                ms.close();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // provo a rilasciare la lock nel caso il thread sia stato interrotto mentre stava stampando
                try {
//                    consoleLock.unlock();
                    newFirstPositionLock.unlock();
                } catch (IllegalMonitorStateException ignored) {}
            }
        });

        this.listener.start();
    }

    /**
     * Permette di gestire la registrazione<br>
     * Richiede all'utente di inserire username e password
     */
    private void register() {
//        consoleLock.lock();

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

//        consoleLock.unlock();
    }

    /**
     * Permette di effettuare il login su server<br>
     * Richiede username e password
     */
    private void login() {
//        consoleLock.lock();

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

//        consoleLock.unlock();

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
     * Richiede lo username dell'utente loggato su questa connessione
     */
    private void logout() {
//        consoleLock.lock();

        terminal.nextLine();
        System.out.print("username: "); String username = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("username", username);

        String request = "logout\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

//        consoleLock.unlock();
    }

    /**
     * Permette di cercare un hotel<br>
     * Richiede nome e città dell'hotel
     */
    private void searchHotel() {
//        consoleLock.lock();

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

//        consoleLock.unlock();
    }

    /**
     * Permette di cercare gli hotel in una determinata città<br>
     * Richiede di passare la città
     */
    private void searchAllHotels() {
//        consoleLock.lock();

        terminal.nextLine();
        System.out.print("Città: "); String citta = terminal.nextLine();

        JsonObject json = new JsonObject();
        json.addProperty("citta", citta);

        String request = "searchAllHotels\n" +
                gson.toJson(json) + "\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

//        consoleLock.unlock();
    }

    /**
     * Permette di inserire una recensione per un hotel<br>
     * Richiede nome e città dell'hotel, e successivamente i diversi voti.
     * I voti devono essere valori numerici, anche con virgola, compresi tra 0 e 5
     */
    private void insertReview() {
//        consoleLock.lock();

        terminal.nextLine();
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
//            consoleLock.unlock();
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

//        consoleLock.unlock();
    }

    /**
     * Mostra i badge dell'utente (se connesso)
     */
    private void showMyBadges() {
//        consoleLock.lock();

        terminal.nextLine();
        String request = "showMyBadges\n";

        Response response = toResponseObject(performRequest(request));

        System.out.println(response.printResponseFormat());

//        consoleLock.unlock();
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
     * attende i comandi da tastiera
     */
    public void waitForCommands() {
//        consoleLock.lock();
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

//        consoleLock.unlock();

        boolean end = false;
        while (!end) {
//            consoleLock.lock();
            // controllo se ci sono notifiche sulle prime posizioni, e nel caso stampo
            newFirstPositionLock.lock();
            if (newFirstPosition != null) {
                System.out.println(
                    "-------------------------\n" +
                    "Nuovi primi posti:\n" +
                    newFirstPosition + "\n" +
                    "-------------------------"
                );
                newFirstPosition = null;
            }
            newFirstPositionLock.unlock();
            
            System.out.print("=>");
            int command = -1;
            try {
                command = terminal.nextInt();
            } catch (InputMismatchException ignored) {
                // consumo la riga errata
                terminal.nextLine();
            }
//            consoleLock.unlock();
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
//                    consoleLock.lock();
                    System.out.println("COMANDO ERRATO!\n" + legenda);
//                    consoleLock.unlock();
                    break;
            }
        }
    }

    /**
     * Esegue le operazioni necessarie ad una corretta terminazione del server, ovvero avvisa il thread listener di uscire
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
            // provo a rilasciare la lock, nel caso non sia stat rilasciata correttamente dal programma
            try {
//                consoleLock.unlock();
                newFirstPositionLock.unlock();
            } catch (IllegalMonitorStateException ignored) {}
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
