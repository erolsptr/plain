package com.planit.repository;

import com.planit.model.PokerRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PokerRoomRepository extends JpaRepository<PokerRoom, String> {

    @Query(value = "SELECT p.* FROM poker_rooms p JOIN room_participants rp ON p.id = rp.poker_room_id WHERE rp.user_id = :userId", nativeQuery = true)
    Set<PokerRoom> findRoomsByParticipantId(@Param("userId") Long userId);

    List<PokerRoom> findByOwnerId(Long ownerId);

}