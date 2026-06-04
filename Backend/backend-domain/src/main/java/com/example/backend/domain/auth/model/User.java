package com.example.backend.domain.auth.model;

import com.example.backend.domain.shared.model.BaseAggregateRoot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseAggregateRoot {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private Set<Role> roles = new HashSet<>();

    public boolean isDisabled() {
        return status != null && status == 0;
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean isAgent() {
        return hasRole("AGENT");
    }

    public boolean isMember() {
        return hasRole("VIP");
    }

    public boolean hasRole(String roleName) {
        return roles != null && roles.stream().anyMatch(r -> roleName.equals(r.getRoleName()));
    }

    public Set<String> roleNames() {
        return roles == null ? Set.of() : roles.stream().map(Role::getRoleName).collect(Collectors.toSet());
    }

    public void activate() {
        this.status = 1;
        markUpdated();
    }

    public void disable() {
        this.status = 0;
        markUpdated();
    }

    public void assignRole(Role role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }
}
