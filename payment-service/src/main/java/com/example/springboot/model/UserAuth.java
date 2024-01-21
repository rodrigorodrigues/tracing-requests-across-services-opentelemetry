package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@Table("user_auth")
public class UserAuth implements UserDetails {
    @Id
    private Long id;

    @NotBlank
    private String username;

    @NotNull
    private BigDecimal balance;

    @NotBlank
    private String address;

    private String password;

    @NotEmpty
    private String fullName;

    private boolean active = true;

    private boolean socialMedia;

    public UserAuth() {}

    public UserAuth(String username, BigDecimal balance, String address, String password, String fullName) {
        this.username = username;
        this.balance = balance;
        this.address = address;
        this.password = password;
        this.fullName = fullName;
    }

    public UserAuth(UserRequestDto userRequestDto, String password) {
        this(userRequestDto.username(), new BigDecimal("1000.00"), userRequestDto.address(), password, userRequestDto.fullName());
    }

    public void updateBalance(UserAuth to, BigDecimal totalPayment) {
        to.balance = to.balance.add(totalPayment);
        this.balance = balance.subtract(totalPayment);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (username.equals("admin")) {
            return Collections.singletonList(new SimpleGrantedAuthority("ADMIN"));
        }
        return Collections.singletonList(new SimpleGrantedAuthority("USER"));
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSocialMedia() {
        return socialMedia;
    }

    public void setSocialMedia(boolean socialMedia) {
        this.socialMedia = socialMedia;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAuth userAuth = (UserAuth) o;
        return active == userAuth.active &&
                socialMedia == userAuth.socialMedia &&
                Objects.equals(id, userAuth.id) &&
                Objects.equals(username, userAuth.username) &&
                Objects.equals(balance, userAuth.balance) &&
                Objects.equals(address, userAuth.address) &&
                Objects.equals(fullName, userAuth.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, balance, address, fullName, active, socialMedia);
    }

    @Override
    public String toString() {
        return "UserAuth{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", balance=" + balance +
                ", address='" + address + '\'' +
                ", fullName='" + fullName + '\'' +
                ", active=" + active +
                ", socialMedia=" + socialMedia +
                '}';
    }
}
