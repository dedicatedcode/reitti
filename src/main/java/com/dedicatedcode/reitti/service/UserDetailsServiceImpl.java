package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserJdbcService userJdbcService;

    public UserDetailsServiceImpl(UserJdbcService userJdbcService) {
        this.userJdbcService = userJdbcService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userJdbcService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
