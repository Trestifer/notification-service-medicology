package com.medicology.dictionary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "[user]")
@Getter
@Setter
public class UserAccount {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;
}
