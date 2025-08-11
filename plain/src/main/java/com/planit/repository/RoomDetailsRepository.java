package com.planit.repository;

import com.planit.model.RoomDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface RoomDetailsRepository extends JpaRepository<RoomDetails, String> {

    // İleride "Geçmiş Odalar" listesini zenginleştirmek için kullanacağımız bir metot.
    // Verilen bir oda ID listesine karşılık gelen tüm RoomDetails nesnelerini bulur.
    List<RoomDetails> findByRoomIdIn(Set<String> roomIds);

}