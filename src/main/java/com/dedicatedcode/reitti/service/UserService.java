package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
    
    @Transactional
    public User createUser(String username, String displayName, String password) {
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }
}
