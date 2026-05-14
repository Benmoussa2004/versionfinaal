package com.example.demo.script;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

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
        // [OPTIMISATION TEMPORELLE] Calculer la date d'il y a 30 jours
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date thirtyDaysAgo = cal.getTime();

        // [OPTIMISATION] Charger les ordres NON_PLANIFIE OU créés depuis 30 jours
        List<Ordre> ordres = ordreRepository.findOrdersToSync(thirtyDaysAgo);
        if (ordres.isEmpty()) return;

        // [OPTIMISATION] Map pour recherche O(1)
        Map<String, Map<String, Object>> planifieMap = ordresPlanifies.stream()
            .filter(obj -> obj instanceof Map)
            .map(obj -> (Map<String, Object>) obj)
            .collect(Collectors.toMap(
                m -> String.valueOf(m.get("OTSNUMBDX")),
                m -> m,
                (existing, replacement) -> existing
            ));

        for (Ordre ordre : ordres) {
            Map<String, Object> data = planifieMap.get(ordre.getOrderNumber());
            if (data != null) {
                ordre.setStatut(Statut.PLANIFIE);
                ordre.setVoycle(String.valueOf(data.get("VOYCLE")));
                ordre.setChauffeur(String.valueOf(data.get("SALNOM")));
                ordre.setTelchauffeur(String.valueOf(data.get("SALTEL")));
                ordre.setCamion(String.valueOf(data.get("PLAMOTI")));
                ordre.setDatevoy(String.valueOf(data.get("VOYDTD")));
            }
        }
        
        ordreRepository.saveAll(ordres);
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

                // [OPTIMISATION] Mappage propre du statut
                int eventCount = o.getEvents().size();
                switch (eventCount) {
                    case 1: o.setStatut(Statut.PLANIFIE); break;
                    case 2:
                    case 3: o.setStatut(Statut.EN_COURS_DE_CHARGEMENT); break;
                    case 4: o.setStatut(Statut.CHARGE); break;
                    case 5: o.setStatut(Statut.EN_COURS_DE_LIVRAISON); break;
                    case 6: o.setStatut(Statut.LIVRE); break;
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
        // [OPTIMISATION] Filtrage DB
        List<Ordre> listordre = ordreRepository.findByVoycleIsNotNullAndStatutNotIn(
            Arrays.asList(Statut.NON_CONFIRME, Statut.NON_PLANIFIE, Statut.LIVRE)
        );

        if (listordre.isEmpty()) return;

        // [OPTIMISATION] Parallélisation Python
        List<CompletableFuture<Void>> futures = listordre.stream()
            .map(ord -> CompletableFuture.runAsync(() -> this.updateOrdrevent(ord.getVoycle())))
            .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

}