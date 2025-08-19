package com.ecamt35.userservice.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class PasswordUtil {
    public static String encrypt(String originalPassword) throws NoSuchAlgorithmException {
        // 生成盐值
        byte[] saltOriginalBytes = new byte[16]; // 16字节盐(32字符)
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(saltOriginalBytes);
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : saltOriginalBytes) {
            stringBuilder.append(String.format("%02x", b));
        }
        String salt = stringBuilder.toString();

        String hashPassword = PasswordUtil.getHashPassword(
                salt.getBytes(StandardCharsets.UTF_8),
                originalPassword.getBytes(StandardCharsets.UTF_8));

        return hashPassword + salt;
    }

    public static boolean verify(String originalPassword, String passwordFromDB) throws NoSuchAlgorithmException {
        String salt = passwordFromDB.substring(64);
        String expectedHashPassword = passwordFromDB.substring(0, 64);
        byte[] saltGetBytes = salt.getBytes(StandardCharsets.UTF_8);
        byte[] originalPasswordGetBytes = originalPassword.getBytes(StandardCharsets.UTF_8);
        String hashPassword = PasswordUtil.getHashPassword(saltGetBytes, originalPasswordGetBytes);
        return expectedHashPassword.equalsIgnoreCase(hashPassword);
    }

    private static String getHashPassword(byte[] saltGetBytes, byte[] originalPasswordGetBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(saltGetBytes);
        md.update(originalPasswordGetBytes);
        byte[] hashedPassword = md.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : hashedPassword) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}