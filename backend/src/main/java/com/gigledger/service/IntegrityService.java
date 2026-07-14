package com.gigledger.service;

import com.gigledger.entity.DiscrepancyFlag;
import com.gigledger.entity.Payout;
import com.gigledger.entity.RecordHashChain;
import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import com.gigledger.repository.DiscrepancyFlagRepository;
import com.gigledger.repository.PayoutRepository;
import com.gigledger.repository.RecordHashChainRepository;
import com.gigledger.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrityService {

    private final RecordHashChainRepository chainRepository;
    private final TaskRepository             taskRepository;
    private final PayoutRepository           payoutRepository;
    private final DiscrepancyFlagRepository flagRepository;

    public record VerificationResult(
        boolean valid,
        int totalRecords,
        UUID brokenAt
    ) {}

    // ── Serialization Helpers ────────────────────────────────────────────────

    public String serializeTask(Task task) {
        BigDecimal promised = task.getPromisedAmount();
        BigDecimal dist = task.getDistanceKm();
        return String.format("task|%s|%s|%s|%s",
                task.getId(),
                promised != null ? promised.setScale(2, RoundingMode.HALF_UP).toPlainString() : "null",
                dist != null ? dist.setScale(2, RoundingMode.HALF_UP).toPlainString() : "null",
                task.getAcceptedAt() != null ? task.getAcceptedAt().toString() : "null"
        );
    }

    public String serializePayout(Payout payout) {
        BigDecimal actual = payout.getActualAmount();
        return String.format("payout|%s|%s|%s",
                payout.getId(),
                actual != null ? actual.setScale(2, RoundingMode.HALF_UP).toPlainString() : "null",
                payout.getDeductionReason() != null ? payout.getDeductionReason() : "null"
        );
    }

    public String serializeFlag(DiscrepancyFlag flag) {
        BigDecimal base = flag.getBaselineRate();
        BigDecimal obs = flag.getObservedRate();
        BigDecimal sd = flag.getSdBelow();
        return String.format("flag|%s|%s|%s|%s|%s|%s|%s|%s",
                flag.getId(),
                flag.getPeriodStart() != null ? flag.getPeriodStart().toString() : "null",
                flag.getPeriodEnd() != null ? flag.getPeriodEnd().toString() : "null",
                flag.getBucket() != null ? flag.getBucket() : "null",
                base != null ? base.setScale(4, RoundingMode.HALF_UP).toPlainString() : "null",
                obs != null ? obs.setScale(4, RoundingMode.HALF_UP).toPlainString() : "null",
                flag.getSeverity() != null ? flag.getSeverity() : "null",
                sd != null ? sd.setScale(4, RoundingMode.HALF_UP).toPlainString() : "null"
        );
    }

    // ── SHA-256 Hashing ──────────────────────────────────────────────────────

    private String computeSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm missing: {}", e.getMessage());
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── Hashing API ──────────────────────────────────────────────────────────

    @Transactional
    public void appendRecord(User user, String recordType, UUID recordId, Object entity) {
        log.debug("Appending cryptographic block to user={} chain for recordType={} recordId={}",
                user.getEmail(), recordType, recordId);

        // 1. Serialize target entity
        String serialized;
        if ("task".equals(recordType)) {
            serialized = serializeTask((Task) entity);
        } else if ("payout".equals(recordType)) {
            serialized = serializePayout((Payout) entity);
        } else if ("flag".equals(recordType)) {
            serialized = serializeFlag((DiscrepancyFlag) entity);
        } else {
            throw new IllegalArgumentException("Unknown record type: " + recordType);
        }

        // 2. Fetch current head of the user's chain
        Optional<RecordHashChain> latestOpt = chainRepository.findTopByUserOrderByCreatedAtDesc(user);
        String previousHash = latestOpt.map(c -> c.getRecordHash()).orElse(null);

        // 3. Hash: compute SHA-256(serialized_content + previous_hash)
        String combined = serialized + "|" + (previousHash != null ? previousHash : "");
        String newHash = computeSHA256(combined);

        // 4. Save block
        RecordHashChain block = RecordHashChain.builder()
                .user(user)
                .recordType(recordType)
                .recordId(recordId)
                .recordHash(newHash)
                .previousHash(previousHash)
                .build();

        chainRepository.save(block);
        log.debug("Block appended successfully. Hash={}", newHash);
    }

    // ── Verification API ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VerificationResult verifyChain(User user) {
        List<RecordHashChain> blocks = chainRepository.findByUserOrderByCreatedAtAsc(user);
        log.info("Verifying cryptographic chain for user={} ({} blocks in chain)",
                user.getEmail(), blocks.size());

        String runningPreviousHash = null;

        for (int i = 0; i < blocks.size(); i++) {
            RecordHashChain block = blocks.get(i);

            // 1. Ensure links match: the block's declared previous hash must equal the running hash
            if ((runningPreviousHash == null && block.getPreviousHash() != null) ||
                (runningPreviousHash != null && !runningPreviousHash.equals(block.getPreviousHash()))) {
                log.warn("Chain integrity break at block index={}: previousHash mismatch. Stored previous={}, calculated previous={}",
                        i, block.getPreviousHash(), runningPreviousHash);
                return new VerificationResult(false, blocks.size(), block.getRecordId());
            }

            // 2. Fetch and serialize the actual referenced entity to check data tampering
            String serialized = null;
            if ("task".equals(block.getRecordType())) {
                Optional<Task> taskOpt = taskRepository.findById(block.getRecordId());
                if (taskOpt.isEmpty()) {
                    log.warn("Chain data break: Task entity deleted. recordId={}", block.getRecordId());
                    return new VerificationResult(false, blocks.size(), block.getRecordId());
                }
                serialized = serializeTask(taskOpt.get());
            } else if ("payout".equals(block.getRecordType())) {
                Optional<Payout> payoutOpt = payoutRepository.findById(block.getRecordId());
                if (payoutOpt.isEmpty()) {
                    log.warn("Chain data break: Payout entity deleted. recordId={}", block.getRecordId());
                    return new VerificationResult(false, blocks.size(), block.getRecordId());
                }
                serialized = serializePayout(payoutOpt.get());
            } else if ("flag".equals(block.getRecordType())) {
                Optional<DiscrepancyFlag> flagOpt = flagRepository.findById(block.getRecordId());
                if (flagOpt.isEmpty()) {
                    log.warn("Chain data break: DiscrepancyFlag entity deleted. recordId={}", block.getRecordId());
                    return new VerificationResult(false, blocks.size(), block.getRecordId());
                }
                serialized = serializeFlag(flagOpt.get());
            }

            // 3. Recompute hash
            String combined = serialized + "|" + (runningPreviousHash != null ? runningPreviousHash : "");
            String recomputedHash = computeSHA256(combined);

            // 4. Compare with the stored block hash
            if (!recomputedHash.equals(block.getRecordHash())) {
                log.warn("Chain integrity break: Stored hash does not match recomputed data for recordId={} at index={}. Stored={}, recomputed={}",
                        block.getRecordId(), i, block.getRecordHash(), recomputedHash);
                return new VerificationResult(false, blocks.size(), block.getRecordId());
            }

            // 5. Update running hash
            runningPreviousHash = block.getRecordHash();
        }

        log.info("Chain integrity verification successful for user={}.", user.getEmail());
        return new VerificationResult(true, blocks.size(), null);
    }
}
