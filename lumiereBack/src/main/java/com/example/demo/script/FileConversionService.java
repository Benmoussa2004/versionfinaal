package com.example.demo.script;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.Entity.Ordre;
import com.example.demo.Entity.Statut;
import com.example.demo.Repository.OrdreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FileConversionService {

    @Autowired
    private OrdreRepository ordreRepository;

    private final String scriptPath = "..\\ConvertScript.py";
    private final String inputPath = "\\\\172.18.3.56\\requetes_edge_5555\\mesvoyes.json";
    private final String outputPath = "..\\mesvoyes_converted.json";

    public List<?> convertFileAndLoadResults() {
        // Execute the Python script
        PythonScriptExecutor.executePythonScript(scriptPath, inputPath, outputPath);

        // Read the converted JSON file and convert it to a list
        return JsonReader.readJsonFileToList(outputPath);
    }

    public List<?> executePythonScript(String param) {
        List<Object> results = new ArrayList<>();
        if (param == null) {
            System.err.println("DEBUG: executePythonScript called with null param");
            results.add("Error: param is null");
            return results;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python",
                    "..\\event.py",
                    param);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            process.waitFor();

            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                results.add("No data returned from the script.");
            } else {
                results = new ObjectMapper().readValue(jsonOutput, List.class);
            }

        } catch (Exception e) {
            e.printStackTrace();
            results.add("Error: " + e.getMessage());
        }
        return results;
    }

    public void updateOrderStatus() {
        // charger les ordres planifiés à partir du fichier converti
        List<?> ordresPlanifies = convertFileAndLoadResults();
        if (ordresPlanifies == null) {
            return;
        }
        // Charger tous les ordres depuis le repository
        List<Ordre> ordres = ordreRepository.findByStatut(Statut.NON_PLANIFIE);

        // Parcourir et comparer les listes
        for (Ordre ordre : ordres) {
            for (Object obj : ordresPlanifies) {
                if (obj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ordrePlanifie = (Map<String, Object>) obj;
                    String otsNumBdx = (String) ordrePlanifie.get("OTSNUMBDX");

                    if (ordre.getOrderNumber().equals(otsNumBdx)) {
                        String voycle = ordrePlanifie.get("VOYCLE").toString();
                        String salnom = ordrePlanifie.get("SALNOM").toString();
                        String saltel = ordrePlanifie.get("SALTEL").toString();
                        String camion = ordrePlanifie.get("PLAMOTI").toString();
                        String datevoy = ordrePlanifie.get("VOYDTD").toString();

                        // Update the order status and other fields
                        ordre.setStatut(Statut.PLANIFIE);
                        ordre.setVoycle(voycle);
                        ordre.setChauffeur(salnom);
                        ordre.setTelchauffeur(saltel);
                        ordre.setCamion(camion);
                        ordre.setDatevoy(datevoy);
                        break;
                    }
                }
            }
        }
        // Enregistrer les ordres mis à jour dans la base de données
        if (!ordres.isEmpty()) {
            ordreRepository.saveAll(ordres);
        }
    }

    public Set<String> updateOrdrevent(String param) {
        if (param == null || param.trim().isEmpty()) {
            return Collections.emptySet();
        }

        List<?> events = executePythonScript(param);
        if (events == null || events.isEmpty()
                || (events.size() == 1 && events.get(0).toString().startsWith("Error"))) {
            return Collections.emptySet();
        }
        System.out.println(events);

        List<Ordre> matchingOrdres = ordreRepository.findByVoycle(param);
        if (matchingOrdres == null || matchingOrdres.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> latestEvents = new HashSet<>();
        for (Ordre o : matchingOrdres) {
            Set<String> listevents = o.getEvents();

            if (listevents == null) {
                listevents = new HashSet<>();
            }

            if (listevents.size() < events.size()) {

                listevents.clear();
                o.setEvents(listevents);

                for (Object obj : events) {
                    if (obj instanceof Map) { // Ensure the objects are of type Map
                        @SuppressWarnings("unchecked")
                        Map<String, Object> event = (Map<String, Object>) obj;
                        String date_saisi = event.get("date_saisi").toString();
                        listevents.add(date_saisi);
                    }
                }
                List<String> eventList = new ArrayList<>(listevents);
                Collections.sort(eventList);
                Set<String> evs = new LinkedHashSet<>(eventList);
                o.setEvents(evs);
                System.out.println(listevents);

                if (o.getEvents().size() == 1) {
                    o.setStatut(Statut.PLANIFIE);

                }

                if (o.getEvents().size() == 2) {
                    o.setStatut(Statut.EN_COURS_DE_CHARGEMENT);

                }

                if (o.getEvents().size() == 3) {
                    o.setStatut(Statut.EN_COURS_DE_CHARGEMENT);

                }

                if (o.getEvents().size() == 4) {
                    o.setStatut(Statut.CHARGE);

                }
                if (o.getEvents().size() == 5) {
                    o.setStatut(Statut.EN_COURS_DE_LIVRAISON);

                }

                if (o.getEvents().size() == 6) {
                    o.setStatut(Statut.LIVRE);

                }

                ordreRepository.save(o);
                latestEvents = evs;
            }
        }

        return latestEvents;
    }

    @Scheduled(cron = "0 */4 * * * *")
    public void updateAllordre() {

        this.updateOrderStatus();

    }

    @Scheduled(cron = "0 */7 * * * *")
    public void updateAllordresevents() {

        List<Ordre> listordre = ordreRepository.findAll();

        for (Ordre ord : listordre) {
            if (ord.getStatut() != Statut.NON_CONFIRME && ord.getStatut() != Statut.NON_PLANIFIE) {
                if (ord.getVoycle() != null) {
                    this.updateOrdrevent(ord.getVoycle());
                } else if (ord.getStatut() != null) {
                    System.err.println("DEBUG: Skipping event sync for order " + ord.getOrderNumber()
                            + " because voycle is null (statut: " + ord.getStatut() + ")");
                }
            }
        }

    }

}