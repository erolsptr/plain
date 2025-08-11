package com.planit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_details")
@Getter
@Setter
@NoArgsConstructor
public class RoomDetails {

    @Id
    @Column(length = 6)
    private String roomId;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    // --- YENİ EKLENEN, VİZYONUNU DESTEKLEYEN ALANLAR ---

    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDateTime creationDate;

    @Column(name = "last_activity_date")
    private LocalDateTime lastActivityDate; // O odadaki son aktivitenin (örn: oylama kaydı) tarihi

    // Buraya ileride 'private String roomDescription;' gibi başka alanlar da eklenebilir.
    // --- YENİ ALANLAR SONU ---
}