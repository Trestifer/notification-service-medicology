package com.medicology.dictionary.repository.auth;

import com.medicology.dictionary.entity.UserAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
}
