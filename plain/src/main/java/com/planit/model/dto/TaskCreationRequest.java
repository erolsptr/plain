package com.planit.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskCreationRequest {

    private String title;
    private String description;
    private String cardSet;

}