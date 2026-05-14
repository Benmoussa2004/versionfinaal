package com.example.demo.Entity;

import jakarta.persistence.*;
import jakarta.persistence.Transient;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.example.demo.Entity.Role;
import com.example.demo.Entity.Status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Table(name = "_user")
@Entity
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String firstname;
    private String lastname;
    private String email;
    @JsonIgnore
    private String passwd;

    @Enumerated(EnumType.STRING)
    Role role;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Transient
    private boolean registrationApproved;

    @Column(columnDefinition = "LONGTEXT")
    private String profileImageBase64;

    public String getProfileImageBase64() {
        return profileImageBase64;
    }

    public void setProfileImageBase64(String profileImageBase64) {
        this.profileImageBase64 = profileImageBase64;
    }

    public boolean isRegistrationApproved() {
        return registrationApproved;
    }

    public void setRegistrationApproved(boolean registrationApproved) {
        this.registrationApproved = registrationApproved;
    }

    @Transient
    private Long linkedClientId;

    public Long getLinkedClientId() {
        return linkedClientId;
    }

    public void setLinkedClientId(Long linkedClientId) {
        this.linkedClientId = linkedClientId;
    }

    // Shared clients relationship - multiple users can own/be linked to multiple clients
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_clients",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "client_id")
    )
    @JsonIgnore
    private List<Client> ownedClients = new ArrayList<>();

    public List<Client> getOwnedClients() {
        return ownedClients;
    }

    public void setOwnedClients(List<Client> ownedClients) {
        this.ownedClients = ownedClients;
    }

    public User() {
    }

    public User(Integer id, String firstname, String lastname, String email, String passwd, Role role) {
        this.id = id;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.passwd = passwd;
        this.role = role;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswd() {
        return passwd;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @JsonIgnore
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @JsonIgnore
    public boolean isCommercial() {
        return role == Role.COMMERCIAL;
    }

    @JsonIgnore
    public boolean isEmployerLumiere() {
        return role == Role.EMPLOYER_LUMIERE;
    }

    @JsonIgnore
    public boolean isStaff() {
        return isAdmin() || isCommercial() || isEmployerLumiere();
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return new ArrayList<>();
        }
        return AuthorityUtils.createAuthorityList(role.name());
        // return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwd;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return email;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return 31; // Constant hash code for entities with generated IDs (Hibernate recommendation)
    }
}
