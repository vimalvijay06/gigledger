package com.gigledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "platform_grievance_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformGrievanceContact {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "platform_name", unique = true, nullable = false, length = 64)
    private String platformName;

    @Column(name = "grievance_email", nullable = false, length = 128)
    private String grievanceEmail;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "contact_notes", length = 512)
    private String contactNotes;

    @Column(name = "legal_basis_note", length = 512)
    private String legalBasisNote;
}
