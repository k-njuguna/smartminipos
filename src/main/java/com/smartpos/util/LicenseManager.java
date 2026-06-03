package com.smartpos.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Scanner;

public class LicenseManager {
    private static final String LICENSE_FILE = "license.lic";
    private static final String SALT = "SMART-POS-SECURE-2026-X";
    
    // Verified SHA-256 Hash for "Admin@SmartPOS2026"
    private static final String MASTER_HASH = "a17d5786df784c204f2e550de0c0125f290d683c5d169bafd266b5a1f6ffaa44"; 

    /**
     * Checks for a permanent license file on startup.
     */
    public static boolean isLicensed() {
        File file = new File(LICENSE_FILE);
        if (!file.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String storedKey = reader.readLine();
            return isValidKey(storedKey);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Retrieves the shop name from the license file.
     * Returns "SmartPOS" as a default if the file is missing or name not found.
     */
    public static String getShopName() {
        File file = new File(LICENSE_FILE);
        if (!file.exists()) return "SmartPOS";
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            reader.readLine(); // Skip the license key line
            String shopName = reader.readLine(); // Read the shop name line
            return (shopName != null && !shopName.isBlank()) ? shopName : "SmartPOS";
        } catch (IOException e) {
            return "SmartPOS";
        }
    }

    public static boolean isMasterKey(String input) {
        if (input == null || input.isBlank()) return false;
        return hashString(input).equals(MASTER_HASH);
    }

    public static boolean isValidKey(String input) {
        if (input == null || input.isBlank()) return false;
        if (isMasterKey(input)) return true;

        String hardwareKey = generateKey(getMachineUUID());
        return input.equalsIgnoreCase(hardwareKey);
    }

    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getMachineUUID() {
        String uuid = "UNKNOWN";
        try {
            Process process = Runtime.getRuntime().exec("wmic csproduct get uuid");
            try (Scanner sc = new Scanner(process.getInputStream())) {
                while (sc.hasNext()) {
                    String s = sc.next();
                    if (s.equalsIgnoreCase("UUID")) continue;
                    uuid = s.trim();
                    break;
                }
            }
        } catch (Exception e) {
            return "ERROR_ID";
        }
        return uuid;
    }

    public static String generateKey(String uuid) {
        if (uuid == null || uuid.equals("UNKNOWN") || uuid.equals("ERROR_ID")) return null;
        return hashString(uuid + SALT).substring(0, 16).toUpperCase();
    }

    /**
     * Saves the license key and shop name to the file.
     * The shop name is written on the second line.
     */
    public static boolean saveLicenseKey(String key, String shopName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LICENSE_FILE))) {
            writer.write(key.trim());
            writer.newLine(); // Move to next line
            writer.write(shopName.trim());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}