package com.planit.model.dto;

import lombok.Getter;
import lombok.Setter;

// Bu DTO, frontend'deki TaskForm'dan yeni bir görev oluşturma isteği geldiğinde
// JSON verilerini taşımak için kullanılır.
@Getter
@Setter
public class TaskCreationRequest {

    private String title;
    private String description;
    private String cardSet;

}