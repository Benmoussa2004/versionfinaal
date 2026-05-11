package com.example.demo.Service;

import com.example.demo.Entity.*;
import com.example.demo.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ChatbotService {
    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    @Autowired
    private OrdreRepository ordreRepository;

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private TranckRepository tranckRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:}")
    private String geminiModel;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:}")
    private String groqModel;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.model:}")
    private String openaiModel;

    @Value("${chatbot.provider:gemini}")
    private String provider;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public String getChatResponse(String userMessage, String userEmail, String platform) {
        return getChatResponseWithHistory(userMessage, null, userEmail, platform);
    }

    public String getChatResponseWithHistory(String userMessage, List<Map<String, String>> history, String userEmail, String platform) {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String systemPrompt = "Tu es 'Otflow Smart Assist', l'assistant IA expert de la plateforme OTFLOW (Lumière Transport). " +
                             "DATE ACTUELLE: " + currentTime + ". " +
                             "TONALITÉ: Professionnelle, serviable et précise. " +
                             "CAPACITÉS: Tu peux gérer les ordres de transport, suivre les livraisons, lister les clients, vérifier les statistiques de la flotte et consulter les notifications système. " +
                             "INSTRUCTIONS CRITIQUES: " +
                             "1. Utilise TOUJOURS les outils (functions) pour obtenir des données réelles. Ne devine jamais une référence d'ordre. " +
                             "2. Si l'information n'est pas disponible via les outils, explique poliment que tu n'as pas accès à cette donnée spécifique. " +
                             "3. Réponds de manière structurée avec des puces (bullet points) quand tu listes plusieurs éléments. " +
                             "4. Tu peux aussi répondre à des questions générales sur la logistique et le transport pour conseiller l'utilisateur. Par exemple, si on te pose une question sur le transport de l'eau, explique que l'eau minérale nécessite un transfert à température ambiante. Même si tu ne trouves pas le code article exact via tes outils, donne toujours des conseils pertinents sur les conditions de transport associées au produit demandé. " +
                             "5. Si l'utilisateur demande 'Que peux-tu faire ?', liste tes capacités principales de manière attractive.";

        if ("groq".equalsIgnoreCase(provider)) {
            return callGroq(userMessage, history, systemPrompt, userEmail, platform);
        } else if ("openai".equalsIgnoreCase(provider)) {
            return callOpenAI(userMessage, history, systemPrompt, userEmail, platform);
        } else {
            return callGemini(userMessage, history, systemPrompt, userEmail, platform);
        }
    }

    private String callGemini(String userMessage, List<Map<String, String>> history, String systemPrompt, String userEmail, String platform) {
        String url = String.format(GEMINI_API_URL, geminiModel, geminiApiKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();

        // Add history if present
        if (history != null) {
            for (Map<String, String> msg : history) {
                Map<String, Object> turn = new HashMap<>();
                turn.put("role", "user".equals(msg.get("role")) ? "user" : "model");
                turn.put("parts", Collections.singletonList(Collections.singletonMap("text", msg.get("content"))));
                contents.add(turn);
            }
        }

        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", Collections.singletonList(Collections.singletonMap("text", systemPrompt + "\n\nMessage: " + userMessage)));
        contents.add(userContent);
        requestBody.put("contents", contents);
        requestBody.put("tools", getGeminiTools(platform));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            Map<String, Object> firstCandidate = (Map<String, Object>) ((List) response.get("candidates")).get(0);
            Map<String, Object> modelContent = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) modelContent.get("parts");

            if (parts != null && !parts.isEmpty() && parts.get(0).containsKey("functionCall")) {
                Map<String, Object> functionCall = (Map<String, Object>) parts.get(0).get("functionCall");
                String functionName = (String) functionCall.get("name");
                Map<String, Object> args = (Map<String, Object>) functionCall.get("args");

                log.info("Gemini requested tool: {} with args: {}", functionName, args);
                Object result = executeTool(functionName, args, userEmail, platform);
                log.info("Tool result: {}", result);

                // Si c'est un message de refus (web), on le retourne directement sans 2ème tour
                if (result instanceof String && ((String) result).startsWith("D\u00e9sol\u00e9")) {
                    return (String) result;
                }

                List<Map<String, Object>> followUpContents = new ArrayList<>(contents);
                followUpContents.add(modelContent);

                Map<String, Object> functionResponseTurn = new HashMap<>();
                functionResponseTurn.put("role", "function");
                Map<String, Object> functionPart = new HashMap<>();
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("name", functionName);
                responseData.put("response", Collections.singletonMap("content", result));
                functionPart.put("functionResponse", responseData);
                functionResponseTurn.put("parts", Collections.singletonList(functionPart));
                followUpContents.add(functionResponseTurn);

                requestBody.put("contents", followUpContents);
                HttpEntity<Map<String, Object>> followUpRequest = new HttpEntity<>(requestBody, headers);
                
                log.info("Sending follow-up turn to Gemini...");
                Map<String, Object> finalResponse = restTemplate.postForObject(url, followUpRequest, Map.class);
                return extractTextFromGemini(finalResponse);
            }

            return extractTextFromGemini(response);
        } catch (HttpStatusCodeException e) {
            log.error("Gemini API Error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                return "Désolé, la limite de messages gratuite a été atteinte (Quota Gemini).";
            }
            return "Erreur Gemini : " + e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("General error in Gemini call: ", e);
            return "Désolé, erreur technique avec Gemini : " + e.getMessage();
        }
    }

    private String callOpenAI(String userMessage, List<Map<String, String>> history, String systemPrompt, String userEmail, String platform) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        
        if (history != null) {
            for (Map<String, String> msg : history) {
                messages.add(Map.of("role", msg.get("role"), "content", msg.get("content")));
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        requestBody.put("messages", messages);
        requestBody.put("tools", getGroqTools(platform)); // Same format as Groq
        requestBody.put("tool_choice", "auto");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(OPENAI_API_URL, request, Map.class);
            List choices = (List) response.get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map message = (Map) firstChoice.get("message");

            if (message.containsKey("tool_calls")) {
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                List<Map<String, Object>> followUpMessages = new ArrayList<>(messages);
                followUpMessages.add(message);

                for (Map<String, Object> toolCall : toolCalls) {
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    String functionName = (String) function.get("name");
                    String toolCallId = (String) toolCall.get("id");
                    
                    String argsJson = (String) function.get("arguments");
                    Map<String, Object> args = new HashMap<>();
                    if (argsJson != null && !argsJson.trim().isEmpty() && !argsJson.equals("{}")) {
                        try {
                            args = new com.fasterxml.jackson.databind.ObjectMapper().readValue(argsJson, Map.class);
                        } catch (Exception e) {
                            log.error("Error parsing arguments for tool {}: {}", functionName, argsJson);
                        }
                    }

                    Object result = executeTool(functionName, args, userEmail, platform);
                    
                    Map<String, Object> toolResponse = new HashMap<>();
                    toolResponse.put("role", "tool");
                    toolResponse.put("tool_call_id", toolCallId);
                    toolResponse.put("name", functionName);
                    toolResponse.put("content", String.valueOf(result));
                    followUpMessages.add(toolResponse);
                }

                requestBody.put("messages", followUpMessages);
                HttpEntity<Map<String, Object>> followUpRequest = new HttpEntity<>(requestBody, headers);
                Map<String, Object> finalResponse = restTemplate.postForObject(OPENAI_API_URL, followUpRequest, Map.class);
                return extractTextFromGroq(finalResponse); // Same structure
            }

            return extractTextFromGroq(response);
        } catch (Exception e) {
            log.error("Error in OpenAI call: ", e);
            return "Désolé, erreur avec OpenAI : " + e.getMessage();
        }
    }

    private String callGroq(String userMessage, List<Map<String, String>> history, String systemPrompt, String userEmail, String platform) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", groqModel);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        
        // Add history if present
        if (history != null) {
            for (Map<String, String> msg : history) {
                messages.add(Map.of("role", msg.get("role"), "content", msg.get("content")));
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        requestBody.put("messages", messages);
        requestBody.put("tools", getGroqTools(platform));
        requestBody.put("tool_choice", "auto");
        requestBody.put("temperature", 0.0); // Absolute precision for tools

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(GROQ_API_URL, request, Map.class);
            List choices = (List) response.get("choices");
            Map firstChoice = (Map) choices.get(0);
            Map message = (Map) firstChoice.get("message");

            if (message.containsKey("tool_calls")) {
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
                List<Map<String, Object>> followUpMessages = new ArrayList<>(messages);
                followUpMessages.add(message);

                for (Map<String, Object> toolCall : toolCalls) {
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    String functionName = (String) function.get("name");
                    String toolCallId = (String) toolCall.get("id");
                    
                    // Parse args
                    String argsJson = (String) function.get("arguments");
                    Map<String, Object> args = new HashMap<>();
                    if (argsJson != null && !argsJson.trim().isEmpty() && !argsJson.equals("{}")) {
                        try {
                            args = new com.fasterxml.jackson.databind.ObjectMapper().readValue(argsJson, Map.class);
                        } catch (Exception e) {
                            log.error("Error parsing arguments for tool {}: {}", functionName, argsJson);
                        }
                    }

                    log.info("Executing tool: {} with ID: {}", functionName, toolCallId);
                    Object result = executeTool(functionName, args, userEmail, platform);
                    
                    Map<String, Object> toolResponse = new HashMap<>();
                    toolResponse.put("role", "tool");
                    toolResponse.put("tool_call_id", toolCallId);
                    toolResponse.put("name", functionName);
                    toolResponse.put("content", String.valueOf(result));
                    followUpMessages.add(toolResponse);
                }

                requestBody.put("messages", followUpMessages);
                HttpEntity<Map<String, Object>> followUpRequest = new HttpEntity<>(requestBody, headers);
                
                log.info("Sending follow-up turn to Groq with {} tool results...", toolCalls.size());
                Map<String, Object> finalResponse = restTemplate.postForObject(GROQ_API_URL, followUpRequest, Map.class);
                String finalResult = extractTextFromGroq(finalResponse);
                
                if (finalResult == null || finalResult.trim().isEmpty()) {
                    // Fallback: If AI fails to summarize, show raw tool results
                    StringBuilder fallback = new StringBuilder("Voici les informations récupérées :\n\n");
                    for (Map<String, Object> msg : followUpMessages) {
                        if ("tool".equals(msg.get("role"))) {
                            fallback.append(msg.get("content")).append("\n\n");
                        }
                    }
                    return fallback.toString().trim();
                }
                return finalResult;
            }

            return extractTextFromGroq(response);
        } catch (HttpStatusCodeException e) {
            log.error("Groq API Error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "Erreur Groq : " + e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("General error in Groq call: ", e);
            return "Désolé, erreur technique avec Groq : " + e.getMessage();
        }
    }

    private List<Map<String, Object>> getGeminiTools(String platform) {
        List<Map<String, Object>> tools = new ArrayList<>();
        // Note: Gemini uses function_declarations wrapper
        tools.add(Map.of("function_declarations", getBaseTools(platform)));
        return tools;
    }
 
    private List<Map<String, Object>> getGroqTools(String platform) {
        List<Map<String, Object>> tools = new ArrayList<>();
        // Note: Groq/OpenAI uses a list of tool objects with type "function"
        for (Map<String, Object> tool : getBaseTools(platform)) {
            tools.add(Map.of("type", "function", "function", tool));
        }
        return tools;
    }
 
    private List<Map<String, Object>> getBaseTools(String platform) {
        List<Map<String, Object>> tools = new ArrayList<>();
 
        // Tool: get_order_details
        Map<String, Object> getOrder = new HashMap<>();
        getOrder.put("name", "get_order_details");
        getOrder.put("description", "Suivre un ORDRE DE TRANSPORT précis via une référence technique/numérique.");
        getOrder.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("order_ref", Map.of("type", "string")),
            "required", List.of("order_ref")
        ));
        tools.add(getOrder);

        // Tool: list_my_clients
        Map<String, Object> listClients = new HashMap<>();
        listClients.put("name", "list_my_clients");
        listClients.put("description", "Lister tous les clients que je gère");
        listClients.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
        tools.add(listClients);
 
        // Tool: list_my_orders
        Map<String, Object> listOrders = new HashMap<>();
        listOrders.put("name", "list_my_orders");
        listOrders.put("description", "Lister mes ordres de transport récents. Peut filtrer par statut.");
        listOrders.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of(
                "status", Map.of("type", "string", "description", "Statut à filtrer (ex: NON_PLANIFIE, NON_CONFIRME)"),
                "date_from", Map.of("type", "string", "description", "Date de début au format YYYY-MM-DD"),
                "location", Map.of("type", "string", "description", "Lieu, ville ou nom du site (ex: BAR, Tunis)")
            ),
            "required", List.of()
        ));
        tools.add(listOrders);

        // Tool: find_orders_by_client_name
        Map<String, Object> searchByClient = new HashMap<>();
        searchByClient.put("name", "find_orders_by_client_name");
        searchByClient.put("description", "Rechercher des ordres via le NOM d'un client (ex: Gias, Tunis Air).");
        searchByClient.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("client_name", Map.of("type", "string")),
            "required", List.of("client_name")
        ));
        tools.add(searchByClient);

        // Tool: get_fleet_stats
        Map<String, Object> stats = new HashMap<>();
        stats.put("name", "get_fleet_stats");
        stats.put("description", "Obtenir les statistiques globales (combien d'ordres par statut).");
        stats.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
        tools.add(stats);

        // Tool: search_clients_by_location
        Map<String, Object> searchLoc = new HashMap<>();
        searchLoc.put("name", "search_clients_by_location");
        searchLoc.put("description", "Rechercher mes clients dans une ville spécifique.");
        searchLoc.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("location", Map.of("type", "string")),
            "required", List.of("location")
        ));
        tools.add(searchLoc);

        // Tool: search_article
        Map<String, Object> searchArt = new HashMap<>();
        searchArt.put("name", "search_article");
        searchArt.put("description", "Rechercher un article. Laisse la requête vide pour tout lister.");
        searchArt.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("query", Map.of("type", "string", "description", "Nom ou code de l'article (optionnel)")),
            "required", List.of()
        ));
        tools.add(searchArt);

        // Tool: get_tracking_history
        Map<String, Object> trackHist = new HashMap<>();
        trackHist.put("name", "get_tracking_history");
        trackHist.put("description", "Obtenir l'historique détaillé des événements d'un ordre.");
        trackHist.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("order_ref", Map.of("type", "string")),
            "required", List.of("order_ref")
        ));
        tools.add(trackHist);

        // Tool: get_my_data_summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("name", "get_my_data_summary");
        summary.put("description", "Obtenir un résumé global de mes données (Clients, Ordres, Articles).");
        summary.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
        tools.add(summary);

        // Tool: create_reminder
        Map<String, Object> createReminder = new HashMap<>();
        createReminder.put("name", "create_reminder");
        createReminder.put("description", "Créer un rappel personnel, une note ou une tâche (ex: Rappelle moi de charger demain).");
        createReminder.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of(
                "text", Map.of("type", "string", "description", "Le contenu du rappel"),
                "time", Map.of("type", "string", "description", "L'heure ou date du rappel (ex: demain à 10h)")
            ),
            "required", List.of("text", "time")
        ));
        tools.add(createReminder);

        // Tool: create_order_draft
        Map<String, Object> createDraft = new HashMap<>();
        createDraft.put("name", "create_order_draft");
        createDraft.put("description", "Créer un brouillon d'ordre de transport.");
        createDraft.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of(
                "client", Map.of("type", "string"),
                "destination", Map.of("type", "string"),
                "quantity", Map.of("type", "string"),
                "details", Map.of("type", "string")
            ),
            "required", List.of("client", "destination", "quantity")
        ));
        tools.add(createDraft);

        // Tool: list_users_by_role
        Map<String, Object> listUsers = new HashMap<>();
        listUsers.put("name", "list_users_by_role");
        listUsers.put("description", "Lister les utilisateurs par rôle (ex: COMMERCIAL, ADMIN).");
        listUsers.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("role", Map.of("type", "string", "description", "Le rôle (COMMERCIAL, ADMIN, etc.)")),
            "required", List.of("role")
        ));
        tools.add(listUsers);

        // Tool: get_my_notifications
        Map<String, Object> getNotifs = new HashMap<>();
        getNotifs.put("name", "get_my_notifications");
        getNotifs.put("description", "Consulter mes notifications et alertes récentes (inscriptions, ordres, etc.).");
        getNotifs.put("parameters", Map.of(
            "type", "object",
            "properties", Map.of("unread_only", Map.of("type", "boolean", "description", "Si vrai, ne lister que les non-lus"))
        ));
        tools.add(getNotifs);

        // Tool: list_pending_registrations
        Map<String, Object> listPending = new HashMap<>();
        listPending.put("name", "list_pending_registrations");
        listPending.put("description", "Lister les inscriptions en attente de validation (statut PENDING).");
        listPending.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
        tools.add(listPending);

        return tools;
    }

    private Object executeTool(String name, Map<String, Object> args, String userEmail, String platform) {
        if (args == null) args = new HashMap<>();
        if (userEmail == null) return "User unidentified.";
        Optional<User> userOpt = userRepository.findFirstByEmailOrderByIdAsc(userEmail);
        if (userOpt.isEmpty()) return "User not found in DB.";
        User user = userOpt.get();

        switch (name) {
            case "get_order_details":
                String ref = (String) args.get("order_ref");
                List<Ordre> ordersByVoycle = ordreRepository.findByVoycle(ref);
                Optional<Ordre> order = ordersByVoycle.isEmpty() ? Optional.empty() : Optional.of(ordersByVoycle.get(0));
                if (order.isEmpty()) order = ordreRepository.findByOrderNumber(ref);
                
                if (order.isPresent()) {
                    Ordre o = order.get();
                    return String.format("Détails Ordre %s:\n" +
                           "• Statut: %s\n" +
                           "• Client: %s\n" +
                           "• Trajet: %s → %s\n" +
                           "• Marchandise: %s (%s)\n" +
                           "• Quantité: %d colis, %d palettes, %.2f kg\n" +
                           "• Transport: Camion %s, Chauffeur %s (%s)",
                           o.getOrderNumber(), o.getStatut(), o.getNomclient(),
                           o.getChargementVille(), o.getLivraisonVille(),
                           o.getDesignation(), o.getCodeArticle(),
                           o.getNombreColis() != null ? o.getNombreColis() : 0,
                           o.getNombrePalettes() != null ? o.getNombrePalettes() : 0,
                           o.getPoids() != null ? o.getPoids() : 0.0,
                           o.getCamion() != null ? o.getCamion() : "N/A",
                           o.getChauffeur() != null ? o.getChauffeur() : "N/A",
                           o.getTelchauffeur() != null ? o.getTelchauffeur() : "N/A");
                }
                return "Référence '" + ref + "' introuvable.";

            case "list_my_clients":
                List<Client> clients;
                if (user.isStaff()) {
                    clients = clientRepository.findAll();
                } else {
                    clients = clientRepository.findByOwner(user);
                }
                
                if (clients.isEmpty()) return "Aucun client trouvé.";
                StringBuilder res = new StringBuilder("Voici la liste des clients :\n");
                for (Client c : clients) res.append("• ").append(c.getNom()).append("\n");
                return res.toString();
 
            case "list_my_orders":
                List<Client> myClients = clientRepository.findByOwner(user);
                List<String> codes = myClients.stream().map(Client::getCodeclient).filter(Objects::nonNull).toList();
                
                String statusFilter = (String) args.get("status");
                String dateFromStr = (String) args.get("date_from");
                String locationFilter = (String) args.get("location");
                List<Ordre> orders;

                Statut filteredStatut = null;
                if (statusFilter != null && !statusFilter.isEmpty()) {
                    try {
                        filteredStatut = Statut.valueOf(statusFilter.toUpperCase().replace(" ", "_"));
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid status received from AI: {}", statusFilter);
                    }
                }

                final Statut finalS = filteredStatut;
                final Date finalDate;
                if (dateFromStr != null && !dateFromStr.isEmpty()) {
                    try {
                        finalDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(dateFromStr);
                    } catch (Exception e) {
                        log.warn("Invalid date format from AI: {}", dateFromStr);
                        return "Format de date invalide (attendu: YYYY-MM-DD).";
                    }
                } else {
                    finalDate = null;
                }

                orders = ordreRepository.findAll((root, query, cb) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                    
                    // Permission check (clients filter)
                    if (!user.isStaff() && !codes.isEmpty()) {
                        predicates.add(root.get("client").in(codes));
                    }

                    // Status filter
                    if (finalS != null) {
                        predicates.add(cb.equal(root.get("statut"), finalS));
                    }

                    // Date filter (on dateSaisie or chargementDate)
                    if (finalDate != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("dateSaisie"), finalDate));
                    }

                    // Location filter (on ville or nom site)
                    if (locationFilter != null && !locationFilter.isEmpty()) {
                        String loc = "%" + locationFilter.toLowerCase() + "%";
                        predicates.add(cb.or(
                            cb.like(cb.lower(root.get("chargementVille")), loc),
                            cb.like(cb.lower(root.get("livraisonVille")), loc),
                            cb.like(cb.lower(root.get("chargementNom")), loc),
                            cb.like(cb.lower(root.get("livraisonNom")), loc)
                        ));
                    }

                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }, org.springframework.data.domain.PageRequest.of(0, 15, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "dateSaisie"))).getContent();

                if (orders.isEmpty()) {
                    String msg = "Aucun ordre trouvé";
                    if (locationFilter != null) msg += " pour le site '" + locationFilter + "'";
                    if (dateFromStr != null) msg += " depuis le " + dateFromStr;
                    return msg + ".";
                }
                
                StringBuilder ordersRes = new StringBuilder("Voici les ordres trouvés :\n");
                for (Ordre o : orders.stream().limit(10).toList()) {
                    ordersRes.append("• ").append(o.getOrderNumber())
                             .append(" (").append(o.getStatut()).append(")")
                             .append(" - ").append(o.getChargementNom() != null ? o.getChargementNom() : o.getClient())
                             .append(" vers ").append(o.getLivraisonVille()).append("\n");
                }
                return ordersRes.toString();

            case "find_orders_by_client_name":
                String clientNameSearch = (String) args.get("client_name");
                List<Ordre> found = ordreRepository.findAll((root, query, cb) -> 
                    cb.or(
                        cb.like(cb.lower(root.get("chargementNom")), "%" + clientNameSearch.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("livraisonNom")), "%" + clientNameSearch.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("nomclient")), "%" + clientNameSearch.toLowerCase() + "%")
                    )
                );
                if (found.isEmpty()) return "Aucun ordre trouvé pour le client '" + clientNameSearch + "'.";
                StringBuilder searchRes = new StringBuilder("Ordres trouvés pour '" + clientNameSearch + "' :\n");
                for (Ordre o : found.stream().limit(10).toList()) {
                    searchRes.append("• ").append(o.getOrderNumber()).append(" (").append(o.getStatut()).append(") - Vers: ").append(o.getLivraisonVille()).append("\n");
                }
                return searchRes.toString();

            case "get_fleet_stats":
                List<Client> statsClients = clientRepository.findByOwner(user);
                List<String> statsCodes = statsClients.stream().map(Client::getCodeclient).filter(Objects::nonNull).toList();
                
                StringBuilder statsSb = new StringBuilder("Tableau de bord actuel :\n");
                for (Statut s : Statut.values()) {
                    long count;
                    if (user.isStaff() || statsCodes.isEmpty()) {
                        count = ordreRepository.countByStatut(s);
                    } else {
                        count = ordreRepository.countByClientCodesAndStatut(statsCodes, s);
                    }
                    if (count > 0) statsSb.append("• ").append(s).append(" : ").append(count).append("\n");
                }
                return statsSb.toString();

            case "search_clients_by_location":
                String loc = (String) args.get("location");
                List<Client> locClients = clientRepository.findByOwner(user).stream()
                    .filter(c -> (c.getVille() != null && c.getVille().equalsIgnoreCase(loc)) || 
                                 (c.getPays() != null && c.getPays().equalsIgnoreCase(loc)))
                    .toList();
                if (locClients.isEmpty()) return "Aucun client trouvé à " + loc + ".";
                StringBuilder locSb = new StringBuilder("Clients à " + loc + " :\n");
                for (Client c : locClients) locSb.append("• ").append(c.getNom()).append(" (").append(c.getCodeclient()).append(")\n");
                return locSb.toString();

            case "search_article":
                String artQuery = (String) args.get("query");
                List<Article> articles;
                if (artQuery == null || artQuery.trim().isEmpty() || artQuery.toLowerCase().contains("tous") || artQuery.toLowerCase().contains("liste")) {
                    articles = articleRepository.findAll().stream().limit(100).toList();
                } else {
                    articles = articleRepository.findAll().stream()
                        .filter(a -> (a.getLabel() != null && a.getLabel().toLowerCase().contains(artQuery.toLowerCase())) ||
                                     (a.getCodeArticle() != null && a.getCodeArticle().toLowerCase().contains(artQuery.toLowerCase())))
                        .limit(10).toList();
                }
                if (articles.isEmpty()) return "Aucun article trouvé pour '" + (artQuery != null ? artQuery : "votre requête") + "'.";
                StringBuilder artSb = new StringBuilder("Voici les articles disponibles :\n");
                for (Article a : articles) artSb.append("• ").append(a.getLabel()).append(" (").append(a.getCodeArticle()).append(") - ").append(a.getUnite()).append("\n");
                return artSb.toString();

            case "get_tracking_history":
                String tRef = (String) args.get("order_ref");
                Optional<Ordre> tOrder = ordreRepository.findByOrderNumber(tRef);
                if (tOrder.isEmpty()) return "Ordre introuvable.";
                Set<String> events = tOrder.get().getEvents();
                if (events == null || events.isEmpty()) return "Aucun événement de suivi pour cet ordre.";
                StringBuilder trackSb = new StringBuilder("Historique de suivi pour " + tRef + " :\n");
                for (String e : events) trackSb.append("• ").append(e).append("\n");
                return trackSb.toString();

            case "get_my_data_summary":
                List<Client> sumClients = clientRepository.findByOwner(user);
                List<String> sumCodes = sumClients.stream().map(Client::getCodeclient).filter(Objects::nonNull).toList();
                long orderCount = sumCodes.isEmpty() ? 0 : ordreRepository.countByClientCodes(sumCodes);
                long artCount = articleRepository.count();
                return String.format("Résumé de votre compte :\n" +
                       "• Clients: %d actifs\n" +
                       "• Ordres: %d au total\n" +
                       "• Articles: %d référencés",
                       sumClients.size(), orderCount, artCount);

            case "create_reminder":
                String text = (String) args.get("text");
                String timeStr = (String) args.get("time");
                Notification note = new Notification();
                note.setType("RAPPEL");
                note.setMessage("📅 Rappel: " + text + " (Prévu pour: " + timeStr + ")");
                note.setTargetUserId(user.getId()); // Target only the creator
                notificationRepository.save(note);
                return "Rappel créé avec succès: '" + text + "' pour " + timeStr;

            case "create_order_draft":
                Ordre draft = new Ordre();
                draft.setChargementNom((String) args.get("client"));
                draft.setLivraisonNom((String) args.get("destination"));
                draft.setDesignation((String) args.get("details"));
                try {
                    String qty = (String) args.get("quantity");
                    if (qty != null) draft.setNombrePalettes(Integer.parseInt(qty.replaceAll("[^0-9]", "")));
                } catch (Exception e) {}
                draft.setStatut(Statut.NON_CONFIRME);
                draft.setOrderNumber("IA-" + System.currentTimeMillis() / 1000);
                ordreRepository.save(draft);
                return "Brouillon créé avec succès: ID " + draft.getOrderNumber() + " (" + draft.getChargementNom() + " vers " + draft.getLivraisonNom() + ")";

            case "list_users_by_role":
                String roleStr = (String) args.get("role");
                try {
                    Role r = Role.valueOf(roleStr.toUpperCase());
                    List<User> users = userRepository.findByRole(r);
                    if (users.isEmpty()) return "Aucun utilisateur trouvé avec le rôle " + roleStr;
                    StringBuilder usersSb = new StringBuilder("Liste des utilisateurs (" + roleStr + ") :\n");
                    for (User u : users) {
                        usersSb.append("• ").append(u.getFirstname()).append(" ").append(u.getLastname())
                               .append(" (").append(u.getEmail()).append(")\n");
                    }
                    return usersSb.toString();
                } catch (Exception e) {
                    return "Rôle invalide. Les rôles possibles sont: CLIENT, COMMERCIAL, ADMIN.";
                }

            case "get_my_notifications":
                Boolean unreadOnly = (Boolean) args.getOrDefault("unread_only", false);
                List<Notification> notifs;
                if (unreadOnly) {
                    notifs = notificationRepository.findForUser(user.getId(), user.getRole()).stream()
                            .filter(n -> !n.isRead()).limit(10).toList();
                } else {
                    notifs = notificationRepository.findForUser(user.getId(), user.getRole()).stream()
                            .limit(10).toList();
                }
                
                if (notifs.isEmpty()) return "Vous n'avez pas de notifications récentes.";
                StringBuilder notifSb = new StringBuilder("Vos notifications récentes :\n");
                for (Notification n : notifs) {
                    notifSb.append(n.isRead() ? "• " : "• [NOUVEAU] ")
                           .append(n.getType()).append(": ").append(n.getMessage()).append("\n");
                }
                return notifSb.toString();

            case "list_pending_registrations":
                if (!user.isStaff()) return "Désolé, cette action est réservée au personnel administratif.";
                List<User> pendingUsers = userRepository.findByRole(Role.CLIENT).stream()
                        .filter(u -> u.getStatus() == Status.PENDING)
                        .toList();
                if (pendingUsers.isEmpty()) return "Aucune inscription en attente de validation actuellement.";
                StringBuilder pendingSb = new StringBuilder("Inscriptions en attente (PENDING) :\n");
                for (User u : pendingUsers) {
                    pendingSb.append("• ").append(u.getFirstname()).append(" ").append(u.getLastname())
                             .append(" (").append(u.getEmail()).append(")\n");
                }
                return pendingSb.toString();

            default: return "Unknown tool";
        }
    }

    private String extractTextFromGemini(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) return (String) parts.get(0).get("text");
            }
        } catch (Exception e) {}
        return "Je n'ai pas pu générer de réponse Gemini.";
    }

    private String extractTextFromGroq(Map<String, Object> response) {
        try {
            List choices = (List) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map choice = (Map) choices.get(0);
                Map message = (Map) choice.get("message");
                String content = (String) message.get("content");
                return (content != null) ? content : "";
            }
        } catch (Exception e) {
            log.error("Error extracting Groq text", e);
        }
        return "";
    }
}
