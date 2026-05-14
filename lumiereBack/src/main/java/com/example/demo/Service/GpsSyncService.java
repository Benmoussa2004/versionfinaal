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
        log.info("📡 Starting GPS Sync with Rimtrack (IP: 172.18.3.77)...");
        
        try {
            // Quick check if Rimtrack DB is even reachable
            rimtrackJdbcTemplate.execute("SELECT 1");
            log.info("✅ Connection to Rimtrack DB verified.");
        } catch (Exception e) {
            log.error("❌ CRITICAL: Cannot reach Rimtrack Database. Is your VPN/Network active? Error: {}", e.getMessage());
            return;
        }
        List<Ordre> activeOrders = ordreRepository.findAll().stream()
                .filter(o -> o.getCamion() != null && !o.getCamion().trim().isEmpty())
                .filter(o -> isTrackingNeeded(o.getStatut()))
                .toList();

        if (activeOrders.isEmpty()) {
            log.info("ℹ️ No active orders found with trucks for GPS tracking.");
            return;
        }

        log.info("🔍 Found {} orders requiring GPS sync.", activeOrders.size());

        for (Ordre ordre : activeOrders) {
            try {
                updateOrderPosition(ordre);
            } catch (Exception e) {
                log.error("⚠️ Failed to update GPS for order {}: {}", ordre.getOrderNumber(), e.getMessage());
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
        log.info("🚛 Processing Truck: {}", camion);
        
        try {
            // Step 1: Mapping Camion -> id_device
            Long idDevice = null;
            
            // [TEST FEATURE] If truck starts with SIM-, use the number as device ID directly
            if (camion.toUpperCase().startsWith("SIM-")) {
                try {
                    idDevice = Long.parseLong(camion.substring(4).trim());
                    log.info("🧪 TEST MODE: Forcing Device ID [{}] for truck [{}]", idDevice, camion);
                } catch (Exception e) {
                    log.warn("🧪 TEST MODE ERROR: Invalid device ID format in SIM- truck name: {}", camion);
                }
            }
            
            if (idDevice == null) {
                String mappingQuery = 
                    "SELECT d.id_device " +
                    "FROM vehicule v " +
                    "JOIN device d ON v.vehicule_id = d.vehicule_id " +
                    "WHERE LOWER(v.matricule) LIKE LOWER(?) OR LOWER(v.alias) LIKE LOWER(?) LIMIT 1";
                
                List<Long> deviceIds = rimtrackJdbcTemplate.queryForList(mappingQuery, Long.class, "%" + camion + "%", "%" + camion + "%");
                
                if (deviceIds.isEmpty()) {
                    log.warn("❓ No device mapping found for truck [{}] in Rimtrack mapping table.", camion);
                    return;
                }
                idDevice = deviceIds.get(0);
                log.info("📍 Found Device ID [{}] for truck [{}]", idDevice, camion);
            }
            
            // Step 2: Récupérer la dernière position
            String dynamicTable = "rimtrack_archive.arch_" + idDevice;
            String posQuery = "SELECT latitude, longitude, speed, date FROM " + dynamicTable + " ORDER BY `date` DESC LIMIT 1";
            
            List<Map<String, Object>> posData = rimtrackJdbcTemplate.queryForList(posQuery);

            if (!posData.isEmpty()) {
                Map<String, Object> row = posData.get(0);
                Double lat = ((Number)row.get("latitude")).doubleValue();
                Double lon = ((Number)row.get("longitude")).doubleValue();
                Double speed = row.get("speed") != null ? ((Number)row.get("speed")).doubleValue() : 0.0;
                
                if (lat != null && lon != null && lat != 0 && lon != 0) {
                    ordre.setCurrentLat(lat);
                    ordre.setCurrentLon(lon);
                    ordre.setSpeed(speed);
                    ordreRepository.save(ordre);
                    log.info("✨ SUCCESS: Updated Order {} position: {}, {} @ {} km/h", 
                        ordre.getOrderNumber(), lat, lon, speed);
                } else {
                    log.warn("⚠️ Received [0,0] coordinates for device {}, skipping.", idDevice);
                }
            } else {
                log.warn("📭 No archive data found in {} for device {}.", dynamicTable, idDevice);
            }
        } catch (Exception e) {
            log.error("❌ SQL ERROR during GPS sync for {}: {}. (Tip: If table arch_XXX doesn't exist yet, this is expected for new devices)", 
                camion, e.getMessage());
        }
    }
    
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