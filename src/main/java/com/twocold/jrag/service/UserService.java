package com.twocold.jrag.service;

import com.twocold.jrag.config.CacheConfig;
import com.twocold.jrag.domain.User;
import com.twocold.jrag.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User register(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setSalt(salt);
        user.setPasswordHash(hashedPassword);
        user.setCreatedAt(LocalDateTime.now());
        user.setRole("USER"); // 默认角色

        return userRepository.save(user);
    }

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码无效"));

        String inputHashed = hashPassword(password, user.getSalt());
        if (!inputHashed.equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码无效");
        }

        return user;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findById(userId);
        String currentHashed = hashPassword(currentPassword, user.getSalt());
        if (!currentHashed.equals(user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码错误");
        }
        
        String newSalt = generateSalt();
        String newHashed = hashPassword(newPassword, newSalt);
        user.setSalt(newSalt);
        user.setPasswordHash(newHashed);
        userRepository.save(user);
    }
    
    @Transactional
    public User createUser(String username, String email, String role, String initialPassword) {
         if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String salt = generateSalt();
        String hashedPassword = hashPassword(initialPassword, salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(role);
        user.setSalt(salt);
        user.setPasswordHash(hashedPassword);
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
    
    @Transactional
    public User updateUser(Long userId, String username, String role, String email) {
        User user = findById(userId);
        if (username != null) user.setUsername(username);
        if (role != null) user.setRole(role);
        if (email != null) user.setEmail(email);
        return userRepository.save(user);
    }
    
    public Iterable<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到用户"));
    }

    @Cacheable(value = CacheConfig.USER_ADMIN_CACHE, key = "#userId")
    public boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole()))
                .orElse(false);
    }

    /**
     * 重置用户密码（管理员使用，无需当前密码）
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return 生成的新密码（明文）
     */
    @Transactional
    public String resetPassword(Long userId, String newPassword) {
        User user = findById(userId);

        String salt = generateSalt();
        String hashedPassword = hashPassword(newPassword, salt);
        user.setSalt(salt);
        user.setPasswordHash(hashedPassword);
        userRepository.save(user);

        return newPassword;
    }

    /**
     * 重置用户密码并生成随机密码
     * @param userId 用户ID
     * @return 生成的随机密码（明文）
     */
    @Transactional
    public String resetPasswordWithRandom(Long userId) {
        String randomPassword = generateComplexPassword();
        return resetPassword(userId, randomPassword);
    }

    private String generateComplexPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*";
        String all = upper + lower + digits + special;

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        // Ensure at least one of each
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Fill the rest
        for (int i = 4; i < 12; i++) {
            password.append(all.charAt(random.nextInt(all.length())));
        }

        // Shuffle
        char[] passwordArray = password.toString().toCharArray();
        for (int i = 0; i < passwordArray.length; i++) {
            int j = random.nextInt(passwordArray.length);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedPassword = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("未找到 SHA-256 算法", e);
        }
    }
}
