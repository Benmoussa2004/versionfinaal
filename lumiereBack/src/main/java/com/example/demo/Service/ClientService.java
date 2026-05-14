package com.example.demo.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Entity.Client;
import com.example.demo.Repository.ClientRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.Entity.User;

@Service
@Transactional
public class ClientService {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    // Multi-tenancy: find clients by owner
    public List<Client> findAllByOwner(User owner) {
        return clientRepository.findByOwner(owner);
    }

    public Optional<Client> findbycode(String code) {

        return clientRepository.findByCodeclient(code);

    }

    public String afficheremail(String id) {

        Optional<Client> client = clientRepository.findByCodeclient(id);
        Client c = client.get();
        return c.getEmail();

    }

    public String affichertelephone(String id) {

        Optional<Client> client = clientRepository.findByCodeclient(id);
        Client c = client.get();
        return c.getTelephone();

    }

    public Optional<Client> findById(Long id) {
        return clientRepository.findById(id);
    }

    public Client save(Client client) {
        return clientRepository.save(client);
    }

    // Shared ownership: save client and link owner
    public Client saveForOwner(Client client, User owner) {
        Optional<Client> existing = Optional.empty();
        if (client.getEmail() != null && !client.getEmail().isEmpty()) {
            List<Client> clients = clientRepository.findAll().stream()
                    .filter(c -> client.getEmail().equalsIgnoreCase(c.getEmail()))
                    .toList();
            if (!clients.isEmpty()) existing = Optional.of(clients.get(0));
        }
        if (existing.isEmpty() && client.getCodeclient() != null && !client.getCodeclient().isEmpty()) {
            existing = clientRepository.findByCodeclient(client.getCodeclient());
        }

        if (existing.isPresent()) {
            Client c = existing.get();
            // If already linked to this owner, just return it
            if (c.getOwners().contains(owner)) {
                return c;
            }
            // Link new owner to existing client (The "Shared Client" requirement)
            // [FIX] Reload owner to avoid LazyInitializationException on ownedClients
            owner = userRepository.findById(owner.getId()).orElse(owner);
            owner.getOwnedClients().add(c);
            userRepository.save(owner); 
            System.out.println("DEBUG: Linked existing client " + c.getEmail() + " to new owner " + owner.getEmail());
            return c;
        }

        // Otherwise, create new
        Client savedClient = clientRepository.save(client);
        // [FIX] Reload owner to avoid LazyInitializationException on ownedClients
        owner = userRepository.findById(owner.getId()).orElse(owner);
        owner.getOwnedClients().add(savedClient);
        userRepository.save(owner);
        return savedClient;
    }

    @Transactional
    public void deleteById(Long id) {
        // [FIX] Manual cleanup of the join table to avoid foreign key constraint violations
        jdbcTemplate.update("DELETE FROM user_clients WHERE client_id = ?", id);
        clientRepository.deleteById(id);
    }

    public Client updateClient(Long id, Client clientDetails) {
        Optional<Client> optionalClient = clientRepository.findById(id);
        if (optionalClient.isPresent()) {
            Client client = optionalClient.get();
            updateClientFields(client, clientDetails);

            // Check if profile is now completed
            if (isProfileComplete(client)) {
                System.out.println("DEBUG: Profile complete for client ID: " + client.getCode());
                client.setProfileCompleted(true);
                client.setRegistrationApproved(true);

                // Handle User promotion (The "New Principle")
                if (client.getOwners().isEmpty()) {
                    if (client.getTempPassword() != null) {
                        System.out.println("DEBUG: Creating NEW user from pending registration for: " + client.getEmail());
                        User newUser = new User();
                        newUser.setEmail(client.getEmail());
                        newUser.setFirstname(client.getNom()); 
                        newUser.setLastname(client.getNom());
                        newUser.setPasswd(client.getTempPassword());
                        newUser.setRole(com.example.demo.Entity.Role.CLIENT);
                        newUser.setStatus(com.example.demo.Entity.Status.ACTIVE);
                        
                        userRepository.save(newUser);
                        client.getOwners().add(newUser);
                        client.setTempPassword(null); // Clear secret!
                        System.out.println("DEBUG: User created successfully!");
                    } else {
                        // Edge case: Link to user by email if owners is empty and no temp password
                        System.out.println("DEBUG: Owners/TempPassword missing, searching for user with email: " + client.getEmail());
                        userRepository.findFirstByEmailOrderByIdAsc(client.getEmail()).ifPresent(u -> {
                            System.out.println("DEBUG: Found existing user, linking as owner.");
                            client.getOwners().add(u);
                            u.setStatus(com.example.demo.Entity.Status.ACTIVE);
                            userRepository.save(u);
                        });
                    }
                } else {
                    // Activate all owners if needed (usually the first one who registered)
                    client.getOwners().forEach(user -> {
                        if (user.getStatus() != com.example.demo.Entity.Status.ACTIVE) {
                            user.setStatus(com.example.demo.Entity.Status.ACTIVE);
                            userRepository.save(user);
                        }
                    });
                }
            } else {
                System.out.println("DEBUG: Profile NOT complete yet.");
                client.setProfileCompleted(false);
            }

            return clientRepository.save(client);
        } else {
            // Fallback: Try searching by codeclient (if the provided id was actually a codeclient)
            return clientRepository.findByCodeclient(String.valueOf(id))
                    .map(client -> {
                        updateClientFields(client, clientDetails);
                        if (isProfileComplete(client)) {
                            client.setProfileCompleted(true);
                            client.setRegistrationApproved(true);
                        }
                        return clientRepository.save(client);
                    })
                    .orElseThrow(() -> new RuntimeException("Client not found with id or code " + id));
        }
    }

    // Multi-tenancy: update client only if owned by user
    public Client updateClientForOwner(Long id, Client clientDetails, User owner) {
        Optional<Client> optionalClient = clientRepository.findById(id);
        if (optionalClient.isPresent()) {
            Client client = optionalClient.get();
            // Security check: verify ownership
            if (!client.getOwners().contains(owner)) {
                throw new RuntimeException("Unauthorized: Client does not belong to this user");
            }
            updateClientFields(client, clientDetails);

            // Clients can also complete their own profile if they have access
            if (isProfileComplete(client)) {
                client.setProfileCompleted(true);
            }

            return clientRepository.save(client);
        } else {
            // Fallback: Try searching by codeclient
            return clientRepository.findByCodeclient(String.valueOf(id))
                    .map(client -> {
                        if (!client.getOwners().contains(owner)) {
                            throw new RuntimeException("Unauthorized: Client does not belong to this user");
                        }
                        updateClientFields(client, clientDetails);
                        if (isProfileComplete(client) && !client.getOwners().isEmpty()) {
                            client.setProfileCompleted(true);
                        }
                        return clientRepository.save(client);
                    })
                    .orElseThrow(() -> new RuntimeException("Client not found with id or code " + id));
        }
    }

    private void updateClientFields(Client target, Client source) {
        target.setNom(source.getNom());
        target.setAdresse(source.getAdresse());
        target.setVille(source.getVille());
        target.setPays(source.getPays());
        target.setCodepostal(source.getCodepostal());
        target.setTelephone(source.getTelephone());
        target.setEmail(source.getEmail());
        target.setCodeclient(source.getCodeclient());
        target.setCivilite(source.getCivilite());
        target.setType(source.getType());
        target.setStatut(source.getStatut());
        target.setsType(source.getsType());
        target.setSocieteFacturation(source.getSocieteFacturation());
        target.setSiteExploitation(source.getSiteExploitation());
        target.setService(source.getService());
        target.setNumeroPortable(source.getNumeroPortable());
        target.setFax(source.getFax());
        target.setIdEdi(source.getIdEdi());
        target.setIdTva(source.getIdTva());
        target.setCodeIso(source.getCodeIso());
        target.setContact(source.getContact());
    }

    private boolean isProfileComplete(Client client) {
        // All attributes must be filled for registration approval per user request
        // Admin must have filled CodeClient and IdEdi
        return client.getCodeclient() != null && !client.getCodeclient().isEmpty() &&
                client.getIdEdi() != null && !client.getIdEdi().isEmpty() &&
                client.getNom() != null && !client.getNom().isEmpty() &&
                client.getAdresse() != null && !client.getAdresse().isEmpty() &&
                client.getVille() != null && !client.getVille().isEmpty() &&
                client.getPays() != null && !client.getPays().isEmpty() &&
                client.getCodepostal() != null &&
                client.getEmail() != null && !client.getEmail().isEmpty() &&
                client.getTelephone() != null && !client.getTelephone().isEmpty() &&
                client.getCivilite() != null && !client.getCivilite().isEmpty();
    }

    public long countAllclients() {
        return clientRepository.count(); // Or ordreRepository.countAllOrders();
    }
}