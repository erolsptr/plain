package com.planit.controller;

import com.planit.model.RoomDetails;
import com.planit.service.RoomDetailsService; 
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/room-details") 
@RequiredArgsConstructor
public class RoomDetailsController {

    private final RoomDetailsService roomDetailsService;

    @PostMapping
    public ResponseEntity<RoomDetails> createOrUpdateRoomDetails(
            @RequestBody RoomDetails roomDetails,
            Authentication authentication) {
        
        String userEmail = authentication.getName();
        RoomDetails savedDetails = roomDetailsService.saveRoomDetails(roomDetails, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDetails);
    }

    @GetMapping
    public ResponseEntity<List<RoomDetails>> getRoomDetailsByIds(@RequestParam Set<String> roomIds) {
        List<RoomDetails> details = roomDetailsService.findRoomDetailsByIds(roomIds);
        return ResponseEntity.ok(details);
    }
}