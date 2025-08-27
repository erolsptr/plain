package com.planit.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoteDetailDTO {
    private String voterName;
    private String voteValue;
}