package com.example.demo.Service;

import com.example.demo.Entity.Ordre;
import com.example.demo.Repository.OrdreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;

@Service
public class GpsSyncService {

    private static final Logger log = LoggerFactory.getLogger(GpsSyncService.class);

    @Autowired
    private OrdreRepository ordreRepository;

    @Autowired
    @Qualifier("rimtrackJdbcTemplate")
    private JdbcTemplate rimtrackJdbcTemplate;

    /**
     * Synchronize GPS positions every 2 minutes for active orders.
     * We match by Camion (Plate Number) -> Device Name in Rimtrack.
     */
    @Scheduled(fixedRate = 120000)
    public void syncGpsPositions() {
        log.info("Starting GPS Sync with Rimtrack...");

        // 1. Get all orders that are currently "EN_COURS_DE_LIVRAISON" or "CHARGE" or
        // "PLANIFIE"
        // and have a truck assigned
        List<Ordre> activeOrders = ordreRepository.findAll().stream()
                .filter(o -> o.getCamion() != null && !o.getCamion().isEmpty())
                .filter(o -> isTrackingNeeded(o.getStatut()))
                .toList();

        if (activeOrders.isEmpty()) {
            log.info("No active orders found for GPS tracking.");
            return;
        }

        for (Ordre ordre : activeOrders) {
            try {
                String camion = ordre.getCamion();
                if (camion == null || camion.isEmpty()) {
                    log.debug("Skipping order {} because camion is null despite status {}", ordre.getOrderNumber(),
                            ordre.getStatut());
                    continue;
                }
                updateOrderPosition(ordre);
            } catch (Exception e) {
                log.error("Failed to update GPS for order {}: {}", ordre.getOrderNumber(), e.getMessage());
            }
        }
    }

    private boolean isTrackingNeeded(com.example.demo.Entity.Statut statut) {
        if (statut == null)
            return false;
        String s = statut.name();
        return s.equals("PLANIFIE") || s.equals("EN_COURS_DE_CHARGEMENT") ||
                s.equals("CHARGE") || s.equals("EN_COURS_DE_LIVRAISON");
    }

    private void updateOrderPosition(Ordre ordre) {
        String camion = ordre.getCamion().trim();
        
        // Query logic: 
        // 1. Find device id for this truck name/plate
        // 2. Find latest activity for this device
        
        // Note: Table names and columns are guessed based on standard Rimtrack/GPS structures.
        // We will refine once the user provides the exact mapping table.
        
        try {
            // Step 1: Mapping Camion -> id_device
            // On passe par 'vehicule' pour trouver le vehicule_id, puis par 'device' pour trouver le id_device
            String mappingQuery = 
                "SELECT d.id_device " +
                "FROM vehicule v " +
                "JOIN device d ON v.vehicule_id = d.vehicule_id " +
                "WHERE v.matricule LIKE ? OR v.alias LIKE ? LIMIT 1";
            
            List<Long> deviceIds = rimtrackJdbcTemplate.queryForList(mappingQuery, Long.class, "%" + camion + "%", "%" + camion + "%");
            
            if (deviceIds.isEmpty()) {
                log.warn("Aucun boîtier (id_device) trouvé dans Rimtrack pour le camion : {}", camion);
                return;
            }
            
            Long idDevice = deviceIds.get(0);
            
            // Step 2: Récupérer la dernière position dans la table archive spécifique au boîtier (ex: arch_303)
            String dynamicTable = "rimtrack_archive.arch_" + idDevice;
            String posQuery = "SELECT latitude, longitude, speed, date FROM " + dynamicTable + " ORDER BY `date` DESC LIMIT 1";
            
            log.debug("Fetching coordinates from dynamic table: {}", dynamicTable);
            List<Map<String, Object>> posData = rimtrackJdbcTemplate.queryForList(posQuery);

            if (!posData.isEmpty()) {
                Map<String, Object> row = posData.get(0);
                Double lat = ((Number)row.get("latitude")).doubleValue();
                Double lon = ((Number)row.get("longitude")).doubleValue();
                Double speed = row.get("speed") != null ? ((Number)row.get("speed")).doubleValue() : 0.0;
                
                if (lat != null && lon != null && lat != 0 && lon != 0) {
                    ordre.setCurrentLat(lat);
                    ordre.setCurrentLon(lon);
                    ordre.setSpeed(speed); // Sauvegarde de la vitesse
                    ordreRepository.save(ordre);
                    log.info("Updated GPS for Order {}: {}/{} (Speed: {} km/h)", 
                        ordre.getOrderNumber(), lat, lon, speed);
                }
            }
        } catch (Exception e) {
            // If tables dont exist yet, we log it
            // We might need to try current_position table directly if id_device is not known.
            log.error("SQL Error during GPS sync for {}: {}", camion, e.getMessage());
        }
    }
    
    // Manual trigger for testing
    public void forceSync() {
        syncGpsPositions();
    }

    /**
     * Récupère l'historique du parcours pour un camion entre deux dates.
     */
    public List<Map<String, Object>> getOrderTrail(String camion, Date start, Date end) {
        if (camion == null || start == null || end == null) {
            return new ArrayList<>();
        }

        try {
            // 1. Trouver l'ID du boîtier (avec une recherche plus flexible sur le matricule)
            log.info("Recherche du parcours pour le camion : {}", camion);
            
            List<Integer> deviceIds = rimtrackJdbcTemplate.queryForList(
                "SELECT d.id_device FROM vehicule v JOIN device d ON v.vehicule_id = d.vehicule_id " +
                "WHERE REPLACE(v.matricule, ' ', '') LIKE REPLACE(?, ' ', '') LIMIT 1",
                Integer.class, camion
            );

            if (!deviceIds.isEmpty()) {
                Integer idDevice = deviceIds.get(0);
                log.info("Boîtier trouvé : {} pour le camion {}", idDevice, camion);
                // 2. Chercher tous les points dans la table archive (correction du nom de la base : rimtrack_archive)
                String sql = "SELECT latitude, longitude, date, speed FROM rimtrack_archive.arch_" + idDevice + 
                             " WHERE date BETWEEN ? AND ? ORDER BY date ASC";
                
                List<Map<String, Object>> points = rimtrackJdbcTemplate.query(sql, (rs, rowNum) -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("lat", rs.getDouble("latitude"));
                    point.put("lng", rs.getDouble("longitude"));
                    point.put("date", rs.getTimestamp("date"));
                    point.put("speed", rs.getDouble("speed"));
                    return point;
                }, start, end);

                if (!points.isEmpty()) {
                    log.info("Échantillon du premier point : lat={}, lng={}", points.get(0).get("lat"), points.get(0).get("lng"));
                }
                
                log.info("Nombre de points trouvés dans l'archive : {}", points.size());
                return points;
            } else {
                log.warn("Aucun boîtier trouvé pour le matricule : {} (vérifiez la correspondance dans Rimtrack)", camion);
            }
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du parcours pour {}: {}", camion, e.getMessage());
        }
        return new ArrayList<>();
    }
}