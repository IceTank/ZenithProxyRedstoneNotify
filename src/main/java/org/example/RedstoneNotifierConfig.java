package org.example;

/**
 * Example configuration POJO.
 *
 * Configurations are saved and loaded to JSON files
 *
 * All fields should be public and mutable.
 *
 * Fields to static inner classes generate nested JSON objects.
 */
public class RedstoneNotifierConfig {
    public boolean enabled = true;
    public boolean discordNotifications = true;
}
