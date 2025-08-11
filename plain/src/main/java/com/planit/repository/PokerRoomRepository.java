package com.planit.repository;

import com.planit.model.PokerRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;


@Repository
public interface PokerRoomRepository extends JpaRepository<PokerRoom, String> {


    @Query("SELECT pr FROM PokerRoom pr JOIN pr.participants p WHERE p.email = :email")
    Set<PokerRoom> findRoomsByParticipantEmail(String email);
    
}