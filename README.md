# Client per servizio HOTELIER
## Creazione JAR ed esecuzione
1. javac -cp lib/gson-2.10.1.jar -d out src/**/*.java
2. jar cfm HotelierClient.jar Manifest.txt -C out .
3. java -jar HotelierClient.jar  
