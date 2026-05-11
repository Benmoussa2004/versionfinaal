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
        if (statut == null) return false;
        String s = statut.name();
        return s.equals("PLANIFIE") || s.equals("EN_COURS_DE_CHARGEMENT") || 
               s.equals("CHARGE") || s.equals("EN_COURS_DE_LIVRAISON");
    }

    private void updateOrderPosition(Ordre ordre) {
        String camion = ordre.getCamion().trim();
        log.info("🚛 Processing Truck: {}", camion);
        
        try {
            // Step 1: Mapping Camion -> id_device
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
            
            Long idDevice = deviceIds.get(0);
            log.info("📍 Found Device ID [{}] for truck [{}]", idDevice, camion);
            
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
            log.error("❌ SQL ERROR for camion {}: {}. Tip: Check if table arch_{} exists.", 
                camion, e.getMessage(), "XXX");
        }
    }
    
    public void forceSync() {
        syncGpsPositions();
    }
}
