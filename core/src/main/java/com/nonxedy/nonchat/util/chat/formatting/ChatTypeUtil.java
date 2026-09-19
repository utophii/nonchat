package com.nonxedy.nonchat.util.chat.formatting;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a chat type configuration with formatting and behavior settings
 * Handles different chat modes like global and local radius-based chat
 */
@Data
@Builder
public class ChatTypeUtil {
    private final boolean enabled;
    private final String displayName;
    private final String format;
    private final int radius;
    private final char chatChar;
    private final String sendPermission; // Can be null or empty
    private final String receivePermission; // Can be null or empty
    private final int cooldown;
    private final int minLength;
    private final int maxLength;
    private final String switchMessage;

    // Legacy constructor for backward compatibility
    public ChatTypeUtil(boolean enabled, String format, int radius, char chatChar, String permission) {
        this(enabled, null, format, radius, chatChar, permission, null, 0, 0, -1, "");
    }

    // Full constructor with all properties
    public ChatTypeUtil(boolean enabled, String displayName, String format, int radius, char chatChar, 
                        String sendPermission, String receivePermission,
                        int cooldown, int minLength, int maxLength, String switchMessage) {
        this.enabled = enabled;
        this.displayName = displayName != null ? displayName : "Channel";
        this.format = format;
        this.radius = radius;
        this.chatChar = chatChar;
        this.sendPermission = sendPermission;
        this.receivePermission = receivePermission;
        this.cooldown = cooldown;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.switchMessage = switchMessage != null ? switchMessage : "";
    }
    
    public boolean hasSendPermission() {
        return sendPermission != null && !sendPermission.isEmpty();
    }
    
    public boolean hasReceivePermission() {
        return receivePermission != null && !receivePermission.isEmpty();
    }
    
    public boolean hasCooldown() {
        return cooldown > 0;
    }
    
    public boolean hasMinLength() {
        return minLength > 0;
    }
    
    public boolean hasMaxLength() {
        return maxLength >= 0;
    }

    public boolean isGlobal() {
        return radius == -1;
    }
}
