package com.medicology.dictionary.service;

import com.medicology.dictionary.wrapper.UserPrincipal;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthenticatedUserService {

    public UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(UNAUTHORIZED, "Yêu cầu đăng nhập.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            if (userPrincipal.getId() == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "Token thiếu mã người dùng.");
            }
            return userPrincipal;
        }

        throw new ResponseStatusException(UNAUTHORIZED, "Thông tin xác thực không hợp lệ.");
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
