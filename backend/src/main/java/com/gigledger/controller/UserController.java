package com.gigledger.controller;

import com.gigledger.entity.User;
import com.gigledger.repository.UserRepository;
import com.gigledger.service.FuelPriceService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Controller exposing REST endpoints for managing user profile settings.
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final FuelPriceService fuelPriceService;

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }

    @GetMapping("/fuel-price")
    public ResponseEntity<FuelPriceResponse> getFuelPrice(
            @RequestParam(required = false, defaultValue = "Tamil Nadu") String state,
            @RequestParam(required = false, defaultValue = "Chennai") String district,
            Authentication auth) {
        
        String queryState = state;
        String queryDistrict = district;
        
        if (auth != null && auth.isAuthenticated()) {
            User user = getUser(auth.getName());
            queryState = user.getState();
            queryDistrict = user.getDistrict();
        }
        
        var cache = fuelPriceService.getTodayPetrolPrice(queryState, queryDistrict);
        return ResponseEntity.ok(FuelPriceResponse.builder()
                .district(cache.getDistrict())
                .state(queryState)
                .petrolPrice(cache.getPetrolPrice())
                .fetchedDate(cache.getFetchedDate().toString())
                .verified(cache.isVerified())
                .build());
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getSettings(Authentication auth) {
        User user = getUser(auth.getName());
        return ResponseEntity.ok(UserSettingsResponse.builder()
                .platformPreference(user.getPlatformPreference())
                .shortfallThreshold(user.getShortfallThreshold())
                .severityThreshold(user.getSeverityThreshold())
                .emailNotificationsEnabled(user.isEmailNotificationsEnabled())
                .district(user.getDistrict())
                .state(user.getState())
                .vehicleType(user.getVehicleType())
                .fuelEfficiency(user.getFuelEfficiency())
                .build());
    }

    @PutMapping("/settings")
    public ResponseEntity<UserSettingsResponse> updateSettings(
            @RequestBody UserSettingsRequest request,
            Authentication auth) {
        User user = getUser(auth.getName());
        
        if (request.getPlatformPreference() != null) {
            user.setPlatformPreference(request.getPlatformPreference());
        }
        if (request.getShortfallThreshold() != null) {
            user.setShortfallThreshold(request.getShortfallThreshold());
        }
        if (request.getSeverityThreshold() != null) {
            user.setSeverityThreshold(request.getSeverityThreshold());
        }
        if (request.getEmailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        if (request.getDistrict() != null) {
            user.setDistrict(request.getDistrict());
        }
        if (request.getState() != null) {
            user.setState(request.getState());
        }
        if (request.getVehicleType() != null) {
            user.setVehicleType(request.getVehicleType());
        }
        if (request.getFuelEfficiency() != null) {
            user.setFuelEfficiency(request.getFuelEfficiency());
        }

        userRepository.save(user);
        log.info("Settings updated for user={}: platform={}, shortfall={}, severity={}, emailNotif={}, district={}, state={}, vehicleType={}, fuelEff={}", 
                 user.getEmail(), user.getPlatformPreference(), user.getShortfallThreshold(), user.getSeverityThreshold(),
                 user.isEmailNotificationsEnabled(), user.getDistrict(), user.getState(), user.getVehicleType(), user.getFuelEfficiency());
        
        return ResponseEntity.ok(UserSettingsResponse.builder()
                .platformPreference(user.getPlatformPreference())
                .shortfallThreshold(user.getShortfallThreshold())
                .severityThreshold(user.getSeverityThreshold())
                .emailNotificationsEnabled(user.isEmailNotificationsEnabled())
                .district(user.getDistrict())
                .state(user.getState())
                .vehicleType(user.getVehicleType())
                .fuelEfficiency(user.getFuelEfficiency())
                .build());
    }

    @Data
    @Builder
    public static class UserSettingsResponse {
        private String platformPreference;
        private BigDecimal shortfallThreshold;
        private String severityThreshold;
        private boolean emailNotificationsEnabled;
        private String district;
        private String state;
        private String vehicleType;
        private BigDecimal fuelEfficiency;
    }

    @Data
    public static class UserSettingsRequest {
        private String platformPreference;
        private BigDecimal shortfallThreshold;
        private String severityThreshold;
        private Boolean emailNotificationsEnabled;
        private String district;
        private String state;
        private String vehicleType;
        private BigDecimal fuelEfficiency;
    }

    @Data
    @Builder
    public static class FuelPriceResponse {
        private String district;
        private String state;
        private BigDecimal petrolPrice;
        private String fetchedDate;
        private boolean verified;
    }
}
