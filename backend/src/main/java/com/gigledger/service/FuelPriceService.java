package com.gigledger.service;

import com.gigledger.entity.FuelPriceCache;
import com.gigledger.repository.FuelPriceCacheRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPriceService {

    private final FuelPriceCacheRepository cacheRepository;

    @Value("${fuel.api.url:https://fuelprice-api-india.herokuapp.com}")
    private String fuelApiUrl;

    @Value("${fuel.default.price:103.50}")
    private BigDecimal defaultPrice;

    private final WebClient webClient = WebClient.builder().build();

    /**
     * Retrieves today's petrol price for a given state and district.
     * Checks cached database records first to adhere to the 24h caching rule.
     */
    public FuelPriceCache getTodayPetrolPrice(String state, String district) {
        String cleanDistrict = district != null ? district.trim() : "Chennai";
        String cleanState = state != null ? state.trim() : "Tamil Nadu";
        LocalDate today = LocalDate.now();

        // 1. Check Cache
        Optional<FuelPriceCache> cached = cacheRepository.findByDistrictIgnoreCaseAndFetchedDate(cleanDistrict, today);
        if (cached.isPresent()) {
            log.info("Found cached petrol price for district={} on date={}: Rs. {}", cleanDistrict, today, cached.get().getPetrolPrice());
            return cached.get();
        }

        // 2. Fetch from API Scraper
        log.info("Fetching petrol price for state={}, district={} from API...", cleanState, cleanDistrict);
        try {
            String formattedState = cleanState.replace(" ", "%20");
            String formattedDistrict = cleanDistrict.replace(" ", "%20");
            String targetUrl = fuelApiUrl + "/price/" + formattedState + "/" + formattedDistrict;

            FuelPriceApiResponse response = webClient.get()
                    .uri(targetUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(FuelPriceApiResponse.class)
                    .block();

            if (response != null && response.getPetrol() != null) {
                BigDecimal price = new BigDecimal(response.getPetrol().trim());
                FuelPriceCache entry = FuelPriceCache.builder()
                        .district(cleanDistrict)
                        .petrolPrice(price)
                        .fetchedDate(today)
                        .verified(true)
                        .build();
                log.info("Successfully fetched petrol price from API: Rs. {}", price);
                return cacheRepository.save(entry);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch petrol price from fuel API (using fallback default): {}", e.getMessage());
        }

        // 3. Fallback to Default
        log.info("Using estimated fallback petrol price: Rs. {}", defaultPrice);
        FuelPriceCache fallbackEntry = FuelPriceCache.builder()
                .district(cleanDistrict)
                .petrolPrice(defaultPrice)
                .fetchedDate(today)
                .verified(false)
                .build();
        return cacheRepository.save(fallbackEntry);
    }

    @Data
    public static class FuelPriceApiResponse {
        private String petrol;
        private String diesel;
        private String district;
        private String state;
    }
}
